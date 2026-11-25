package com.example.data.model

import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import com.example.R
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserStudioSettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Candidate",
    val activePersonaId: String = PreloadedPersonas.ELENA_ID,
    val activeModelId: String = BuiltInModels.GEMINI_FLASH_ID,
    val voiceAutoPlay: Boolean = true,
    val searchGrounding: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InterviewerPersona(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val roleTitle: String = "",
    val companyArchetype: String = "",
    val styleTag: String = "",
    val warmth: Int = 70,
    val rigor: Int = 75,
    val followUpDepth: Int = 80,
    val cadence: String = "conversational",
    val voicePitch: Double = 1.0,
    val voiceRate: Double = 1.0,
    val systemPrompt: String = "",
    val presetDrawableKey: String = "elena",
    val localPortraitFile: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun buildEffectiveSystemPrompt(targetRole: String, deepThinking: Boolean): String {
        val warmthDesc = when {
            warmth >= 80 -> "warm, encouraging, and deeply attentive"
            warmth >= 55 -> "poised, respectful, and naturally engaging"
            else -> "cool, direct, and unflappably executive"
        }
        val rigorDesc = when {
            rigor >= 85 -> "high-bar and incisive—challenge vague claims and ask for concrete numbers or architecture trade-offs"
            rigor >= 65 -> "structured and thorough—probe for clear STAR outcomes and decision rationale"
            else -> "supportive and exploratory—help the candidate draw out their strongest stories"
        }
        val depthDesc = when {
            followUpDepth >= 80 -> "Ask focused multi-layered follow-ups on what the candidate just said before moving to a new topic."
            followUpDepth >= 55 -> "Ask one thoughtful follow-up per answer when something interesting comes up."
            else -> "Keep momentum brisk across distinct interview dimensions."
        }
        val cadenceDesc = when (cadence) {
            "rapid" -> "Keep replies crisp (2-3 natural spoken sentences) so the conversation moves briskly."
            "measured" -> "Take a calm, unhurried editorial tone (3-4 thoughtful sentences) with natural transitions."
            else -> "Speak like a real human interviewer on a live studio call (2-4 natural spoken sentences)."
        }
        val thinkingNote = if (deepThinking) {
            "Apply deep domain rigor and evaluate subtle second-order trade-offs."
        } else ""

        return buildString {
            appendLine("You are $name, $roleTitle at a $companyArchetype.")
            appendLine("You are conducting a live realistic spoken interview for the target role: $targetRole.")
            appendLine("Core Persona Blueprint: $systemPrompt")
            appendLine("Communication Traits: Be $warmthDesc. Maintain $rigorDesc. $depthDesc $cadenceDesc $thinkingNote")
            appendLine("CRITICAL REALISM RULES (Nora-grade human conversational presence):")
            appendLine("- Speak naturally in first-person dialogue as if talking live over audio/video. Never use markdown bullet lists, bold headers, asterisks, or robotic formatting.")
            appendLine("- Acknowledge a specific detail the candidate just shared before asking your next question—show genuine active listening.")
            appendLine("- Ask ONLY ONE clear question per turn. Never stack 3 questions at once.")
            appendLine("- Stay completely in character as $name; never break character or say you are an AI.")
        }.trim()
    }
}

@Keep
@IgnoreExtraProperties
data class ModelPreset(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val providerType: String = PROVIDER_GEMINI,
    val modelId: String = "",
    val baseUrl: String = "",
    val temperature: Double = 0.7,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_OPENAI_COMPATIBLE = "openai_compatible"
    }
}

@Keep
@IgnoreExtraProperties
data class InterviewSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val personaId: String = "",
    val personaName: String = "",
    val personaRole: String = "",
    val targetRole: String = "",
    val modelLabel: String = "",
    val modelId: String = "",
    val status: String = STATUS_ACTIVE,
    val turnCount: Int = 0,
    val overallScore: Int = 0,
    val clarityScore: Int = 0,
    val depthScore: Int = 0,
    val confidenceScore: Int = 0,
    val summaryFeedback: String = "",
    val strengths: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_COMPLETED = "completed"
    }
}

@Keep
@IgnoreExtraProperties
data class InterviewMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val senderRole: String = ROLE_INTERVIEWER,
    val content: String = "",
    val modelUsed: String = "",
    val hasAttachment: Boolean = false,
    val attachmentLabel: String = "",
    val sequenceIndex: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    companion object {
        const val ROLE_INTERVIEWER = "interviewer"
        const val ROLE_CANDIDATE = "candidate"
        const val ROLE_COACH = "coach"
    }
}

data class SessionScorecard(
    val overallScore: Int,
    val clarityScore: Int,
    val depthScore: Int,
    val confidenceScore: Int,
    val summaryFeedback: String,
    val strengths: List<String>,
    val improvements: List<String>
)

data class MediaAttachment(
    val uriString: String,
    val label: String,
    val mimeType: String,
    val isVideo: Boolean
)

object PreloadedPersonas {
    const val ELENA_ID = "preset_elena"
    const val MARCUS_ID = "preset_marcus"
    const val SORA_ID = "preset_sora"
    const val JULIAN_ID = "preset_julian"

    val defaults: List<InterviewerPersona> = listOf(
        InterviewerPersona(
            id = ELENA_ID,
            name = "Elena Vance",
            roleTitle = "VP of Executive Strategy",
            companyArchetype = "Global Tech & Advisory Firm",
            styleTag = "Nora Realism",
            warmth = 80,
            rigor = 74,
            followUpDepth = 86,
            cadence = "conversational",
            voicePitch = 1.04,
            voiceRate = 0.98,
            systemPrompt = "You embody poised, nuanced human warmth—attentive pauses, natural conversational transitions, and perceptive follow-ups that explore leadership judgment, ambiguity, and real stakeholder dynamics.",
            presetDrawableKey = "elena"
        ),
        InterviewerPersona(
            id = MARCUS_ID,
            name = "Marcus Thorne",
            roleTitle = "Principal Systems Architect",
            companyArchetype = "Hyper-Scale Cloud Infrastructure",
            styleTag = "Deep Tech",
            warmth = 58,
            rigor = 92,
            followUpDepth = 94,
            cadence = "measured",
            voicePitch = 0.86,
            voiceRate = 0.96,
            systemPrompt = "You probe architectural fundamentals, failure modes, latency budgets, and trade-offs with calm Socratic precision. You respect honest 'I don't know' moments and push gently past surface buzzwords.",
            presetDrawableKey = "marcus"
        ),
        InterviewerPersona(
            id = SORA_ID,
            name = "Sora Takahashi",
            roleTitle = "Director of Product & Design",
            companyArchetype = "Design-Led Consumer Studio",
            styleTag = "Empathetic",
            warmth = 88,
            rigor = 68,
            followUpDepth = 82,
            cadence = "conversational",
            voicePitch = 1.12,
            voiceRate = 1.02,
            systemPrompt = "You create psychological safety while uncovering product taste, user empathy, craft obsession, and how candidates navigate cross-functional tension between engineering, design, and business goals.",
            presetDrawableKey = "sora"
        ),
        InterviewerPersona(
            id = JULIAN_ID,
            name = "Julian Mercer",
            roleTitle = "General Partner & Board Panelist",
            companyArchetype = "Tier-1 Venture & Executive Board",
            styleTag = "Stress-Test",
            warmth = 42,
            rigor = 96,
            followUpDepth = 90,
            cadence = "rapid",
            voicePitch = 0.82,
            voiceRate = 1.06,
            systemPrompt = "You run high-stakes executive and founder-level pressure tests. You cut through fluff cleanly, test conviction under polite skepticism, and drill into unit economics, ownership, and hard trade-offs.",
            presetDrawableKey = "julian"
        )
    )

    @DrawableRes
    fun drawableForKey(key: String): Int = when (key.lowercase()) {
        "marcus" -> R.drawable.img_avatar_marcus
        "sora" -> R.drawable.img_avatar_sora
        "julian" -> R.drawable.img_avatar_julian
        else -> R.drawable.img_avatar_elena
    }
}

object BuiltInModels {
    const val GEMINI_FLASH_ID = "builtin_gemini_38_flash"
    const val GEMINI_LITE_ID = "builtin_gemini_35_flash_lite"
    const val GEMINI_ARGON_ID = "builtin_gemini_4_argon"

    val defaults: List<ModelPreset> = listOf(
        ModelPreset(
            id = GEMINI_FLASH_ID,
            label = "Gemini 3.8 Flash",
            providerType = ModelPreset.PROVIDER_GEMINI,
            modelId = "gemini-3.8-flash",
            baseUrl = "",
            temperature = 0.75
        ),
        ModelPreset(
            id = GEMINI_LITE_ID,
            label = "Gemini 3.5 Flash-Lite",
            providerType = ModelPreset.PROVIDER_GEMINI,
            modelId = "gemini-3.5-flash-lite",
            baseUrl = "",
            temperature = 0.7
        ),
        ModelPreset(
            id = GEMINI_ARGON_ID,
            label = "Gemini 4 Argon (Deep)",
            providerType = ModelPreset.PROVIDER_GEMINI,
            modelId = "gemini-4-argon",
            baseUrl = "",
            temperature = 0.6
        )
    )
}
