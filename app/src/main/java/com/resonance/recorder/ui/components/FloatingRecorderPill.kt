package com.resonance.recorder.ui.components

import android.animation.ValueAnimator
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.resonance.recorder.data.db.RecordingEntity
import com.resonance.recorder.ui.theme.GlassContrast
import com.resonance.recorder.ui.theme.LiquidGlassCard
import com.resonance.recorder.ui.theme.LiquidGlassPill
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

@Composable
fun FloatingRecorderPill(
    isRecording: Boolean,
    elapsedMs: Long,
    liveRmsFlow: StateFlow<Float>,
    selectedRecording: RecordingEntity?,
    isPlaying: Boolean,
    playbackPositionMs: Long,
    playbackDurationMs: Long,
    onRecord: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismissPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 350f)
            ),
        outerRadius = 32.dp,
        contrast = if (isRecording) GlassContrast.TINTED else GlassContrast.CLEAR,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        when {
            isRecording -> RecordingControls(elapsedMs, liveRmsFlow, onStop, onCancel)
            selectedRecording != null -> PlaybackControls(
                recording = selectedRecording,
                isPlaying = isPlaying,
                positionMs = playbackPositionMs,
                durationMs = playbackDurationMs.takeIf { it > 0 } ?: selectedRecording.durationMs,
                onPlayPause = onPlayPause,
                onSeek = onSeek,
                onDismiss = onDismissPlayback
            )
            else -> IdleControls(onRecord)
        }
    }
}

@Composable
private fun IdleControls(onRecord: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRecord
            )
            .semantics { contentDescription = "Start recording" }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulsingRecordDot()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Capture a resonance", fontWeight = FontWeight.SemiBold)
            Text(
                "Tap to record · saved only on this device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
            )
        }
        Text("REC", style = MaterialTheme.typography.labelLarge, color = Color(0xFFD73B42))
    }
}

@Composable
private fun PulsingRecordDot() {
    val transition = rememberInfiniteTransition(label = "record pulse")
    val scale by transition.animateFloat(
        initialValue = 0.88f,
        targetValue = if (ValueAnimator.areAnimatorsEnabled()) 1.10f else 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "record dot scale"
    )
    Box(
        Modifier
            .size(46.dp)
            .background(Color(0x1FE84A4A), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(20.dp)
                .scale(scale)
                .background(Color(0xFFE84A4A), CircleShape)
        )
    }
}

@Composable
private fun RecordingControls(
    elapsedMs: Long,
    liveRmsFlow: StateFlow<Float>,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(Color(0xFFE84A4A), CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("Recording", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(elapsedMs), fontWeight = FontWeight.Medium)
        }
        LiveWaveformVisualizer(
            liveRmsFlow = liveRmsFlow,
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(vertical = 10.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button, onClick = onStop),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(6.dp))
                Text("Finish", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel recording")
            }
        }
    }
}

@Composable
private fun PlaybackControls(
    recording: RecordingEntity,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            Column(Modifier.weight(1f)) {
                Text(recording.title, maxLines = 1, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
                )
            }
            AnalysisBadges(recording)
            IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close playback")
            }
        }
        StaticWaveformVisualizer(
            points = recording.waveformPoints,
            progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            modifier = Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 8.dp)
        )
        Slider(
            value = positionMs.coerceAtMost(durationMs).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AnalysisBadges(recording: RecordingEntity) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        recording.detectedKey?.let { key ->
            Badge("$key ${formatCents(recording.centsDeviation)}")
        }
        recording.detectedBPM?.let { Badge("$it BPM") }
    }
}

@Composable
private fun Badge(label: String) {
    LiquidGlassPill(
        contrast = GlassContrast.TINTED,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}

fun formatCents(cents: Int?): String = when {
    cents == null -> ""
    cents > 0 -> "+$cents¢"
    else -> "$cents¢"
}
