package dev.capriguard.scamguard.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/* --------------------------------------------------------------- settings */

@Suppress("DEPRECATION")
private val Context.guardStore: DataStore<Preferences> by preferencesDataStore(name = "guard_settings")

object Keys {
    val BaseUrl = stringPreferencesKey("ai_base_url")
    val ApiKey = stringPreferencesKey("ai_api_key")
    val Model = stringPreferencesKey("ai_model")
    val VisionOn = booleanPreferencesKey("ai_vision_enabled")
}

data class AiConfig(val baseUrl: String, val apiKey: String, val model: String, val visionOn: Boolean) {
    /** Anything the analysis path can actually attempt. */
    val usable: Boolean get() = visionOn && apiKey.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()

    val endpoint: String
        get() {
            val trimmed = baseUrl.trim().trimEnd('/')
            val root = if (trimmed.endsWith("/v1")) trimmed else "$trimmed/v1"
            return "$root/chat/completions"
        }
}

class SettingsRepository(private val context: Context) {

    val config: Flow<AiConfig> = context.guardStore.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            AiConfig(
                baseUrl = p[Keys.BaseUrl] ?: DEFAULT_BASE_URL,
                apiKey = p[Keys.ApiKey] ?: "",
                model = p[Keys.Model] ?: DEFAULT_MODEL,
                visionOn = p[Keys.VisionOn] ?: false,
            )
        }

    suspend fun setBaseUrl(v: String) = context.guardStore.edit { it[Keys.BaseUrl] = v.trim() }
    suspend fun setApiKey(v: String) = context.guardStore.edit { it[Keys.ApiKey] = v.trim() }
    suspend fun setModel(v: String) = context.guardStore.edit { it[Keys.Model] = v.trim() }
    suspend fun setVisionOn(v: Boolean) = context.guardStore.edit { it[Keys.VisionOn] = v }

    /** One-shot read for callers that are not in composition. */
    suspend fun current(): AiConfig = config.first()

    companion object {
        /** Deliberately empty: no vendor ships with someone else's quota baked in. */
        const val DEFAULT_BASE_URL = "https://api.openai.com"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}

/* --------------------------------------------------------------- ai client */

class AiNotConfigured : IOException("No AI endpoint configured")

/**
 * Talks to any OpenAI-compatible chat-completions endpoint. The user supplies
 * base URL, key and model; nothing is proxied through us, and the key never
 * leaves this device's private storage.
 */
class AiClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .build()

    /**
     * @param jpegBase64 base64 of a downscaled JPEG, or null for a text-only turn
     * @throws IOException with a human-readable reason on any HTTP or parse failure
     */
    fun complete(endpoint: String, apiKey: String, model: String, prompt: String, jpegBase64: String?): String {
        val content = JSONArray()
        if (jpegBase64 != null) {
            content.put(
                JSONObject()
                    .put("type", "text")
                    .put("text", "Image is provided below at roughly 1024px on its long edge. Judge only what is visible."),
            )
            content.put(
                JSONObject()
                    .put("type", "image_url")
                    .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")),
            )
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 900)
            .put("temperature", 0.2)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .toString()

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(text).getJSONObject("error").optString("message")
                }.getOrNull()
                throw IOException(
                    when {
                        response.code == 401 || response.code == 403 ->
                            "Key rejected by the endpoint (HTTP ${response.code}). Check the API key in Settings."
                        response.code == 404 ->
                            "Endpoint not found (HTTP 404). Check the base URL — it should look like https://host/v1/chat/completions."
                        response.code == 429 ->
                            "Rate limited (HTTP 429). Wait a moment or lower your request volume."
                        else ->
                            "HTTP ${response.code}${if (detail.isNullOrBlank()) "" else ": $detail"}"
                    },
                )
            }
            return runCatching {
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }.getOrElse { throw IOException("The endpoint returned a body this app could not read.") }
        }
    }

    /** Off the main thread; cancellation is propagated rather than swallowed. */
    suspend fun await(
        endpoint: String,
        apiKey: String,
        model: String,
        prompt: String,
        jpegBase64: String?,
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            complete(endpoint, apiKey, model, prompt, jpegBase64)
        } catch (ce: CancellationException) {
            throw ce
        }
    }
}

fun encodeJpegBase64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
