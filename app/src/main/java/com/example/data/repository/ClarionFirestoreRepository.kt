package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.BuiltInModels
import com.example.data.model.InterviewMessage
import com.example.data.model.InterviewSession
import com.example.data.model.InterviewerPersona
import com.example.data.model.ModelPreset
import com.example.data.model.PreloadedPersonas
import com.example.data.model.SessionScorecard
import com.example.data.model.UserStudioSettings
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ClarionFirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // --- User Settings ---

    fun observeUserSettings(userId: String): Flow<UserStudioSettings?> = flow {
        val path = "users/$userId"
        emitAll(
            userDoc(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserStudioSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.GET, path, userId)
                    throw error
                }
        )
    }

    suspend fun ensureUserSettings(displayName: String): Result<Unit> {
        var path = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            path = ref.path
            val snapshot = ref.get().await()
            if (!snapshot.exists()) {
                val safeName = displayName.trim().ifBlank { "Candidate" }.take(100)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "activePersonaId" to PreloadedPersonas.ELENA_ID,
                    "activeModelId" to BuiltInModels.GEMINI_FLASH_ID,
                    "voiceAutoPlay" to true,
                    "searchGrounding" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUserSettings(
        activePersonaId: String? = null,
        activeModelId: String? = null,
        voiceAutoPlay: Boolean? = null,
        searchGrounding: Boolean? = null
    ): Result<Unit> {
        var path = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val ref = userDoc(uid)
            path = ref.path
            val updates = buildMap<String, Any> {
                activePersonaId?.let { put("activePersonaId", it.take(128)) }
                activeModelId?.let { put("activeModelId", it.take(160)) }
                voiceAutoPlay?.let { put("voiceAutoPlay", it) }
                searchGrounding?.let { put("searchGrounding", it) }
                put("updatedAt", FieldValue.serverTimestamp())
            }
            ref.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- Personas ---

    fun observeCustomPersonas(userId: String): Flow<List<InterviewerPersona>> = flow {
        val path = "users/$userId/personas"
        emitAll(
            userDoc(userId).collection("personas")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InterviewerPersona::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun savePersona(persona: InterviewerPersona): Result<String> {
        var path = "users/unauthenticated/personas"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("personas")
            val isExisting = persona.id.isNotBlank() && persona.userId == uid
            val docRef = if (isExisting) col.document(persona.id) else col.document()
            path = docRef.path

            val safeCadence = when (persona.cadence) {
                "measured", "rapid" -> persona.cadence
                else -> "conversational"
            }

            if (isExisting) {
                val updates = mutableMapOf<String, Any>(
                    "name" to persona.name.trim().ifBlank { "Custom Interviewer" }.take(80),
                    "roleTitle" to persona.roleTitle.trim().ifBlank { "Lead Interviewer" }.take(120),
                    "companyArchetype" to persona.companyArchetype.trim().ifBlank { "Modern Tech Studio" }.take(120),
                    "styleTag" to persona.styleTag.trim().ifBlank { "Custom" }.take(40),
                    "warmth" to persona.warmth.coerceIn(0, 100),
                    "rigor" to persona.rigor.coerceIn(0, 100),
                    "followUpDepth" to persona.followUpDepth.coerceIn(0, 100),
                    "cadence" to safeCadence,
                    "voicePitch" to persona.voicePitch.coerceIn(0.5, 2.0),
                    "voiceRate" to persona.voiceRate.coerceIn(0.5, 2.0),
                    "systemPrompt" to persona.systemPrompt.trim().ifBlank { "Conduct a realistic structured interview." }.take(4000),
                    "presetDrawableKey" to persona.presetDrawableKey.take(64),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                persona.localPortraitFile?.takeIf { it.isNotBlank() }?.let {
                    updates["localPortraitFile"] = it.take(256)
                }
                docRef.update(updates).await()
            } else {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "name" to persona.name.trim().ifBlank { "Custom Interviewer" }.take(80),
                    "roleTitle" to persona.roleTitle.trim().ifBlank { "Lead Interviewer" }.take(120),
                    "companyArchetype" to persona.companyArchetype.trim().ifBlank { "Modern Tech Studio" }.take(120),
                    "styleTag" to persona.styleTag.trim().ifBlank { "Custom" }.take(40),
                    "warmth" to persona.warmth.coerceIn(0, 100),
                    "rigor" to persona.rigor.coerceIn(0, 100),
                    "followUpDepth" to persona.followUpDepth.coerceIn(0, 100),
                    "cadence" to safeCadence,
                    "voicePitch" to persona.voicePitch.coerceIn(0.5, 2.0),
                    "voiceRate" to persona.voiceRate.coerceIn(0.5, 2.0),
                    "systemPrompt" to persona.systemPrompt.trim().ifBlank { "Conduct a realistic structured interview." }.take(4000),
                    "presetDrawableKey" to persona.presetDrawableKey.take(64),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                persona.localPortraitFile?.takeIf { it.isNotBlank() }?.let {
                    payload["localPortraitFile"] = it.take(256)
                }
                docRef.set(payload).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePersona(personaId: String): Result<Unit> {
        var path = "users/unauthenticated/personas/$personaId"
        return try {
            val uid = requireUserId()
            require(personaId.isNotBlank())
            val docRef = userDoc(uid).collection("personas").document(personaId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Custom Model Presets ---

    fun observeModelPresets(userId: String): Flow<List<ModelPreset>> = flow {
        val path = "users/$userId/model_presets"
        emitAll(
            userDoc(userId).collection("model_presets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ModelPreset::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun saveModelPreset(preset: ModelPreset): Result<String> {
        var path = "users/unauthenticated/model_presets"
        return try {
            val uid = requireUserId()
            val col = userDoc(uid).collection("model_presets")
            val isExisting = preset.id.isNotBlank() && preset.userId == uid
            val docRef = if (isExisting) col.document(preset.id) else col.document()
            path = docRef.path

            val safeProvider = if (preset.providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) {
                ModelPreset.PROVIDER_OPENAI_COMPATIBLE
            } else {
                ModelPreset.PROVIDER_GEMINI
            }

            if (isExisting) {
                val updates = mapOf(
                    "label" to preset.label.trim().ifBlank { preset.modelId }.take(80),
                    "providerType" to safeProvider,
                    "modelId" to preset.modelId.trim().take(160),
                    "baseUrl" to preset.baseUrl.trim().take(300),
                    "temperature" to preset.temperature.coerceIn(0.0, 2.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(updates).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "label" to preset.label.trim().ifBlank { preset.modelId }.take(80),
                    "providerType" to safeProvider,
                    "modelId" to preset.modelId.trim().take(160),
                    "baseUrl" to preset.baseUrl.trim().take(300),
                    "temperature" to preset.temperature.coerceIn(0.0, 2.0),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteModelPreset(presetId: String): Result<Unit> {
        var path = "users/unauthenticated/model_presets/$presetId"
        return try {
            val uid = requireUserId()
            require(presetId.isNotBlank())
            val docRef = userDoc(uid).collection("model_presets").document(presetId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- Interview Sessions & Messages ---

    fun observeSessions(userId: String): Flow<List<InterviewSession>> = flow {
        val path = "users/$userId/sessions"
        emitAll(
            userDoc(userId).collection("sessions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InterviewSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    fun observeMessages(userId: String, sessionId: String): Flow<List<InterviewMessage>> = flow {
        val path = "users/$userId/sessions/$sessionId/messages"
        emitAll(
            userDoc(userId).collection("sessions").document(sessionId)
                .collection("messages")
                .orderBy("sequenceIndex", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        InterviewMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, OperationType.LIST, path, userId)
                    throw error
                }
        )
    }

    suspend fun createSession(
        persona: InterviewerPersona,
        targetRole: String,
        model: ModelPreset
    ): Result<String> {
        var path = "users/unauthenticated/sessions"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("sessions").document()
            path = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "personaId" to persona.id.ifBlank { PreloadedPersonas.ELENA_ID }.take(128),
                "personaName" to persona.name.ifBlank { "Elena Vance" }.take(80),
                "personaRole" to persona.roleTitle.ifBlank { "Interviewer" }.take(120),
                "targetRole" to targetRole.trim().ifBlank { "Senior Product & Engineering Leader" }.take(140),
                "modelLabel" to model.label.ifBlank { model.modelId }.take(100),
                "modelId" to model.modelId.ifBlank { "gemini-3.8-flash" }.take(160),
                "status" to InterviewSession.STATUS_ACTIVE,
                "turnCount" to 0,
                "overallScore" to 0,
                "clarityScore" to 0,
                "depthScore" to 0,
                "confidenceScore" to 0,
                "summaryFeedback" to "",
                "strengths" to emptyList<String>(),
                "improvements" to emptyList<String>(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun appendMessage(
        sessionId: String,
        senderRole: String,
        content: String,
        modelUsed: String,
        hasAttachment: Boolean,
        attachmentLabel: String,
        sequenceIndex: Int
    ): Result<String> {
        var path = "users/unauthenticated/sessions/$sessionId/messages"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("sessions").document(sessionId)
                .collection("messages").document()
            path = docRef.path
            val safeRole = when (senderRole) {
                InterviewMessage.ROLE_CANDIDATE, InterviewMessage.ROLE_COACH -> senderRole
                else -> InterviewMessage.ROLE_INTERVIEWER
            }
            val payload = mapOf(
                "userId" to uid,
                "senderRole" to safeRole,
                "content" to content.trim().ifBlank { "..." }.take(12000),
                "modelUsed" to modelUsed.take(160),
                "hasAttachment" to hasAttachment,
                "attachmentLabel" to attachmentLabel.take(160),
                "sequenceIndex" to sequenceIndex.coerceIn(0, 1000),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSessionProgress(
        sessionId: String,
        turnCount: Int,
        model: ModelPreset
    ): Result<Unit> {
        var path = "users/unauthenticated/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("sessions").document(sessionId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "turnCount" to turnCount.coerceIn(0, 500),
                    "modelLabel" to model.label.ifBlank { model.modelId }.take(100),
                    "modelId" to model.modelId.take(160),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun completeSessionWithScorecard(
        sessionId: String,
        turnCount: Int,
        scorecard: SessionScorecard
    ): Result<Unit> {
        var path = "users/unauthenticated/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("sessions").document(sessionId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "status" to InterviewSession.STATUS_COMPLETED,
                    "turnCount" to turnCount.coerceIn(0, 500),
                    "overallScore" to scorecard.overallScore.coerceIn(0, 100),
                    "clarityScore" to scorecard.clarityScore.coerceIn(0, 100),
                    "depthScore" to scorecard.depthScore.coerceIn(0, 100),
                    "confidenceScore" to scorecard.confidenceScore.coerceIn(0, 100),
                    "summaryFeedback" to scorecard.summaryFeedback.take(4000),
                    "strengths" to scorecard.strengths.take(10),
                    "improvements" to scorecard.improvements.take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        var path = "users/unauthenticated/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val sessionRef = userDoc(uid).collection("sessions").document(sessionId)
            path = sessionRef.path
            val msgs = sessionRef.collection("messages").get().await()
            for (doc in msgs.documents) {
                doc.reference.delete().await()
            }
            sessionRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfAuthenticated(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }
}
