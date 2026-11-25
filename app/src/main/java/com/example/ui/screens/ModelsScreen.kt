package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BuiltInModels
import com.example.data.model.ModelPreset
import com.example.ui.components.StudioPillBadge
import com.example.viewmodel.ClarionStudioViewModel
import com.example.viewmodel.ModelTestUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ClarionStudioViewModel
) {
    val allModels by viewModel.allModels.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val testState by viewModel.modelTestState.collectAsStateWithLifecycle()

    var editingModel by remember { mutableStateOf<ModelPreset?>(null) }

    val builtIn = remember(allModels) { allModels.filter { it.userId.isBlank() } }
    val custom = remember(allModels) { allModels.filter { it.userId.isNotBlank() } }

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
                    text = "Model Engine",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Run Gemini or connect any custom model endpoint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    viewModel.resetModelTestState()
                    editingModel = ModelPreset(
                        label = "",
                        providerType = ModelPreset.PROVIDER_OPENAI_COMPATIBLE,
                        modelId = "",
                        baseUrl = "https://openrouter.ai/api/v1/chat/completions",
                        temperature = 0.7
                    )
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("add_model_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Model")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Live connection probe banner
        when (val state = testState) {
            is ModelTestUiState.Testing -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Probing model endpoint...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            is ModelTestUiState.Success -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = "Model ready: ${state.reply}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            is ModelTestUiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            ModelTestUiState.Idle -> Unit
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "BUILT-IN GEMINI (FIREBASE AI LOGIC · NO API KEY)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            items(builtIn, key = { it.id }) { model ->
                ModelEngineCard(
                    model = model,
                    selected = model.id == activeModel.id,
                    description = when (model.id) {
                        BuiltInModels.GEMINI_LITE_ID -> "Ultra-low latency rapid-fire conversational replies"
                        BuiltInModels.GEMINI_ARGON_ID -> "Deep reasoning & high-rigor architecture / case interviews"
                        else -> "Balanced studio realism, web search grounding & multimodal critique"
                    },
                    onSelect = { viewModel.selectModel(model.id) },
                    onTest = { viewModel.testModelPreset(model) },
                    onEdit = null,
                    onDelete = null
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "YOUR CUSTOM MODELS & EXTERNAL ENDPOINTS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (custom.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Hub,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Bring Any Model You Want",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Add any custom Gemini model ID or connect OpenRouter, OpenAI, Groq, Together, DeepSeek, or a local Ollama / vLLM server. API keys stay strictly on-device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(custom, key = { it.id }) { model ->
                    ModelEngineCard(
                        model = model,
                        selected = model.id == activeModel.id,
                        description = if (model.providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) {
                            model.baseUrl.ifBlank { "Custom OpenAI-compatible API" }
                        } else {
                            "Custom Gemini model via Firebase AI Logic"
                        },
                        onSelect = { viewModel.selectModel(model.id) },
                        onTest = { viewModel.testModelPreset(model) },
                        onEdit = {
                            viewModel.resetModelTestState()
                            editingModel = model
                        },
                        onDelete = { viewModel.deleteModelPreset(model.id) }
                    )
                }
            }
        }
    }

    editingModel?.let { initial ->
        ModalBottomSheet(
            onDismissRequest = { editingModel = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ModelEditorSheet(
                initial = initial,
                viewModel = viewModel,
                onSave = { preset, apiKey ->
                    viewModel.saveModelPreset(preset, apiKey, activateImmediately = true)
                    editingModel = null
                },
                onCancel = { editingModel = null }
            )
        }
    }
}

@Composable
private fun ModelEngineCard(
    model: ModelPreset,
    selected: Boolean,
    description: String,
    onSelect: () -> Unit,
    onTest: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onSelect)
            .testTag("model_card_${model.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.modelId,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (selected) {
                    StudioPillBadge(
                        text = "ACTIVE",
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    StudioPillBadge(
                        text = if (model.providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) "CUSTOM API" else "GEMINI"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test")
                }

                if (!selected) {
                    Button(
                        onClick = onSelect,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Use")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Temp ${String.format("%.2f", model.temperature)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (onEdit != null) {
                    OutlinedButton(
                        onClick = onEdit,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Edit")
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
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

@Composable
private fun ModelEditorSheet(
    initial: ModelPreset,
    viewModel: ClarionStudioViewModel,
    onSave: (ModelPreset, String) -> Unit,
    onCancel: () -> Unit
) {
    var providerType by remember { mutableStateOf(initial.providerType) }
    var label by remember { mutableStateOf(initial.label) }
    var modelId by remember { mutableStateOf(initial.modelId) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf("") }
    var temperature by remember { mutableDoubleStateOf(initial.temperature) }

    LaunchedEffect(initial.id) {
        if (initial.id.isNotBlank()) {
            apiKey = viewModel.getSavedLocalApiKey(initial.id)
        }
    }

    val quickTemplates = listOf(
        Triple("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "anthropic/claude-3.7-sonnet"),
        Triple("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o"),
        Triple("Groq", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile"),
        Triple("Local Ollama", "http://10.0.2.2:11434/v1/chat/completions", "llama3.2")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (initial.id.isNotBlank()) "Edit Model Preset" else "Add Any Model",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE,
                onClick = { providerType = ModelPreset.PROVIDER_OPENAI_COMPATIBLE },
                label = { Text("OpenAI-Compatible API") }
            )
            FilterChip(
                selected = providerType == ModelPreset.PROVIDER_GEMINI,
                onClick = {
                    providerType = ModelPreset.PROVIDER_GEMINI
                    baseUrl = ""
                },
                label = { Text("Custom Gemini ID") }
            )
        }

        if (providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickTemplates.forEach { (name, url, sampleModel) ->
                    FilterChip(
                        selected = baseUrl == url,
                        onClick = {
                            label = name
                            baseUrl = url
                            modelId = sampleModel
                        },
                        label = { Text(name) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it.take(80) },
            label = { Text("Display Name (e.g. Claude via OpenRouter)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("model_label_input")
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it.take(160) },
            label = {
                Text(
                    if (providerType == ModelPreset.PROVIDER_GEMINI) {
                        "Gemini Model ID (e.g. gemini-3.8-flash)"
                    } else {
                        "Model ID (e.g. gpt-4o, llama-3.3-70b-versatile)"
                    }
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("model_id_input")
        )

        if (providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it.take(300) },
                label = { Text("Endpoint URL (https://... or localhost)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("model_url_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key (optional for local servers)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("model_apikey_input")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "API keys stay locally on your device and are never synced to cloud storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Temperature",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format("%.2f", temperature),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Slider(
            value = temperature.toFloat(),
            onValueChange = { temperature = it.toDouble() },
            valueRange = 0f..1.5f
        )

        Spacer(modifier = Modifier.height(14.dp))
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
                    val preset = initial.copy(
                        label = label.trim().ifBlank { modelId.trim() },
                        providerType = providerType,
                        modelId = modelId.trim(),
                        baseUrl = if (providerType == ModelPreset.PROVIDER_OPENAI_COMPATIBLE) baseUrl.trim() else "",
                        temperature = temperature
                    )
                    onSave(preset, apiKey)
                },
                enabled = modelId.isNotBlank() &&
                    (providerType == ModelPreset.PROVIDER_GEMINI || baseUrl.isNotBlank()),
                modifier = Modifier.weight(1.4f).height(50.dp).testTag("save_model_button")
            ) {
                Text("Save & Activate")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
