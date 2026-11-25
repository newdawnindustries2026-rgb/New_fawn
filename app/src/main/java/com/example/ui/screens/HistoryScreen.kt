package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.InterviewMessage
import com.example.data.model.InterviewSession
import com.example.data.model.UiState
import com.example.ui.components.ScoreArcBadge
import com.example.ui.components.StudioPillBadge
import com.example.viewmodel.ClarionStudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: ClarionStudioViewModel,
    onSignOut: () -> Unit
) {
    val sessionsState by viewModel.sessionsState.collectAsStateWithLifecycle()
    val selectedSessionId by viewModel.selectedHistorySessionId.collectAsStateWithLifecycle()
    val historyMessages by viewModel.historyMessages.collectAsStateWithLifecycle()

    val currentUser = Firebase.auth.currentUser
    val dateFormatter = remember { SimpleDateFormat("MMM d · h:mm a", Locale.US) }

    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val selectedSession = sessions.firstOrNull { it.id == selectedSessionId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Interview Archive",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = currentUser?.email ?: currentUser?.displayName ?: "Signed in with Google",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("sign_out_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.cd_sign_out),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Sign Out")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (val state = sessionsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is UiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is UiState.Success -> {
                val list = state.data
                val completed = list.filter { it.status == InterviewSession.STATUS_COMPLETED }
                val avgScore = if (completed.isNotEmpty()) {
                    completed.map { it.overallScore }.average().toInt()
                } else 0

                // Executive Summary Banner
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatBlock("Sessions", "${list.size}")
                        StatBlock("Scored", "${completed.size}")
                        StatBlock("Avg Score", if (completed.isNotEmpty()) "$avgScore" else "—")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (list.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No recorded interviews yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start a live session on the Stage tab to save transcripts and scorecards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(list, key = { it.id }) { session ->
                            val ts = session.updatedAt ?: session.createdAt ?: Timestamp.now()
                            val formattedDate = dateFormatter.format(ts.toDate())
                            SessionArchiveCard(
                                session = session,
                                formattedDate = formattedDate,
                                onClick = { viewModel.openHistorySession(session.id) },
                                onDelete = { viewModel.deleteSession(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedSession != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.openHistorySession(null) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SessionDetailSheet(
                session = selectedSession,
                messages = historyMessages
            )
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionArchiveCard(
    session: InterviewSession,
    formattedDate: String,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = session.status == InterviewSession.STATUS_COMPLETED
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("session_card_${session.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StudioPillBadge(
                        text = if (isCompleted) "SCORED ${session.overallScore}" else "IN PROGRESS",
                        dotColor = if (isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = session.targetRole,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${session.personaName} · ${session.modelLabel} · ${session.turnCount} turns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.cd_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SessionDetailSheet(
    session: InterviewSession,
    messages: List<InterviewMessage>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = session.targetRole,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Interviewer: ${session.personaName} · Engine: ${session.modelId}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (session.status == InterviewSession.STATUS_COMPLETED) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ScoreArcBadge(session.overallScore, "Overall", accentColor = MaterialTheme.colorScheme.primary)
                ScoreArcBadge(session.clarityScore, "Clarity", accentColor = MaterialTheme.colorScheme.secondary)
                ScoreArcBadge(session.depthScore, "Depth", accentColor = MaterialTheme.colorScheme.primary)
                ScoreArcBadge(session.confidenceScore, "Presence", accentColor = MaterialTheme.colorScheme.secondary)
            }
            if (session.summaryFeedback.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = session.summaryFeedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (session.strengths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "STRENGTHS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                session.strengths.forEach { s ->
                    Text("• $s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (session.improvements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "REFINEMENTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                session.improvements.forEach { imp ->
                    Text("• $imp", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "TRANSCRIPT (${messages.size} TURNS)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        messages.forEach { msg ->
            val speaker = if (msg.senderRole == InterviewMessage.ROLE_CANDIDATE) "You" else session.personaName
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = speaker,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (msg.senderRole == InterviewMessage.ROLE_CANDIDATE) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
