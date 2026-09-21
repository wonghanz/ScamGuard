package dev.capriguard.scamguard.link

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.capriguard.scamguard.core.AiClient
import dev.capriguard.scamguard.core.AiConfig
import dev.capriguard.scamguard.core.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The message stays in memory for as long as the screen is up and is dropped when
 * the app leaves the foreground. A forwarded scam text carries somebody's phone
 * number — usually the sender's, sometimes a victim's — so it is not written to
 * DataStore, to a file, or to a log, and there is no history screen to write it to.
 *
 * The reading is recomputed on every keystroke because the whole pass is string
 * arithmetic over a table the device already has; it costs nothing and it means
 * the finding list is never stale relative to the text.
 */
class CheckViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepository(app)
    private val ai = AiClient()

    var config by mutableStateOf(
        AiConfig(SettingsRepository.DEFAULT_BASE_URL, "", SettingsRepository.DEFAULT_MODEL, false),
    )
        private set

    init {
        viewModelScope.launch { settings.config.collect { config = it } }
    }

    fun setBaseUrl(v: String) { viewModelScope.launch { settings.setBaseUrl(v) } }
    fun setApiKey(v: String) { viewModelScope.launch { settings.setApiKey(v) } }
    fun setModel(v: String) { viewModelScope.launch { settings.setModel(v) } }
    fun setExplainerOn(v: Boolean) { viewModelScope.launch { settings.setVisionOn(v) } }

    var text by mutableStateOf("")
        private set

    var reading by mutableStateOf<Reading?>(null)
        private set

    var plain by mutableStateOf<String?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var busyRemote by mutableStateOf(false)
        private set

    fun typeText(v: String) {
        text = v
        plain = null
        error = null
        reading = if (v.isBlank()) null else Analyzer.analyze(v)
    }

    /** True while the text on screen came in through the share sheet. */
    var fromShare by mutableStateOf(false)
        private set

    fun receiveShared(v: String) {
        fromShare = true
        typeText(v)
    }

    fun rerun() {
        if (text.isNotBlank()) reading = Analyzer.analyze(text)
    }

    fun clearAll() {
        text = ""
        fromShare = false
        reading = null
        plain = null
        error = null
    }

    fun wipe() {
        text = ""
        fromShare = false
        reading = null
        plain = null
    }

    /**
     * The optional pass. It sends the findings — the labels, the weights and the
     * fragments that matched — and never the message body, because the findings
     * already say everything the wording does. What it is for is the last step: a
     * list of eleven shapes is true but not obvious, and a person holding a phone
     * in a noisy room sometimes needs three sentences instead.
     */
    fun askExplainer() {
        val r = reading ?: return
        viewModelScope.launch {
            busyRemote = true
            error = null
            runCatching {
                val cfg = settings.current()
                if (!cfg.usable) throw IllegalArgumentException("Add a base URL, model and key in Settings first.")
                withContext(Dispatchers.IO) {
                    ai.complete(
                        endpoint = cfg.endpoint,
                        apiKey = cfg.apiKey,
                        model = cfg.model,
                        prompt = buildPrompt(r),
                        jpegBase64 = null,
                    )
                }
            }.onSuccess { plain = it.trim() }
                .onFailure { error = it.message ?: "The request failed." }
            busyRemote = false
        }
    }

    private fun buildPrompt(r: Reading): String = buildString {
        appendLine("You are the optional plain-words explainer inside ScamGuard, an open-source Android app that")
        appendLine("reads a message and its links with on-device string checks. The findings below were produced on")
        appendLine("the device from a published weight table; you are only asked to put them into words.")
        appendLine()
        appendLine("Total: ${fmt(r.score)} points. Band printed by the app: ${r.band}")
        appendLine(r.note)
        for (s in r.links) {
            appendLine("Link — owner-part ${s.owner}${if (s.suffixKnown) "" else " (suffix not recognised, ownership is a guess)"}.")
            if (s.findings.isEmpty()) appendLine("- nothing matched on this address")
            for (f in s.findings) appendLine("- ${f.label} (${fmt(f.weight)} points). Reason given: ${f.detail}")
        }
        for (f in r.message) appendLine("- ${f.label} (${fmt(f.weight)} points). Reason given: ${f.detail}")
        appendLine()
        appendLine("Task:")
        appendLine("- Two or three sentences for the person who received this, saying which of the shapes above")
        appendLine("  actually matters here and what they should look at with their own eyes before doing anything.")
        appendLine("Rules, all binding:")
        appendLine("- Do not say the message is safe, and do not say it is definitely a scam. Neither is knowable")
        appendLine("  from a weight table, and a false clearance is the most harmful thing this screen can produce.")
        appendLine("- Do not output a percentage, a probability or a score of your own.")
        appendLine("- Do not invent facts about the sender, the company, or the site. Nothing about them was sent.")
        appendLine("- Do not tell the person to call, message or pay anyone, and do not name a helpline or number.")
        appendLine("- Do not introduce any finding that is not listed above.")
        appendLine("- If the findings are all informational or empty, reply exactly: too thin to read.")
        appendLine("- Format: the sentences only. No heading, no bullets, no preamble.")
    }
}
