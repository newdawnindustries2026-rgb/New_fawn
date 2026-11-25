package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.model.InterviewerPersona
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "InterviewVoiceCtrl"

class InterviewVoiceController(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceError = MutableStateFlow<String?>(null)
    val voiceError: StateFlow<String?> = _voiceError.asStateFlow()

    private var onFinalSpeechCallback: ((String) -> Unit)? = null

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                    }
                })
                ttsReady = true
            } else {
                Log.w(TAG, "TextToSpeech initialization failed with status $status")
            }
        }
    }

    fun speakAsPersona(text: String, persona: InterviewerPersona) {
        val cleaned = text
            .replace(Regex("[*_`#]"), "")
            .trim()
        if (!ttsReady || cleaned.isEmpty()) return
        stopListening()
        mainHandler.post {
            tts?.setPitch(persona.voicePitch.toFloat().coerceIn(0.6f, 1.5f))
            tts?.setSpeechRate(persona.voiceRate.toFloat().coerceIn(0.7f, 1.35f))
            tts?.speak(
                cleaned,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "clarion_${UUID.randomUUID()}"
            )
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            tts?.stop()
            _isSpeaking.value = false
        }
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        _voiceError.value = null
        stopSpeaking()
        onFinalSpeechCallback = onFinalResult

        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                _voiceError.value = "On-device speech recognition is unavailable on this device."
                _isListening.value = false
                return@post
            }

            try {
                speechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _partialTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {
                        _isListening.value = true
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _micLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _isListening.value = false
                        _micLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _micLevel.value = 0f
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Tap the mic to try again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Tap the mic when ready."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for voice interviews."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            else -> "Voice input paused (code $error)."
                        }
                        if (error != SpeechRecognizer.ERROR_CLIENT) {
                            _voiceError.value = message
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _micLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        if (best.isNotEmpty()) {
                            onFinalSpeechCallback?.invoke(best)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                speechRecognizer = recognizer
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                recognizer.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _isListening.value = false
                _voiceError.value = e.localizedMessage ?: "Unable to start microphone."
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (_: Exception) {
            }
            _isListening.value = false
            _micLevel.value = 0f
        }
    }

    fun clearVoiceError() {
        _voiceError.value = null
    }

    fun release() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (_: Exception) {
            }
        }
    }
}
