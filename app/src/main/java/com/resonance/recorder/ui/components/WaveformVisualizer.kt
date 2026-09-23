package com.resonance.recorder.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LiveWaveformVisualizer(
    liveRmsFlow: StateFlow<Float>,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFE84A4A),
    barCount: Int = 48
) {
    val latest by liveRmsFlow.collectAsState()
    val bars = remember(barCount) { mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }
    LaunchedEffect(liveRmsFlow, barCount) {
        while (true) {
            withFrameNanos {
                if (bars.isNotEmpty()) bars.removeAt(0)
                bars.add((latest * 3.2f).coerceIn(0.025f, 1f))
            }
        }
    }
    WaveformCanvas(
        points = bars,
        modifier = modifier.semantics { contentDescription = "Live microphone level" },
        activeColor = color,
        progress = 1f
    )
}

@Composable
fun StaticWaveformVisualizer(
    points: List<Float>,
    progress: Float,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFE84A4A),
    inactiveColor: Color = Color(0xFF777A74).copy(alpha = 0.30f)
) {
    WaveformCanvas(
        points = points,
        modifier = modifier.semantics {
            contentDescription = "Recording waveform, ${(progress.coerceIn(0f, 1f) * 100).toInt()} percent played"
        },
        activeColor = activeColor,
        inactiveColor = inactiveColor,
        progress = progress
    )
}

@Composable
private fun WaveformCanvas(
    points: List<Float>,
    modifier: Modifier,
    activeColor: Color,
    inactiveColor: Color = activeColor.copy(alpha = 0.28f),
    progress: Float
) {
    val gap = with(LocalDensity.current) { 2.dp.toPx() }
    Canvas(modifier = modifier) {
        if (points.isEmpty()) return@Canvas
        val barWidth = ((size.width - gap * (points.size - 1)) / points.size).coerceAtLeast(1f)
        val centerY = size.height / 2f
        val played = (points.size * progress.coerceIn(0f, 1f)).toInt()
        points.forEachIndexed { index, point ->
            val height = (point.coerceIn(0.02f, 1f) * size.height).coerceAtLeast(2.dp.toPx())
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = if (index <= played) activeColor else inactiveColor,
                topLeft = Offset(left, centerY - height / 2f),
                size = Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f)
            )
        }
        if (progress in 0.001f..0.999f) {
            val x = size.width * progress
            drawLine(
                color = activeColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.5.dp.toPx()
            )
            drawCircle(
                color = activeColor,
                radius = 4.dp.toPx(),
                center = Offset(x, centerY)
            )
        }
    }
}
