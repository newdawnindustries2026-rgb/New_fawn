package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.InterviewerPersona
import com.example.data.model.PreloadedPersonas
import com.example.data.repository.PortraitAspectRatio
import com.example.data.repository.PortraitResolution
import com.example.ui.components.PersonaPortrait
import com.example.ui.components.StudioPillBadge
import com.example.ui.components.StudioVignetteOverlay
import com.example.ui.components.TraitMeterRow
import com.example.viewmodel.ClarionStudioViewModel
import com.example.viewmodel.PortraitGenUiState
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonasScreen(
    viewModel: ClarionStudioViewModel,
    onSelectAndEnterStage: () -> Unit
) {
    val allPersonas by viewModel.allPersonas.collectAsStateWithLifecycle()
    val activePersona by viewModel.activePersona.collectAsStateWithLifecycle()
    val portraitGenState by viewModel.portraitGenState.collectAsStateWithLifecycle()

    var editingPersona by remember { mutableStateOf<InterviewerPersona?>(null) }

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
                    text = "Interviewer Avatars",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Preloaded lifelike personas & custom AI portraits",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    viewModel.resetPortraitGenState()
                    editingPersona = InterviewerPersona(
                        name = "",
                        roleTitle = "Senior Hiring Director",
                        companyArchetype = "Global Innovation Lab",
                        styleTag = "Custom",
                        warmth = 74,
                        rigor = 78,
                        followUpDepth = 84,
                        cadence = "conversational",
                        voicePitch = 1.0,
                        voiceRate = 1.0,
                        systemPrompt = "Conduct a natural, lifelike interview balancing warmth with perceptive follow-up questions.",
                        presetDrawableKey = "elena"
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("create_persona_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Avatar")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(allPersonas, key = { it.id }) { persona ->
                val isSelected = persona.id == activePersona.id
                val isCustom = persona.userId.isNotBlank()
                PersonaStudioCard(
                    persona = persona,
                    isSelected = isSelected,
                    isCustom = isCustom,
                    onSelect = {
                        viewModel.selectPersona(persona.id)
                        onSelectAndEnterStage()
                    },
                    onCustomize = {
                        viewModel.resetPortraitGenState()
                        // If editing a preloaded preset, clone it into a customizable version
                        editingPersona = if (isCustom) {
                            persona
                        } else {
                            persona.copy(
                                id = "",
                                userId = "",
                                name = "${persona.name} (Custom)"
                            )
                        }
                    },
                    onDelete = if (isCustom) {
                        { viewModel.deleteCustomPersona(persona.id) }
                    } else null
                )
            }
        }
    }

    editingPersona?.let { initialPersona ->
        ModalBottomSheet(
            onDismissRequest = {
                viewModel.resetPortraitGenState()
                editingPersona = null
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PersonaEditorSheet(
                initial = initialPersona,
                portraitGenState = portraitGenState,
                onGeneratePortrait = { prompt, usePro, aspect, resolution, refBitmap ->
                    viewModel.generateAvatarPortrait(prompt, usePro, aspect, resolution, refBitmap)
                },
                onSave = { updated ->
                    viewModel.saveCustomPersona(updated, activateImmediately = true)
                    viewModel.resetPortraitGenState()
                    editingPersona = null
                },
                onCancel = {
                    viewModel.resetPortraitGenState()
                    editingPersona = null
                }
            )
        }
    }
}

@Composable
private fun PersonaStudioCard(
    persona: InterviewerPersona,
    isSelected: Boolean,
    isCustom: Boolean,
    onSelect: () -> Unit,
    onCustomize: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape = RoundedCornerShape(22.dp)
            )
            .testTag("persona_card_${persona.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .clickable(onClick = onSelect)
            ) {
                PersonaPortrait(
                    persona = persona,
                    modifier = Modifier.fillMaxSize()
                )
                StudioVignetteOverlay(topAlpha = 0.25f, bottomAlpha = 0.90f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StudioPillBadge(
                        text = persona.styleTag,
                        dotColor = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    )

                    if (isSelected) {
                        StudioPillBadge(
                            text = "ACTIVE",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    } else if (isCustom) {
                        StudioPillBadge(text = "CUSTOM")
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = persona.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${persona.roleTitle} · ${persona.companyArchetype}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TraitMeterRow(
                        label = "Warmth",
                        value = persona.warmth,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    TraitMeterRow(
                        label = "Rigor",
                        value = persona.rigor,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TraitMeterRow(
                        label = "Depth",
                        value = persona.followUpDepth,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = persona.systemPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSelect,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Stage")
                        } else {
                            Text("Use Interviewer")
                        }
                    }

                    OutlinedButton(
                        onClick = onCustomize,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isCustom) Icons.Filled.Edit else Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.cd_edit),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isCustom) "Edit" else "Tune")
                    }

                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.cd_delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonaEditorSheet(
    initial: InterviewerPersona,
    portraitGenState: PortraitGenUiState,
    onGeneratePortrait: (
        prompt: String,
        useProQuality: Boolean,
        aspect: PortraitAspectRatio,
        resolution: PortraitResolution,
        referenceBitmap: android.graphics.Bitmap?
    ) -> Unit,
    onSave: (InterviewerPersona) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var roleTitle by remember { mutableStateOf(initial.roleTitle) }
    var company by remember { mutableStateOf(initial.companyArchetype) }
    var styleTag by remember { mutableStateOf(initial.styleTag) }
    var warmth by remember { mutableFloatStateOf(initial.warmth.toFloat()) }
    var rigor by remember { mutableFloatStateOf(initial.rigor.toFloat()) }
    var depth by remember { mutableFloatStateOf(initial.followUpDepth.toFloat()) }
    var cadence by remember { mutableStateOf(initial.cadence) }
    var voicePitch by remember { mutableDoubleStateOf(initial.voicePitch) }
    var voiceRate by remember { mutableDoubleStateOf(initial.voiceRate) }
    var systemPrompt by remember { mutableStateOf(initial.systemPrompt) }
    var presetDrawableKey by remember { mutableStateOf(initial.presetDrawableKey) }
    var localPortraitFile by remember { mutableStateOf(initial.localPortraitFile) }

    // AI Portrait Generator / Editor controls
    var portraitPrompt by remember {
        mutableStateOf("Poised executive interviewer in warm minimalist architectural office, tailored linen blazer, natural lighting")
    }
    var useProImageModel by remember { mutableStateOf(false) }
    var selectedAspect by remember { mutableStateOf(PortraitAspectRatio.PORTRAIT_3_4) }
    var selectedResolution by remember { mutableStateOf(PortraitResolution.RES_1K) }
    var editCurrentPortrait by remember { mutableStateOf(false) }

    if (portraitGenState is PortraitGenUiState.Success) {
        localPortraitFile = portraitGenState.fileName
    }

    val presetKeys = listOf("elena" to "Elena", "marcus" to "Marcus", "sora" to "Sora", "julian" to "Julian")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (initial.userId.isNotBlank()) "Edit Interviewer Persona" else "Create Custom Interviewer",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(14.dp))

        // Base Avatar Picker Strip
        Text(
            text = "PRELOADED BASE PORTRAIT",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            presetKeys.forEach { (key, label) ->
                val active = presetDrawableKey == key && localPortraitFile.isNullOrBlank()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            presetDrawableKey = key
                            localPortraitFile = null
                        }
                ) {
                    Image(
                        painter = painterResource(id = PreloadedPersonas.drawableForKey(key)),
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.85f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (active) 2.dp else 1.dp,
                                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp)
                            )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Runtime AI Portrait Studio (Gemini Nano Banana 2.1 / Pro Image) ---
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Portrait Studio (Generate or Edit)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = portraitPrompt,
                    onValueChange = { portraitPrompt = it },
                    label = { Text("Describe appearance, attire, lighting, or edit instruction") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PortraitAspectRatio.entries.forEach { aspect ->
                        FilterChip(
                            selected = selectedAspect == aspect,
                            onClick = { selectedAspect = aspect },
                            label = { Text(aspect.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PortraitResolution.entries.forEach { res ->
                        FilterChip(
                            selected = selectedResolution == res,
                            onClick = { selectedResolution = res },
                            label = { Text(res.label) }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (useProImageModel) "Pro" else "Fast",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = useProImageModel,
                        onCheckedChange = { useProImageModel = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit currently selected portrait (Img2Img)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = editCurrentPortrait,
                        onCheckedChange = { editCurrentPortrait = it }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (portraitGenState) {
                    is PortraitGenUiState.Loading -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Synthesizing photorealistic portrait (30–60s)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    is PortraitGenUiState.Success -> {
                        Image(
                            bitmap = portraitGenState.bitmap.asImageBitmap(),
                            contentDescription = "Generated custom interviewer portrait",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    is PortraitGenUiState.Error -> {
                        Text(
                            text = portraitGenState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    PortraitGenUiState.Idle -> Unit
                }

                OutlinedButton(
                    onClick = {
                        val refBitmap = if (editCurrentPortrait) {
                            val local = localPortraitFile?.let { File(File(context.filesDir, "avatars"), it) }
                            if (local != null && local.exists()) {
                                BitmapFactory.decodeFile(local.absolutePath)
                            } else {
                                BitmapFactory.decodeResource(
                                    context.resources,
                                    PreloadedPersonas.drawableForKey(presetDrawableKey)
                                )
                            }
                        } else null
                        onGeneratePortrait(
                            portraitPrompt,
                            useProImageModel,
                            selectedAspect,
                            selectedResolution,
                            refBitmap
                        )
                    },
                    enabled = portraitGenState !is PortraitGenUiState.Loading && portraitPrompt.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_portrait_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (editCurrentPortrait) "Edit Portrait with Gemini" else "Generate Portrait with Gemini")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Identity & Characteristics ---
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text("Interviewer Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("persona_name_input")
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = roleTitle,
            onValueChange = { roleTitle = it.take(120) },
            label = { Text("Role / Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = company,
                onValueChange = { company = it.take(120) },
                label = { Text("Company Archetype") },
                singleLine = true,
                modifier = Modifier.weight(1.3f)
            )
            OutlinedTextField(
                value = styleTag,
                onValueChange = { styleTag = it.take(40) },
                label = { Text("Style Tag") },
                singleLine = true,
                modifier = Modifier.weight(0.9f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "PERSONALITY & VOICE CALIBRATION",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))

        LabeledSlider("Warmth & Empathy", warmth, 0f..100f, "${warmth.roundToInt()}%") { warmth = it }
        LabeledSlider("Analytical Rigor", rigor, 0f..100f, "${rigor.roundToInt()}%") { rigor = it }
        LabeledSlider("Follow-Up Probing Depth", depth, 0f..100f, "${depth.roundToInt()}%") { depth = it }
        LabeledSlider("Voice Pitch", voicePitch.toFloat(), 0.7f..1.4f, String.format("%.2fx", voicePitch)) {
            voicePitch = it.toDouble()
        }
        LabeledSlider("Speaking Pace", voiceRate.toFloat(), 0.8f..1.3f, String.format("%.2fx", voiceRate)) {
            voiceRate = it.toDouble()
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("conversational" to "Conversational", "measured" to "Measured", "rapid" to "Rapid").forEach { (id, label) ->
                FilterChip(
                    selected = cadence == id,
                    onClick = { cadence = id },
                    label = { Text(label) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = { systemPrompt = it.take(4000) },
            label = { Text("Behavior Blueprint & Interview Philosophy") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(50.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.ifBlank { "Custom Interviewer" },
                            roleTitle = roleTitle.ifBlank { "Lead Interviewer" },
                            companyArchetype = company.ifBlank { "Modern Studio" },
                            styleTag = styleTag.ifBlank { "Custom" },
                            warmth = warmth.roundToInt(),
                            rigor = rigor.roundToInt(),
                            followUpDepth = depth.roundToInt(),
                            cadence = cadence,
                            voicePitch = voicePitch,
                            voiceRate = voiceRate,
                            systemPrompt = systemPrompt.ifBlank { "Conduct a realistic structured interview." },
                            presetDrawableKey = presetDrawableKey,
                            localPortraitFile = localPortraitFile
                        )
                    )
                },
                modifier = Modifier.weight(1.4f).height(50.dp).testTag("save_persona_button")
            ) {
                Text("Save & Activate")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = display,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}
