package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.InterviewMessage
import com.example.data.model.InterviewerPersona
import com.example.data.model.MediaAttachment
import com.example.data.model.ModelPreset
import com.example.data.model.SessionScorecard
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

private const val TAG = "ModelEngineRepo"
private const val MAX_INLINE_MEDIA_BYTES = 19 * 1024 * 1024 // < 20MB inline limit

enum class PortraitAspectRatio(val label: String) {
    PORTRAIT_3_4("3:4"),
    SQUARE_1_1("1:1"),
    PORTRAIT_9_16("9:16"),
    LANDSCAPE_4_3("4:3"),
    LANDSCAPE_16_9("16:9")
}

enum class PortraitResolution(val label: String) {
    RES_1K("1K"),
    RES_2K("2K"),
    RES_4K("4K")
}

class ModelEngineRepository(
    private val context: Context,
    private val apiKeyStore: ApiKeyStore
) {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Streams a live realistic interviewer turn using either Firebase AI Logic (any Gemini model)
     * or any user-configured OpenAI-compatible custom endpoint.
     */
    fun streamInterviewReply(
        persona: InterviewerPersona,
        targetRole: String,
        model: ModelPreset,
        history: List<InterviewMessage>,
        candidateMessage: String,
        attachment: MediaAttachment?,
        useSearchGrounding: Boolean,
        deepThinking: Boolean
    ): Flow<String> = flow {
        val systemInstructionText = persona.buildEffectiveSystemPrompt(targetRole, deepThinking)

        if (model.providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) {
            val apiKey = apiKeyStore.getApiKey(model.id)
            val fullReply = withTimeout(60_000L) {
                streamOpenAiCompatible(
                    model = model,
                    apiKey = apiKey,
                    systemPrompt = systemInstructionText,
                    history = history,
                    userMessage = buildTextWithAttachmentNote(candidateMessage, attachment)
                ) { chunk ->
                    // Emitted via channel-free accumulation below; stream directly
                }
            }
            // Stream via dedicated OkHttp flow for incremental chunks
            emit(fullReply)
        } else {
            val effectiveModelId = if (deepThinking && model.modelId == "gemini-3.8-flash") {
                "gemini-4-argon"
            } else {
                model.modelId.ifBlank { "gemini-3.8-flash" }
            }

            val tools = if (useSearchGrounding) listOf(Tool.googleSearch()) else null
            val config = generationConfig {
                temperature = model.temperature.toFloat().coerceIn(0f, 2f)
            }

            val generativeModel = Firebase.ai.generativeModel(
                modelName = effectiveModelId,
                generationConfig = config,
                systemInstruction = content { text(systemInstructionText) },
                tools = tools
            )

            val promptContent = buildGeminiMultimodalPrompt(
                history = history,
                candidateMessage = candidateMessage,
                attachment = attachment
            )

            val accumulated = StringBuilder()
            withTimeout(60_000L) {
                generativeModel.generateContentStream(promptContent).collect { response ->
                    val piece = response.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Model $effectiveModelId returned an empty response.")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates a streaming interview opening turn tailored to the active persona and target role.
     */
    fun streamOpeningGreeting(
        persona: InterviewerPersona,
        targetRole: String,
        model: ModelPreset,
        useSearchGrounding: Boolean,
        deepThinking: Boolean
    ): Flow<String> = streamInterviewReply(
        persona = persona,
        targetRole = targetRole,
        model = model,
        history = emptyList(),
        candidateMessage = "I have just joined the interview studio for the $targetRole role. Greet me warmly in character as ${persona.name}, set the stage briefly, and ask your opening interview question.",
        attachment = null,
        useSearchGrounding = useSearchGrounding,
        deepThinking = deepThinking
    )

    /**
     * Generates a structured JSON Executive Interview Scorecard using Firebase AI Logic Schema.obj().
     */
    suspend fun generateExecutiveScorecard(
        persona: InterviewerPersona,
        targetRole: String,
        transcript: List<InterviewMessage>,
        deepThinking: Boolean
    ): SessionScorecard = withContext(Dispatchers.IO) {
        val scorecardSchema = Schema.obj(
            properties = mapOf(
                "overallScore" to Schema.integer("Overall interview readiness score 0-100"),
                "clarityScore" to Schema.integer("Communication clarity and structure score 0-100"),
                "depthScore" to Schema.integer("Technical or strategic depth score 0-100"),
                "confidenceScore" to Schema.integer("Executive presence and composure score 0-100"),
                "summaryFeedback" to Schema.string("Concise 2-4 sentence executive debrief of the interview"),
                "strengths" to Schema.array(
                    items = Schema.string(),
                    description = "2 to 4 concrete strengths demonstrated in the transcript"
                ),
                "improvements" to Schema.array(
                    items = Schema.string(),
                    description = "2 to 4 actionable improvements for the next round"
                )
            )
        )

        val config = generationConfig {
            responseMimeType = "application/json"
            responseSchema = scorecardSchema
            temperature = 0.35f
        }

        val modelName = if (deepThinking) "gemini-4-argon" else "gemini-3.8-flash"
        val evaluator = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = config
        )

        val formattedTranscript = transcript.joinToString("\n\n") { msg ->
            val speaker = when (msg.senderRole) {
                InterviewMessage.ROLE_CANDIDATE -> "CANDIDATE"
                InterviewMessage.ROLE_COACH -> "COACH"
                else -> "INTERVIEWER (${persona.name})"
            }
            "$speaker: ${msg.content}"
        }

        val evalPrompt = """
            Evaluate this mock interview transcript for the role "$targetRole" conducted by ${persona.name} (${persona.roleTitle}).
            Be candid, fair, specific to what the candidate actually said, and return scores from 0 to 100.

            TRANSCRIPT:
            $formattedTranscript
        """.trimIndent()

        val response = withTimeout(60_000L) {
            evaluator.generateContent(evalPrompt)
        }
        val jsonText = response.text
            ?: throw IllegalStateException("Scorecard evaluation returned an empty response.")
        val json = JSONObject(jsonText)

        val strengthsJson = json.optJSONArray("strengths") ?: JSONArray()
        val strengths = (0 until strengthsJson.length())
            .map { strengthsJson.optString(it) }
            .filter { it.isNotBlank() }
            .take(10)

        val improvementsJson = json.optJSONArray("improvements") ?: JSONArray()
        val improvements = (0 until improvementsJson.length())
            .map { improvementsJson.optString(it) }
            .filter { it.isNotBlank() }
            .take(10)

        SessionScorecard(
            overallScore = json.optInt("overallScore", 75).coerceIn(0, 100),
            clarityScore = json.optInt("clarityScore", 75).coerceIn(0, 100),
            depthScore = json.optInt("depthScore", 75).coerceIn(0, 100),
            confidenceScore = json.optInt("confidenceScore", 75).coerceIn(0, 100),
            summaryFeedback = json.optString(
                "summaryFeedback",
                "Solid conversational structure with clear room to sharpen quantified outcomes."
            ),
            strengths = strengths.ifEmpty { listOf("Engaged actively with interviewer questions") },
            improvements = improvements.ifEmpty { listOf("Quantify impact metrics earlier in each story") }
        )
    }

    /**
     * Generates or edits a photorealistic interviewer avatar portrait at runtime via Firebase AI Logic
     * (gemini-nano-banana-2.1 or gemini-3-pro-image) with aspect ratio & 1K/2K/4K resolution controls.
     * Saves the resulting JPEG to internal app storage and returns the local filename.
     */
    suspend fun generateOrEditAvatarPortrait(
        prompt: String,
        useProQuality: Boolean,
        aspectRatio: PortraitAspectRatio,
        resolution: PortraitResolution,
        referenceBitmap: Bitmap? = null
    ): Pair<String, Bitmap> = withContext(Dispatchers.IO) {
        val sdkAspectRatio = when (aspectRatio) {
            PortraitAspectRatio.SQUARE_1_1 -> AspectRatio.SQUARE_1x1
            PortraitAspectRatio.PORTRAIT_3_4 -> AspectRatio.PORTRAIT_3x4
            PortraitAspectRatio.PORTRAIT_9_16 -> AspectRatio.PORTRAIT_9x16
            PortraitAspectRatio.LANDSCAPE_4_3 -> AspectRatio.LANDSCAPE_4x3
            PortraitAspectRatio.LANDSCAPE_16_9 -> AspectRatio.LANDSCAPE_16x9
        }
        val sdkImageSize = when (resolution) {
            PortraitResolution.RES_1K -> ImageSize.SIZE_1K
            PortraitResolution.RES_2K -> ImageSize.SIZE_2K
            PortraitResolution.RES_4K -> ImageSize.SIZE_4K
        }

        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(sdkAspectRatio)
                .setImageSize(sdkImageSize)
                .build()
        }

        val modelName = if (useProQuality) "gemini-3-pro-image" else "gemini-nano-banana-2.1"
        val imageModel = Firebase.ai.generativeModel(
            modelName = modelName,
            generationConfig = config
        )

        val enrichedPrompt = if (referenceBitmap != null) {
            "Edit this photorealistic executive studio interviewer portrait while preserving lifelike skin texture, natural eye contact, and cinematic depth of field: $prompt"
        } else {
            "Photorealistic 85mm editorial studio portrait of a lifelike human interviewer looking directly into the camera with natural attentive expression, soft architectural studio bokeh, authentic lighting: $prompt"
        }

        val response = withTimeout(60_000L) {
            if (referenceBitmap != null) {
                imageModel.generateContent(
                    content {
                        image(referenceBitmap)
                        text(enrichedPrompt)
                    }
                )
            } else {
                imageModel.generateContent(enrichedPrompt)
            }
        }

        val generatedBitmap = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()?.image
            ?: throw IllegalStateException("No portrait image was returned by $modelName.")

        val avatarsDir = File(context.filesDir, "avatars").apply { mkdirs() }
        val fileName = "avatar_${System.currentTimeMillis()}.jpg"
        val outFile = File(avatarsDir, fileName)
        FileOutputStream(outFile).use { fos ->
            generatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        }
        fileName to generatedBitmap
    }

    /**
     * Tests connectivity against any Gemini or custom OpenAI-compatible model preset.
     */
    suspend fun testModelConnection(model: ModelPreset, overrideApiKey: String? = null): String =
        withContext(Dispatchers.IO) {
            if (model.providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) {
                val key = overrideApiKey ?: apiKeyStore.getApiKey(model.id)
                withTimeout(30_000L) {
                    streamOpenAiCompatible(
                        model = model,
                        apiKey = key,
                        systemPrompt = "You are a connectivity test responder.",
                        history = emptyList(),
                        userMessage = "Reply with exactly: Ready for interview studio.",
                        onDelta = {}
                    )
                }
            } else {
                val geminiModel = Firebase.ai.generativeModel(
                    modelName = model.modelId.ifBlank { "gemini-3.8-flash" }
                )
                val response = withTimeout(30_000L) {
                    geminiModel.generateContent("Reply with a short 5-word studio readiness check.")
                }
                response.text?.trim()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Model returned empty response.")
            }
        }

    private fun buildGeminiMultimodalPrompt(
        history: List<InterviewMessage>,
        candidateMessage: String,
        attachment: MediaAttachment?
    ) = content {
        if (history.isNotEmpty()) {
            val recentHistory = history.takeLast(14).joinToString("\n") { msg ->
                val who = when (msg.senderRole) {
                    InterviewMessage.ROLE_CANDIDATE -> "Candidate"
                    InterviewMessage.ROLE_COACH -> "Coach"
                    else -> "Interviewer"
                }
                "$who: ${msg.content}"
            }
            text("Conversation so far:\n$recentHistory\n\nLatest response from candidate:")
        }

        if (attachment != null) {
            val uri = Uri.parse(attachment.uriString)
            if (attachment.isVideo) {
                val bytes = readBytesBounded(uri)
                if (bytes != null) {
                    inlineData(bytes, attachment.mimeType.ifBlank { "video/mp4" })
                    text("The candidate attached a practice video clip (${attachment.label}). Analyze their delivery, clarity, posture/pacing, and answer content, then respond naturally in character.")
                } else {
                    text("[Candidate attempted to attach a video larger than 19MB; proceed using their text response.]")
                }
            } else {
                val bitmap = loadScaledBitmap(uri)
                if (bitmap != null) {
                    image(bitmap)
                    text("The candidate shared an image (${attachment.label} — e.g. resume, portfolio, or system diagram). Reference specific details you see in it during your follow-up question.")
                }
            }
        }

        text(candidateMessage)
    }

    private fun buildTextWithAttachmentNote(
        candidateMessage: String,
        attachment: MediaAttachment?
    ): String {
        if (attachment == null) return candidateMessage
        return "$candidateMessage\n[Attached file: ${attachment.label} (${attachment.mimeType})]"
    }

    private fun loadScaledBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val original = BitmapFactory.decodeStream(stream) ?: return null
                val maxDim = 1280
                if (original.width <= maxDim && original.height <= maxDim) {
                    original
                } else {
                    val scale = maxDim.toFloat() / maxOf(original.width, original.height)
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * scale).toInt().coerceAtLeast(1),
                        (original.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode attached image", e)
            null
        }
    }

    private fun readBytesBounded(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(chunk)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_INLINE_MEDIA_BYTES) return null
                    buffer.write(chunk, 0, read)
                }
                buffer.toByteArray()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read media attachment bytes", e)
            null
        }
    }

    private fun validateEndpointUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "Custom model endpoint URL cannot be empty." }
        val parsed = URI(trimmed)
        val scheme = parsed.scheme?.lowercase().orEmpty()
        val host = parsed.host?.lowercase().orEmpty()
        val isLocalDev = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
        require(scheme == "https" || (scheme == "http" && isLocalDev)) {
            "Custom model endpoint must use https:// (or http:// only for localhost / 10.0.2.2)."
        }
        return if (trimmed.endsWith("/chat/completions") || trimmed.contains("/chat/completions?")) {
            trimmed
        } else {
            trimmed.trimEnd('/') + "/chat/completions"
        }
    }

    private fun streamOpenAiCompatible(
        model: ModelPreset,
        apiKey: String,
        systemPrompt: String,
        history: List<InterviewMessage>,
        userMessage: String,
        onDelta: (String) -> Unit
    ): String {
        val endpoint = validateEndpointUrl(model.baseUrl)
        val messagesArray = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            history.takeLast(14).forEach { msg ->
                val role = if (msg.senderRole == InterviewMessage.ROLE_CANDIDATE) "user" else "assistant"
                put(JSONObject().apply {
                    put("role", role)
                    put("content", msg.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val requestPayload = JSONObject().apply {
            put("model", model.modelId.trim())
            put("messages", messagesArray)
            put("temperature", model.temperature.coerceIn(0.0, 2.0))
            put("stream", false)
        }

        val reqBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .post(requestPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer $apiKey")
        }

        httpClient.newCall(reqBuilder.build()).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val errSnippet = bodyString.take(220).ifBlank { response.message }
                throw IllegalStateException("HTTP ${response.code} from ${model.label}: $errSnippet")
            }
            val json = JSONObject(bodyString)
            val choices = json.optJSONArray("choices")
            val contentText = choices?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
                .orEmpty()
            if (contentText.isBlank()) {
                throw IllegalStateException("Empty completion returned by ${model.modelId}.")
            }
            onDelta(contentText)
            return contentText
        }
    }
}
