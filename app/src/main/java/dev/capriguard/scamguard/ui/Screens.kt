package dev.capriguard.scamguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.capriguard.scamguard.core.GlassCard
import dev.capriguard.scamguard.core.LiquidBackdrop
import dev.capriguard.scamguard.core.LocalGlass
import dev.capriguard.scamguard.core.Palette
import dev.capriguard.scamguard.core.Radii
import dev.capriguard.scamguard.core.tnum
import dev.capriguard.scamguard.link.Analyzer
import dev.capriguard.scamguard.link.AuditTable
import dev.capriguard.scamguard.link.Bands
import dev.capriguard.scamguard.link.CheckViewModel
import dev.capriguard.scamguard.link.Finding
import dev.capriguard.scamguard.link.Limits
import dev.capriguard.scamguard.link.LinkScan
import dev.capriguard.scamguard.link.Reading
import dev.capriguard.scamguard.link.fmt

/* ------------------------------------------------------------------ shell */

@Composable
fun ScreenShell(
    title: String,
    onBack: (() -> Unit)?,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidBackdrop(base = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconBadge(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                } else {
                    Spacer(Modifier.width(40.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun IconBadge(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(Radii.PillShape)
            .background(LocalGlass.current.scrim)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun OutlinedAction(icon: @Composable () -> Unit, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Radii.ControlShape)
            .border(0.7.dp, LocalGlass.current.hairline, Radii.ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon()
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Weight tells the colour, and nothing else does. Two tiers above grey, one below:
 * the point is to show which lines are worth a second look, not to make the card
 * look like a traffic light.
 */
@Composable
private fun severityTint(weight: Double) = when {
    weight <= 0.0 -> MaterialTheme.colorScheme.outline
    weight < Bands.SOME_SIGNALS_FROM -> MaterialTheme.colorScheme.outline
    weight < Weight6 -> Palette.Warm
    else -> Palette.Alert
}

private const val Weight6 = 6.0

/* ------------------------------------------------------------------- home */

@Composable
fun HomeScreen(vm: CheckViewModel, onOpenSettings: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val r = vm.reading

    ScreenShell(title = "ScamGuard", onBack = null, action = {
        IconBadge(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Settings") }
    }) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (r == null && vm.text.isBlank()) IntroCard()

            InputCard(vm, onPaste = {
                val clip = clipboard.getText()?.text ?: ""
                if (clip.isNotBlank()) vm.typeText(clip)
            })

            if (vm.fromShare && vm.text.isNotBlank()) {
                NoteCard("This arrived from another app through the share sheet. It is not stored, and it leaves memory when ScamGuard goes to the background.")
            }

            if (r != null) {
                BandCard(r)
                if (r.links.isNotEmpty()) r.links.forEach { LinkCard(it, r.links.size) }
                if (r.message.isNotEmpty()) MessageCard(r.message)
                if (r.links.isNotEmpty() && r.links.all { it.findings.isEmpty() } && r.message.isEmpty()) {
                    CleanReadingCard()
                }
                AbsentCard(r.absent)
                ScoreCard(r)
                CannotSeeCard()
                WillNotSayCard()
                PlainCard(vm)

                OutlinedAction(
                    icon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)) },
                    text = "Copy this reading",
                    onClick = { clipboard.setText(AnnotatedString(Analyzer.reportText(r))) },
                )
                OutlinedAction(
                    icon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)) },
                    text = "Clear everything",
                    onClick = vm::clearAll,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun IntroCard() {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Paste it before you tap it", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Share the message in from wherever it landed, or paste it here. ScamGuard reads the wording " +
                    "and every address inside it against a weight table shipped in the app, and prints what " +
                    "matched, what did not, and what it cannot see at all.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "It never opens the link. It never asks a server. A reading is a list of reasons, and you are the " +
                    "one who decides what to do with them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InputCard(vm: CheckViewModel, onPaste: () -> Unit) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("The message, as it arrived")
            OutlinedTextField(
                value = vm.text,
                onValueChange = vm::typeText,
                label = { Text("Text or link", style = MaterialTheme.typography.labelMedium) },
                placeholder = {
                    Text(
                        "Your Shopee account will be suspended in 24 hours. Verify here shopee-pay-help.top/kyc",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                shape = Radii.ControlShape,
                minLines = 5,
                maxLines = 12,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Default),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedAction(
                icon = { Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)) },
                text = "Paste from clipboard",
                onClick = onPaste,
            )
            Text(
                "The reading updates as you type. Nothing is sent anywhere while you do.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    GlassCard(padding = 15.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Filled.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/* ----------------------------------------------------------------- result */

@Composable
private fun BandCard(r: Reading) {
    val tone = severityTint(if (r.scored.isEmpty()) 0.0 else r.scored.maxOf { it.weight })
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("The reading")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fmt(r.score), style = MaterialTheme.typography.displaySmall.tnum(), color = tone)
                Text(
                    "points, ${r.scored.size} of them earned",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(r.band, style = MaterialTheme.typography.headlineSmall)
            Text(r.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Points are a sum over a published table that you can read below. They are not a probability, " +
                    "they are not a percentage, and there is no number this app will not show you the working for.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (r.score >= Bands.MAX_REPORTED) {
                Text(
                    "Capped at ${fmt(Bands.MAX_REPORTED)}. Past that the extra findings say nothing the first " +
                        "twelve did not.",
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                )
            }
        }
    }
}

@Composable
private fun LinkCard(s: LinkScan, total: Int) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle(if (total > 1) "A link in this message" else "The link in this message")
            Text(
                s.raw,
                style = MaterialTheme.typography.bodySmall.tnum(),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "owned by",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Box(
                    Modifier
                        .clip(Radii.PillShape)
                        .border(0.7.dp, MaterialTheme.colorScheme.primary, Radii.PillShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(s.owner, style = MaterialTheme.typography.labelMedium.tnum())
                }
            }
            Text(
                if (s.suffixKnown) {
                    "Read the address right to left. Everything before this pill — the part that looks like a " +
                        "company name — is chosen by whoever registered the address, not the other way round."
                } else {
                    "The suffix here is not in the list this app ships, so even the owner-part is a guess rather " +
                        "than a fact. That is a limit of the app, printed where it applies."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.findings.isEmpty()) {
                Text(
                    "Nothing matched on this address. Most scam addresses are also clean by every test below, " +
                        "because they were registered this week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                s.findings.forEach { FindingLine(it) }
            }
        }
    }
}

@Composable
private fun MessageCard(findings: List<Finding>) {
    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            SectionTitle("What the wording is doing")
            findings.forEach { FindingLine(it) }
        }
    }
}

@Composable
private fun FindingLine(f: Finding) {
    val tone = severityTint(f.weight)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(Radii.PillShape)
                    .background(tone),
            )
            Text(f.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (f.informational) "—" else "${fmt(f.weight)} pt",
                style = MaterialTheme.typography.labelMedium.tnum(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(f.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        if (f.evidence.isNotBlank()) {
            Text(
                "matched: ${f.evidence}",
                style = MaterialTheme.typography.labelSmall.tnum(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(f.on, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun CleanReadingCard() {
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionTitle("Why a clean reading is the weakest answer here")
            Text(
                "Every test below is a shape that has to be visible in the text itself: an address that borrows a " +
                    "name, a suffix sold in bulk, a word that pressures you. A domain registered on Monday, a " +
                    "correctly spelled address and a politely worded request trip none of them. Those are the " +
                    "scams that work, and this app cannot tell you about them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AbsentCard(absent: List<String>) {
    var open by remember { mutableStateOf(false) }
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.ControlShape)
                    .clickable(onClick = { open = !open }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle("Checked, and did not match  (${absent.size})")
                Icon(
                    Icons.Filled.ExpandMore,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) {
                absent.forEach {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.padding(top = 7.dp).size(4.dp).clip(Radii.PillShape).background(MaterialTheme.colorScheme.outline))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    "Printed because a list of what was tested and passed is the only part of a checker you can " +
                        "actually audit.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScoreCard(r: Reading) {
    var open by remember { mutableStateOf(false) }
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.ControlShape)
                    .clickable(onClick = { open = !open }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle("The whole table, and what this reading spent")
                Icon(
                    Icons.Filled.ExpandMore,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) {
                val spent = r.all.groupingBy { it.on }.eachCount()
                AuditTable.rows.forEach { row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            fmt(row.points),
                            style = MaterialTheme.typography.bodyMedium.tnum(),
                            color = if (row.points > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.width(34.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(row.what, style = MaterialTheme.typography.bodySmall)
                            Text(row.on, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        val n = spent[row.on] ?: 0
                        if (n > 0) {
                            Text(
                                "${n} in this reading",
                                style = MaterialTheme.typography.labelSmall.tnum(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "Bands: under ${fmt(Bands.SOME_SIGNALS_FROM)} is inconclusive by design; " +
                        "${fmt(Bands.SEVERAL_SIGNALS_FROM)} and above is several shapes agreeing; " +
                        "${fmt(Bands.MAX_REPORTED)} is where the total stops being printed. Framing families score " +
                        "once each, however many of their words appear.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "The brand and suffix lists live in link/Tables.kt and are curated, not complete. A finding " +
                        "you disagree with is a line in that file, and issues against it are welcome.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CannotSeeCard() {
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionTitle("What a phone cannot see from text alone")
            Limits.cannotSee.forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Warning, null, Modifier.size(15.dp), tint = Palette.Warm)
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WillNotSayCard() {
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SectionTitle("What this will not tell you")
            Limits.willNotSay.forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 7.dp).size(4.dp).clip(Radii.PillShape).background(MaterialTheme.colorScheme.outline))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PlainCard(vm: CheckViewModel) {
    val on = vm.config.usable
    GlassCard(padding = 15.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("In plain words, if you want it")
            Text(
                if (on) {
                    "Sends the findings — the labels, the weights and the fragments that matched — to your own " +
                        "endpoint. Not the message body: the findings already carry everything the wording said. " +
                        "It cannot add a finding the table did not produce."
                } else {
                    "Optional, and off. It needs an endpoint you configure in Settings, and the reading above " +
                        "stands on its own without it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (on) {
                Button(
                    onClick = vm::askExplainer,
                    enabled = !vm.busyRemote,
                    shape = Radii.ControlShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                ) {
                    Text(if (vm.busyRemote) "Asking your endpoint…" else "Rewrite the reading", style = MaterialTheme.typography.labelLarge)
                }
            }
            vm.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Alert) }
            vm.plain?.let {
                Box(Modifier.fillMaxWidth().clip(Radii.ControlShape).background(LocalGlass.current.scrim).padding(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Generated by your endpoint from the findings above. Not part of the table, and not a verdict.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Warm,
                        )
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/* --------------------------------------------------------------- settings */

@Composable
fun SettingsScreen(vm: CheckViewModel, onBack: () -> Unit) {
    var reveal by remember { mutableStateOf(false) }

    ScreenShell(title = "Settings", onBack = onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Every check on the main screen runs on the device against tables bundled in the APK, and needs " +
                    "none of this. The endpoint below is only for the optional plain-words pass.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Plain-words pass", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = vm.config.visionOn,
                    onCheckedChange = vm::setExplainerOn,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            SettingsField("Base URL", vm.config.baseUrl, "https://api.example.com", onDone = vm::setBaseUrl)
            SettingsField("Model", vm.config.model, "gpt-4o-mini", onDone = vm::setModel)
            SettingsField(
                label = "API key",
                value = vm.config.apiKey,
                placeholder = "Typed here, kept on this device",
                masked = !reveal,
                secret = true,
                onDone = vm::setApiKey,
            )
            Text(
                "What goes out when you use it: each finding's label, its weight and the fragment that matched, " +
                    "which can include the address itself. The message body is not sent. The prompt forbids " +
                    "clearing a message, declaring one a scam, producing a number, or naming anyone to call.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { reveal = !reveal }) {
                Text(if (reveal) "Hide key" else "Show key", style = MaterialTheme.typography.labelMedium)
            }
            Text(
                "The key lives in this app's private storage and the manifest sets allowBackup=false, so it cannot " +
                    "ride out in a cloud backup, a device transfer or an adb backup. This build ships without a " +
                    "key and without anyone else's quota.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "The same applies to what you check: no history, no queue, no account. Nothing on this screen is " +
                    "needed for a reading, which is the point of the app.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    placeholder: String,
    onDone: (String) -> Unit,
    masked: Boolean = false,
    secret: Boolean = false,
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine = !secret,
        visualTransformation = if (masked) androidx.compose.ui.text.input.PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (secret) KeyboardType.Password else KeyboardType.Uri,
            autoCorrectEnabled = false,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone(text) }),
        shape = Radii.ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
