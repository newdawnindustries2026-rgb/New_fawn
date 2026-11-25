package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.audio.InterviewVoiceController
import com.example.data.model.BuiltInModels
import com.example.data.model.InterviewMessage
import com.example.data.model.InterviewSession
import com.example.data.model.InterviewerPersona
import com.example.data.model.MediaAttachment
import com.example.data.model.ModelPreset
import com.example.data.model.PreloadedPersonas
import com.example.data.model.SessionScorecard
import com.example.data.model.UiState
import com.example.data.model.UserStudioSettings
import com.example.data.repository.ApiKeyStore
import com.example.data.repository.ClarionFirestoreRepository
import com.example.data.repository.ModelEngineRepository
import com.example.data.repository.PortraitAspectRatio
import com.example.data.repository.PortraitResolution
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "ClarionStudioVM"

sealed interface PortraitGenUiState {
    data object Idle : PortraitGenUiState
    data object Loading : PortraitGenUiState
    data class Success(val fileName: String, val bitmap: Bitmap) : PortraitGenUiState
    data class Error(val message: String) : PortraitGenUiState
}

sealed interface ModelTestUiState {
    data object Idle : ModelTestUiState
    data object Testing : ModelTestUiState
    data class Success(val reply: String) : ModelTestUiState
    data class Error(val message: String) : ModelTestUiState
}

data class LiveStageState(
    val activeSessionId: String? = null,
    val targetRole: String = "Senior Product & Engineering Lead",
    val isGenerating: Boolean = false,
    val streamingReply: String = "",
    val deepThinkingEnabled: Boolean = false,
    val pendingAttachment: MediaAttachment? = null,
    val stageError: String? = null,
    val isScoring: Boolean = false,
    val latestScorecard: SessionScorecard? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ClarionStudioViewModel(
    private val currentUserId: String,
    private val firestoreRepo: ClarionFirestoreRepository,
    private val modelEngine: ModelEngineRepository,
    private val apiKeyStore: ApiKeyStore,
    val voiceController: InterviewVoiceController
) : ViewModel() {

    private val _liveStage = MutableStateFlow(LiveStageState())
    val liveStage: StateFlow<LiveStageState> = _liveStage.asStateFlow()

    private val _portraitGenState = MutableStateFlow<PortraitGenUiState>(PortraitGenUiState.Idle)
    val portraitGenState: StateFlow<PortraitGenUiState> = _portraitGenState.asStateFlow()

    private val _modelTestState = MutableStateFlow<ModelTestUiState>(ModelTestUiState.Idle)
    val modelTestState: StateFlow<ModelTestUiState> = _modelTestState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _selectedHistorySessionId = MutableStateFlow<String?>(null)
    val selectedHistorySessionId: StateFlow<String?> = _selectedHistorySessionId.asStateFlow()

    // --- Real-Time Firestore Streams with Two-Tier Error Unwrapping ---

    val userSettings: StateFlow<UserStudioSettings> = firestoreRepo.observeUserSettings(currentUserId)
        .map { it ?: UserStudioSettings(userId = currentUserId) }
        .catch { error ->
            val firestoreError = unwrapFirestoreError(error)
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing user settings", firestoreError ?: error)
            emit(UserStudioSettings(userId = currentUserId))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserStudioSettings(userId = currentUserId)
        )

    val customPersonasState: StateFlow<UiState<List<InterviewerPersona>>> =
        firestoreRepo.observeCustomPersonas(currentUserId)
            .map<List<InterviewerPersona>, UiState<List<InterviewerPersona>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing personas", root)
                emit(UiState.Error(root.message ?: "Failed to load custom personas"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val allPersonas: StateFlow<List<InterviewerPersona>> = customPersonasState
        .map { state ->
            val custom = (state as? UiState.Success)?.data.orEmpty()
            PreloadedPersonas.defaults + custom
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PreloadedPersonas.defaults
        )

    val activePersona: StateFlow<InterviewerPersona> = combine(allPersonas, userSettings) { list, settings ->
        list.firstOrNull { it.id == settings.activePersonaId } ?: PreloadedPersonas.defaults.first()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = PreloadedPersonas.defaults.first()
    )

    val customModelsState: StateFlow<UiState<List<ModelPreset>>> =
        firestoreRepo.observeModelPresets(currentUserId)
            .map<List<ModelPreset>, UiState<List<ModelPreset>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing model presets", root)
                emit(UiState.Error(root.message ?: "Failed to load custom models"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val allModels: StateFlow<List<ModelPreset>> = customModelsState
        .map { state ->
            val custom = (state as? UiState.Success)?.data.orEmpty()
            BuiltInModels.defaults + custom
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = BuiltInModels.defaults
        )

    val activeModel: StateFlow<ModelPreset> = combine(allModels, userSettings) { list, settings ->
        list.firstOrNull { it.id == settings.activeModelId } ?: BuiltInModels.defaults.first()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BuiltInModels.defaults.first()
    )

    val sessionsState: StateFlow<UiState<List<InterviewSession>>> =
        firestoreRepo.observeSessions(currentUserId)
            .map<List<InterviewSession>, UiState<List<InterviewSession>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = unwrapFirestoreError(error)
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing sessions", root)
                emit(UiState.Error(root.message ?: "Failed to load interview history"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val activeMessages: StateFlow<List<InterviewMessage>> = _liveStage
        .map { it.activeSessionId }
        .flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                firestoreRepo.observeMessages(currentUserId, sessionId)
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing active messages", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val historyMessages: StateFlow<List<InterviewMessage>> = _selectedHistorySessionId
        .flatMapLatest { sessionId ->
            if (sessionId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                firestoreRepo.observeMessages(currentUserId, sessionId)
                    .catch { error ->
                        val firestoreError = unwrapFirestoreError(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing history messages", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            val name = Firebase.auth.currentUser?.displayName ?: "Candidate"
            firestoreRepo.ensureUserSettings(name)
        }
    }

    // --- Studio Preferences ---

    fun selectPersona(personaId: String) {
        viewModelScope.launch {
            voiceController.stopSpeaking()
            firestoreRepo.updateUserSettings(activePersonaId = personaId)
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            firestoreRepo.updateUserSettings(activeModelId = modelId)
        }
    }

    fun toggleVoiceAutoPlay() {
        val next = !userSettings.value.voiceAutoPlay
        if (!next) voiceController.stopSpeaking()
        viewModelScope.launch {
            firestoreRepo.updateUserSettings(voiceAutoPlay = next)
        }
    }

    fun toggleSearchGrounding() {
        val next = !userSettings.value.searchGrounding
        viewModelScope.launch {
            firestoreRepo.updateUserSettings(searchGrounding = next)
        }
    }

    fun toggleDeepThinking() {
        _liveStage.value = _liveStage.value.copy(
            deepThinkingEnabled = !_liveStage.value.deepThinkingEnabled
        )
    }

    fun updateTargetRole(role: String) {
        _liveStage.value = _liveStage.value.copy(targetRole = role.take(140))
    }

    fun setAttachment(attachment: MediaAttachment?) {
        _liveStage.value = _liveStage.value.copy(pendingAttachment = attachment)
    }

    fun clearStageError() {
        _liveStage.value = _liveStage.value.copy(stageError = null)
    }

    fun dismissScorecard() {
        _liveStage.value = _liveStage.value.copy(latestScorecard = null)
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    // --- Live Interview Session ---

    fun startNewInterview() {
        if (_liveStage.value.isGenerating) return
        val persona = activePersona.value
        val model = activeModel.value
        val settings = userSettings.value
        val role = _liveStage.value.targetRole.trim().ifBlank { "Senior Software & Product Lead" }

        viewModelScope.launch {
            voiceController.stopSpeaking()
            _liveStage.value = _liveStage.value.copy(
                isGenerating = true,
                streamingReply = "",
                stageError = null,
                latestScorecard = null
            )

            val created = firestoreRepo.createSession(persona, role, model)
            val sessionId = created.getOrElse { err ->
                _liveStage.value = _liveStage.value.copy(
                    isGenerating = false,
                    stageError = err.localizedMessage ?: "Failed to create interview session."
                )
                return@launch
            }

            _liveStage.value = _liveStage.value.copy(activeSessionId = sessionId)

            try {
                var finalGreeting = ""
                modelEngine.streamOpeningGreeting(
                    persona = persona,
                    targetRole = role,
                    model = model,
                    useSearchGrounding = settings.searchGrounding,
                    deepThinking = _liveStage.value.deepThinkingEnabled
                ).collect { partial ->
                    finalGreeting = partial
                    _liveStage.value = _liveStage.value.copy(streamingReply = partial)
                }

                if (finalGreeting.isNotBlank()) {
                    firestoreRepo.appendMessage(
                        sessionId = sessionId,
                        senderRole = InterviewMessage.ROLE_INTERVIEWER,
                        content = finalGreeting,
                        modelUsed = model.modelId,
                        hasAttachment = false,
                        attachmentLabel = "",
                        sequenceIndex = 0
                    )
                    firestoreRepo.updateSessionProgress(sessionId, 1, model)
                    if (settings.voiceAutoPlay) {
                        voiceController.speakAsPersona(finalGreeting, persona)
                    }
                }
                _liveStage.value = _liveStage.value.copy(
                    isGenerating = false,
                    streamingReply = ""
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Opening greeting timed out", e)
                _liveStage.value = _liveStage.value.copy(
                    isGenerating = false,
                    streamingReply = "",
                    stageError = "Model response timed out after 60 seconds. Check connection or try a faster model."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Opening greeting failed", e)
                _liveStage.value = _liveStage.value.copy(
                    isGenerating = false,
                    streamingReply = "",
                    stageError = e.localizedMessage ?: "Failed to start interview with ${model.label}."
                )
            }
        }
    }

    fun sendCandidateTurn(rawText: String) {
        val text = rawText.trim()
        val attachment = _liveStage.value.pendingAttachment
        if ((text.isBlank() && attachment == null) || _liveStage.value.isGenerating) return

        val persona = activePersona.value
        val model = activeModel.value
        val settings = userSettings.value
        val role = _liveStage.value.targetRole.trim().ifBlank { "Senior Product & Engineering Lead" }
        val effectiveText = text.ifBlank { "Please review the attached ${attachment?.label ?: "file"} and ask your follow-up question." }

        viewModelScope.launch {
            voiceController.stopSpeaking()
            _liveStage.value = _liveStage.value.copy(
                isGenerating = true,
                streamingReply = "",
                stageError = null,
                pendingAttachment = null
            )

            var sessionId = _liveStage.value.activeSessionId
            if (sessionId.isNullOrBlank()) {
                val created = firestoreRepo.createSession(persona, role, model)
                sessionId = created.getOrElse { err ->
                    _liveStage.value = _liveStage.value.copy(
                        isGenerating = false,
                        stageError = err.localizedMessage ?: "Failed to initialize session."
                    )
                    return@launch
                }
                _liveStage.value = _liveStage.value.copy(activeSessionId = sessionId)
            }

            val currentHistory = activeMessages.value
            val nextIdx = (currentHistory.maxOfOrNull { it.sequenceIndex } ?: -1) + 1

            firestoreRepo.appendMessage(
                sessionId = sessionId,
                senderRole = InterviewMessage.ROLE_CANDIDATE,
                content = effectiveText,
                modelUsed = "",
                hasAttachment = attachment != null,
                attachmentLabel = attachment?.label.orEmpty(),
                sequenceIndex = nextIdx
            )

            try {
                var finalReply = ""
                modelEngine.streamInterviewReply(
                    persona = persona,
                    targetRole = role,
                    model = model,
                    history = currentHistory,
                    candidateMessage = effectiveText,
                    attachment = attachment,
                    useSearchGrounding = settings.searchGrounding,
                    deepThinking = _liveStage.value.deepThinkingEnabled
                ).collect { partial ->
                    finalReply = partial
                    _liveStage.value = _liveStage.value.copy(streamingReply = partial)
                }

                if (finalReply.isNotBlank()) {
                    firestoreRepo.appendMessage(
                        sessionId = sessionId,
                        senderRole = InterviewMessage.ROLE_INTERVIEWER,
                        content = finalReply,
                        modelUsed = model.modelId,
                        hasAttachment = false,
                        attachmentLabel = "",
                        sequenceIndex = nextIdx + 1
                    )
                    firestoreRepo.updateSessionProgress(sessionId, nextIdx + 2, model)
                    if (settings.voiceAutoPlay) {
                        voiceController.speakAsPersona(finalReply, persona)
                    }
                }
                _liveStage.value = _liveStage.value.copy(
                    isGenerating = false,
                    streamingReply = ""
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Interview reply timed out", e)
                _liveStage.value = _liveStage.value.copy(
                    isGenerating = false,
                    streamingReply = "",
                    stageError = "Response timed out after 60s. You can switch models anytime."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Interview reply failed", e)
                _liveStage.value = _liveStage.value.copy(
                    isGenerating = false,
                    streamingReply = "",
                    stageError = e.localizedMessage ?: "Model inference failed."
                )
            }
        }
    }

    fun finishAndScoreSession() {
        val sessionId = _liveStage.value.activeSessionId ?: return
        val transcript = activeMessages.value
        if (transcript.isEmpty() || _liveStage.value.isScoring) return

        val persona = activePersona.value
        val role = _liveStage.value.targetRole

        viewModelScope.launch {
            voiceController.stopSpeaking()
            _liveStage.value = _liveStage.value.copy(isScoring = true, stageError = null)
            try {
                val scorecard = modelEngine.generateExecutiveScorecard(
                    persona = persona,
                    targetRole = role,
                    transcript = transcript,
                    deepThinking = _liveStage.value.deepThinkingEnabled
                )
                firestoreRepo.completeSessionWithScorecard(
                    sessionId = sessionId,
                    turnCount = transcript.size,
                    scorecard = scorecard
                )
                _liveStage.value = _liveStage.value.copy(
                    isScoring = false,
                    latestScorecard = scorecard,
                    activeSessionId = null
                )
                _snackbarMessage.value = "Interview scored & saved to History"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Scorecard evaluation failed", e)
                _liveStage.value = _liveStage.value.copy(
                    isScoring = false,
                    stageError = e.localizedMessage ?: "Could not generate executive scorecard."
                )
            }
        }
    }

    // --- Persona CRUD & Runtime AI Portrait Studio ---

    fun saveCustomPersona(persona: InterviewerPersona, activateImmediately: Boolean = true) {
        viewModelScope.launch {
            val result = firestoreRepo.savePersona(persona)
            result.onSuccess { newId ->
                if (activateImmediately) {
                    firestoreRepo.updateUserSettings(activePersonaId = newId)
                }
                _snackbarMessage.value = "Saved interviewer ${persona.name}"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not save persona"
            }
        }
    }

    fun deleteCustomPersona(personaId: String) {
        viewModelScope.launch {
            val wasActive = userSettings.value.activePersonaId == personaId
            firestoreRepo.deletePersona(personaId).onSuccess {
                if (wasActive) {
                    firestoreRepo.updateUserSettings(activePersonaId = PreloadedPersonas.ELENA_ID)
                }
                _snackbarMessage.value = "Persona deleted"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not delete persona"
            }
        }
    }

    fun generateAvatarPortrait(
        prompt: String,
        useProQuality: Boolean,
        aspectRatio: PortraitAspectRatio,
        resolution: PortraitResolution,
        referenceBitmap: Bitmap? = null
    ) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _portraitGenState.value = PortraitGenUiState.Loading
            try {
                val (fileName, bitmap) = modelEngine.generateOrEditAvatarPortrait(
                    prompt = prompt.trim(),
                    useProQuality = useProQuality,
                    aspectRatio = aspectRatio,
                    resolution = resolution,
                    referenceBitmap = referenceBitmap
                )
                _portraitGenState.value = PortraitGenUiState.Success(fileName, bitmap)
            } catch (e: TimeoutCancellationException) {
                _portraitGenState.value = PortraitGenUiState.Error(
                    "Portrait generation timed out after 60 seconds. Please try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Avatar portrait generation failed", e)
                _portraitGenState.value = PortraitGenUiState.Error(
                    e.localizedMessage ?: "Portrait generation failed."
                )
            }
        }
    }

    fun resetPortraitGenState() {
        _portraitGenState.value = PortraitGenUiState.Idle
    }

    // --- Custom Models CRUD & Live Connection Test ---

    fun saveModelPreset(preset: ModelPreset, localApiKey: String, activateImmediately: Boolean = true) {
        viewModelScope.launch {
            val result = firestoreRepo.saveModelPreset(preset)
            result.onSuccess { newId ->
                if (preset.providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) {
                    apiKeyStore.saveApiKey(newId, localApiKey)
                }
                if (activateImmediately) {
                    firestoreRepo.updateUserSettings(activeModelId = newId)
                }
                _snackbarMessage.value = "Saved model ${preset.label}"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to save model preset"
            }
        }
    }

    fun deleteModelPreset(presetId: String) {
        viewModelScope.launch {
            val wasActive = userSettings.value.activeModelId == presetId
            firestoreRepo.deleteModelPreset(presetId).onSuccess {
                apiKeyStore.deleteApiKey(presetId)
                if (wasActive) {
                    firestoreRepo.updateUserSettings(activeModelId = BuiltInModels.GEMINI_FLASH_ID)
                }
                _snackbarMessage.value = "Model preset removed"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to remove model preset"
            }
        }
    }

    fun testModelPreset(preset: ModelPreset, overrideApiKey: String? = null) {
        viewModelScope.launch {
            _modelTestState.value = ModelTestUiState.Testing
            try {
                val reply = modelEngine.testModelConnection(preset, overrideApiKey)
                _modelTestState.value = ModelTestUiState.Success(reply)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Model connection test failed", e)
                _modelTestState.value = ModelTestUiState.Error(
                    e.localizedMessage ?: "Connection test failed."
                )
            }
        }
    }

    fun resetModelTestState() {
        _modelTestState.value = ModelTestUiState.Idle
    }

    suspend fun getSavedLocalApiKey(presetId: String): String = apiKeyStore.getApiKey(presetId)

    // --- History ---

    fun openHistorySession(sessionId: String?) {
        _selectedHistorySessionId.value = sessionId
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            if (_selectedHistorySessionId.value == sessionId) {
                _selectedHistorySessionId.value = null
            }
            if (_liveStage.value.activeSessionId == sessionId) {
                _liveStage.value = _liveStage.value.copy(activeSessionId = null)
            }
            firestoreRepo.deleteSession(sessionId).onSuccess {
                _snackbarMessage.value = "Interview session deleted"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not delete session"
            }
        }
    }

    private fun unwrapFirestoreError(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    override fun onCleared() {
        super.onCleared()
        voiceController.release()
    }

    companion object {
        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app: Application = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val firestoreRepo = ClarionFirestoreRepository(db)
                val apiKeyStore = ApiKeyStore(app)
                val modelEngine = ModelEngineRepository(app, apiKeyStore)
                val voiceController = InterviewVoiceController(app)
                ClarionStudioViewModel(
                    currentUserId = currentUserId,
                    firestoreRepo = firestoreRepo,
                    modelEngine = modelEngine,
                    apiKeyStore = apiKeyStore,
                    voiceController = voiceController
                )
            }
        }
    }
}
