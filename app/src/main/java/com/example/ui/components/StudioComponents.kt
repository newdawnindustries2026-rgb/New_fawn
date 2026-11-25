package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.InterviewerPersona
import com.example.data.model.PreloadedPersonas
import java.io.File

@Composable
fun PersonaPortrait(
    persona: InterviewerPersona,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.cd_persona_portrait),
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val localFile = remember(persona.localPortraitFile) {
        persona.localPortraitFile
            ?.takeIf { it.isNotBlank() }
            ?.let { File(File(context.filesDir, "avatars"), it) }
            ?.takeIf { it.exists() }
    }

    if (localFile != null) {
        AsyncImage(
            model = localFile,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    } else {
        Image(
            painter = painterResource(id = PreloadedPersonas.drawableForKey(persona.presetDrawableKey)),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier
        )
    }
}

@Composable
fun VoiceWaveformBars(
    active: Boolean,
    intensity: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    Canvas(modifier = modifier) {
        val barCount = 9
        val spacing = size.width / (barCount * 2f)
        val barWidth = spacing.coerceAtLeast(3f)
        val centerY = size.height / 2f

        for (i in 0 until barCount) {
            val factor = if (active) {
                val wave = kotlin.math.abs(kotlin.math.sin(phase + i * 0.75f)).toFloat()
                val boosted = (0.28f + wave * 0.72f) * (0.55f + intensity * 0.45f)
                boosted.coerceIn(0.18f, 1f)
            } else {
                0.18f
            }
            val barHeight = size.height * factor
            val x = spacing / 2f + i * (barWidth + spacing)
            drawLine(
                color = color.copy(alpha = if (active) 0.92f else 0.35f),
                start = Offset(x, centerY - barHeight / 2f),
                end = Offset(x, centerY + barHeight / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun TraitMeterRow(
    label: String,
    value: Int,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$value",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value.coerceIn(0, 100) / 100f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun ScoreArcBadge(
    score: Int,
    label: String,
    size: Dp = 74.dp,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val strokeWidth = 6.dp.toPx()
                val arcSize = Size(this.size.width - strokeWidth, this.size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentColor,
                    startAngle = -90f,
                    sweepAngle = (score.coerceIn(0, 100) / 100f) * 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Text(
                text = "$score",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StudioPillBadge(
    text: String,
    dotColor: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        modifier = modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            shape = RoundedCornerShape(50)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StudioVignetteOverlay(
    topAlpha: Float = 0.45f,
    bottomAlpha: Float = 0.92f
) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to bg.copy(alpha = topAlpha),
                        0.42f to Color.Transparent,
                        0.72f to bg.copy(alpha = 0.62f),
                        1.0f to bg.copy(alpha = bottomAlpha)
                    )
                )
            )
    )
}
