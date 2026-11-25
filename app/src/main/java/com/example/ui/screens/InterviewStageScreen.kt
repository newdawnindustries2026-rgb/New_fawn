package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.InterviewMessage
import com.example.data.model.InterviewerPersona
import com.example.data.model.MediaAttachment
import com.example.data.model.ModelPreset
import com.example.data.model.SessionScorecard
import com.example.ui.components.PersonaPortrait
import com.example.ui.components.ScoreArcBadge
import com.example.ui.components.StudioPillBadge
import com.example.ui.components.StudioVignetteOverlay
import com.example.ui.components.VoiceWaveformBars
import com.example.viewmodel.ClarionStudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InterviewStageScreen(
    viewModel: ClarionStudioViewModel,
    onNavigateToModels: () -> Unit,
    onNavigateToAvatars: () -> Unit
) {
    val context = LocalContext.current
    val activePersona by viewModel.activePersona.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val allPersonas by viewModel.allPersonas.collectAsStateWithLifecycle()
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val settings by viewModel.userSettings.collectAsStateWithLifecycle()
    val stage by viewModel.liveStage.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()

    val isSpeaking by viewModel.voiceController.isSpeaking.collectAsStateWithLifecycle()
    val isListening by viewModel.voiceController.isListening.collectAsStateWithLifecycle()
    val micLevel by viewModel.voiceController.micLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceController.partialTranscript.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceController.voiceError.collectAsStateWithLifecycle()

    var draftInput by remember { mutableStateOf("") }
    var showPersonaSheet by remember { mutableStateOf(false) }
    var showModelSheet by remember { mutableStateOf(false) }
    var showRoleDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.voiceController.startListening { spoken ->
                viewModel.sendCandidateTurn(spoken)
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val isVideo = mime.startsWith("video/")
            val label = if (isVideo) "Practice Video Clip" else "Resume / Visual Slide"
            viewModel.setAttachment(
                MediaAttachment(
                    uriString = uri.toString(),
                    label = label,
                    mimeType = mime,
                    isVideo = isVideo
                )
            )
        }
    }

    LaunchedEffect(messages.size, stage.streamingReply) {
        if (messages.isNotEmpty() || stage.streamingReply.isNotBlank()) {
            val targetIdx = (messages.size + if (stage.streamingReply.isNotBlank()) 1 else 0) - 1
            if (targetIdx >= 0) {
                listState.animateScrollToItem(targetIdx)
            }
        }
    }

    // Subtle organic breathing motion on portrait for Nora-grade live presence
    val breathTransition = rememberInfiniteTransition(label = "portraitBreath")
    val breathScale by breathTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isSpeaking) 1.032f else 1.014f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isSpeaking) 1600 else 3600,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val stageStatusText = when {
        stage.isScoring -> "SCORING"
        stage.isGenerating -> "THINKING"
        isSpeaking -> "SPEAKING"
        isListening -> "LISTENING"
        stage.activeSessionId != null -> "LIVE STUDIO"
        else -> "STANDBY"
    }

    val stageStatusDot = when {
        isListening -> MaterialTheme.colorScheme.tertiary
        isSpeaking -> MaterialTheme.colorScheme.secondary
        stage.isGenerating || stage.isScoring -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // --- Cinema Studio Portrait Stage ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(256.dp)
                .clip(RoundedCornerShape(26.dp))
                .border(
                    width = if (isSpeaking || isListening) 2.dp else 1.dp,
                    brush = Brush.linearGradient(
                        colors = if (isSpeaking) {
                            listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary)
                        } else if (isListening) {
                            listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary)
                        } else {
                            listOf(
                                MaterialTheme.colorScheme.outline,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            PersonaPortrait(
                persona = activePersona,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(breathScale)
            )
            StudioVignetteOverlay(topAlpha = 0.50f, bottomAlpha = 0.90f)

            // Top overlay controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StudioPillBadge(
                    text = stageStatusText,
                    dotColor = stageStatusDot
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Quick Model switcher pill
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                RoundedCornerShape(50)
                            )
                            .clickable { showModelSheet = true }
                            .testTag("stage_model_switcher")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeModel.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(105.dp)
                            )
                        }
                    }

                    // Voice autoplay toggle
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.toggleVoiceAutoPlay() }
                            .testTag("voice_autoplay_toggle")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (settings.voiceAutoPlay) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                contentDescription = stringResource(R.string.cd_voice_toggle),
                                tint = if (settings.voiceAutoPlay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Bottom overlay: Interviewer nameplate + live acoustic waveform + swap avatar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = activePersona.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = activePersona.styleTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${activePersona.roleTitle} · ${activePersona.companyArchetype}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(horizontalAlignment = Alignment.End) {
                    VoiceWaveformBars(
                        active = isSpeaking || isListening || stage.isGenerating,
                        intensity = if (isListening) micLevel else if (isSpeaking) 0.85f else 0.45f,
                        color = if (isListening) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .width(64.dp)
                            .height(22.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showPersonaSheet = true }
                            .testTag("stage_persona_switcher")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Face,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Switch",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- Studio Telemetry & Controls Strip ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = false,
                onClick = { showRoleDialog = true },
                label = {
                    Text(
                        text = "Role: ${stage.targetRole}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                },
                modifier = Modifier.testTag("target_role_chip")
            )

            FilterChip(
                selected = settings.searchGrounding,
                onClick = { viewModel.toggleSearchGrounding() },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Public,
                        contentDescription = stringResource(R.string.cd_search_grounding),
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Web Search") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.testTag("search_grounding_chip")
            )

            FilterChip(
                selected = stage.deepThinkingEnabled,
                onClick = { viewModel.toggleDeepThinking() },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = stringResource(R.string.cd_deep_thinking),
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("Deep Think") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("deep_thinking_chip")
            )

            if (stage.activeSessionId != null && messages.size >= 2) {
                FilterChip(
                    selected = true,
                    onClick = { viewModel.finishAndScoreSession() },
                    enabled = !stage.isScoring && !stage.isGenerating,
                    leadingIcon = {
                        if (stage.isScoring) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    label = { Text(if (stage.isScoring) "Scoring..." else "Finish & Score") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("finish_and_score_chip")
                )
            }
        }

        // Error banner (real transparent error propagation)
        AnimatedVisibility(visible = stage.stageError != null || voiceError != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stage.stageError ?: voiceError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            viewModel.clearStageError()
                            viewModel.voiceController.clearVoiceError()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cd_close),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // --- Conversation Transcript / Standby Prompt ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && stage.streamingReply.isBlank() && !stage.isGenerating) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ready in the studio with ${activePersona.name}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Warmth ${activePersona.warmth} · Rigor ${activePersona.rigor} · Depth ${activePersona.followUpDepth} · Engine: ${activeModel.modelId}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.startNewInterview() },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("start_interview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Begin Live Interview")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(messages, key = { it.id.ifBlank { "${it.sequenceIndex}_${it.senderRole}" } }) { message ->
                        DialogueTurnBubble(
                            message = message,
                            interviewerName = activePersona.name
                        )
                    }

                    if (stage.streamingReply.isNotBlank()) {
                        item(key = "streaming_live") {
                            DialogueTurnBubble(
                                message = InterviewMessage(
                                    senderRole = InterviewMessage.ROLE_INTERVIEWER,
                                    content = stage.streamingReply,
                                    modelUsed = activeModel.modelId
                                ),
                                interviewerName = activePersona.name,
                                isStreaming = true
                            )
                        }
                    } else if (stage.isGenerating) {
                        item(key = "thinking_live") {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${activePersona.name} is considering your response (${activeModel.label})...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live partial speech transcript banner
        AnimatedVisibility(visible = isListening) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Text(
                    text = partialTranscript.ifBlank { "Listening to your answer... speak naturally" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Pending media attachment pill (Resume photo or Practice video clip)
        AnimatedVisibility(visible = stage.pendingAttachment != null) {
            stage.pendingAttachment?.let { att ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attached: ${att.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.setAttachment(null) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Bottom Studio Composer (Attach + Live Mic + Text Input + Send) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zero-permission Android Photo Picker for resume photos & practice video clips
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                    .testTag("attach_media_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = stringResource(R.string.cd_attach_media),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Voice Mic Button
            Surface(
                shape = CircleShape,
                color = if (isListening) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (isListening) {
                            viewModel.voiceController.stopListening()
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                viewModel.voiceController.startListening { spoken ->
                                    viewModel.sendCandidateTurn(spoken)
                                }
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    .testTag("mic_toggle_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.cd_mic_toggle),
                        tint = if (isListening) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            OutlinedTextField(
                value = draftInput,
                onValueChange = { draftInput = it },
                placeholder = {
                    Text(
                        text = "Speak or type your answer...",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (draftInput.isNotBlank() || stage.pendingAttachment != null) {
                            val toSend = draftInput
                            draftInput = ""
                            viewModel.sendCandidateTurn(toSend)
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("stage_message_input")
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !stage.isGenerating && (draftInput.isNotBlank() || stage.pendingAttachment != null)) {
                        val toSend = draftInput
                        draftInput = ""
                        viewModel.sendCandidateTurn(toSend)
                    }
                    .testTag("send_turn_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send_response),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // --- Quick Persona Switcher Bottom Sheet ---
    if (showPersonaSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPersonaSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Switch Interviewer",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = {
                            showPersonaSheet = false
                            onNavigateToAvatars()
                        }
                    ) {
                        Text("Customize")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                allPersonas.forEach { persona ->
                    val selected = persona.id == activePersona.id
                    QuickPersonaRow(
                        persona = persona,
                        selected = selected,
                        onClick = {
                            viewModel.selectPersona(persona.id)
                            showPersonaSheet = false
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // --- Quick Model Switcher Bottom Sheet ("Run Any Model") ---
    if (showModelSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Active Model",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedButton(
                        onClick = {
                            showModelSheet = false
                            onNavigateToModels()
                        }
                    ) {
                        Text("Add Any Model")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                allModels.forEach { model ->
                    val selected = model.id == activeModel.id
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.selectModel(model.id)
                                showModelSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = model.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${model.modelId} · ${if (model.providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) "Custom API" else "Firebase AI Logic"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // --- Target Role Configuration Sheet ---
    if (showRoleDialog) {
        var roleDraft by remember(stage.targetRole) { mutableStateOf(stage.targetRole) }
        val quickRoles = listOf(
            "Staff Software Engineer",
            "VP of Product",
            "AI Research Scientist",
            "Design Director",
            "Startup Founder Pitch"
        )
        ModalBottomSheet(
            onDismissRequest = { showRoleDialog = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Target Role & Scenario",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = roleDraft,
                    onValueChange = { roleDraft = it },
                    label = { Text("Role or interview scenario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickRoles.forEach { suggestion ->
                        FilterChip(
                            selected = roleDraft == suggestion,
                            onClick = { roleDraft = suggestion },
                            label = { Text(suggestion) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.updateTargetRole(roleDraft)
                        showRoleDialog = false
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Apply Target Role")
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // --- Executive Scorecard Modal Sheet ---
    stage.latestScorecard?.let { scorecard ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissScorecard() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ScorecardContent(
                scorecard = scorecard,
                personaName = activePersona.name,
                targetRole = stage.targetRole,
                onDone = { viewModel.dismissScorecard() }
            )
        }
    }
}

@Composable
private fun DialogueTurnBubble(
    message: InterviewMessage,
    interviewerName: String,
    isStreaming: Boolean = false
) {
    val isCandidate = message.senderRole == InterviewMessage.ROLE_CANDIDATE
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isCandidate) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isCandidate) 18.dp else 4.dp,
                bottomEnd = if (isCandidate) 4.dp else 18.dp
            ),
            color = if (isCandidate) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .border(
                    width = 1.dp,
                    color = if (isCandidate) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isCandidate) 18.dp else 4.dp,
                        bottomEnd = if (isCandidate) 4.dp else 18.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCandidate) "You" else interviewerName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCandidate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                    if (!isCandidate && message.modelUsed.isNotBlank()) {
                        Text(
                            text = if (isStreaming) "${message.modelUsed} · live" else message.modelUsed,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (message.hasAttachment && message.attachmentLabel.isNotBlank()) {
                        Text(
                            text = message.attachmentLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun QuickPersonaRow(
    persona: InterviewerPersona,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PersonaPortrait(
                persona = persona,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = persona.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = persona.roleTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Warmth ${persona.warmth} · Rigor ${persona.rigor} · Depth ${persona.followUpDepth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun ScorecardContent(
    scorecard: SessionScorecard,
    personaName: String,
    targetRole: String,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Executive Debrief",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$targetRole · Interviewed by $personaName",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ScoreArcBadge(scorecard.overallScore, "Overall", accentColor = MaterialTheme.colorScheme.primary)
            ScoreArcBadge(scorecard.clarityScore, "Clarity", accentColor = MaterialTheme.colorScheme.secondary)
            ScoreArcBadge(scorecard.depthScore, "Depth", accentColor = MaterialTheme.colorScheme.primary)
            ScoreArcBadge(scorecard.confidenceScore, "Presence", accentColor = MaterialTheme.colorScheme.secondary)
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = scorecard.summaryFeedback,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "STANDOUT STRENGTHS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        scorecard.strengths.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "NEXT-ROUND REFINEMENTS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        scorecard.improvements.forEach { item ->
            Text(
                text = "• $item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 3.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onDone,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Done")
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}
