package com.resonance.recorder.ui.splash

import android.animation.ValueAnimator
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private const val Credits = "Made by Nitir, Arsh, GPT-5.6 Terra and Gemini 3.8 Flash"

@Composable
fun StartupSplashScreen(modifier: Modifier = Modifier) {
    val motion = remember { ValueAnimator.areAnimatorsEnabled() }
    val titleScale = remember { Animatable(if (motion) 0.92f else 1f) }
    val capsuleEntrance = remember { Animatable(if (motion) 0f else 1f) }
    var seconds by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var capsuleOrigin by remember { mutableStateOf(Offset.Zero) }
    val backgroundBlur = remember { blurEffect(70f) }
    val capsuleBlur = remember { blurEffect(32f) }
    LaunchedEffect(motion) {
        if (motion) {
            launch { titleScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 280f)) }
            launch { capsuleEntrance.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 280f)) }
            val start = withFrameNanos { it }
            while (true) withFrameNanos { seconds = (it - start) / 1_000_000_000f }
        }
    }
    Box(
        modifier.fillMaxSize().background(Color(0xFF0D0D0E))
            .onGloballyPositioned {
                rootOrigin = it.positionInRoot()
                canvasSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
            }
    ) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = backgroundBlur }) {
            drawAurora(size, seconds)
        }
        Column(
            Modifier.align(Alignment.Center).safeDrawingPadding().padding(horizontal = 24.dp)
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Resonance",
                modifier = Modifier.graphicsLayer { scaleX = titleScale.value; scaleY = titleScale.value },
                style = TextStyle(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    brush = Brush.linearGradient(
                        listOf(Color.White, Color(0xFFA9CEFF), Color(0xFFFFBED2), Color.White),
                        start = Offset((seconds % 2.2f / 2.2f * 1_200f) - 600f, 0f),
                        end = Offset((seconds % 2.2f / 2.2f * 1_200f), 180f),
                        tileMode = TileMode.Mirror
                    )
                )
            )
            Spacer(Modifier.height(28.dp))
            val shape = RoundedCornerShape(26.dp)
            Box(
                Modifier.fillMaxWidth().graphicsLayer {
                    alpha = capsuleEntrance.value.coerceIn(0f, 1f)
                    translationY = (1f - capsuleEntrance.value) * 24.dp.toPx()
                }.shadow(20.dp, shape, ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.16f))
                    .clip(shape)
                    .onGloballyPositioned { capsuleOrigin = it.positionInRoot() - rootOrigin }
            ) {
                // Replay the exact backdrop coordinates into an isolated blurred layer.
                // Only this layer is blurred; the caption and credits remain sharp.
                Canvas(Modifier.matchParentSize().graphicsLayer { renderEffect = capsuleBlur }) {
                    drawAurora(canvasSize, seconds, capsuleOrigin)
                }
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha =
                    if (Build.VERSION.SDK_INT >= 31) 0.42f else 0.78f)))
                Box(Modifier.matchParentSize().border(0.75.dp, Brush.linearGradient(
                    0f to Color.White.copy(alpha = 0.45f),
                    0.4f to Color.White.copy(alpha = 0.10f),
                    1f to Color.Transparent
                ), shape))
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CRAFTED WITH PRECISION", color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(Credits, color = Color.White.copy(alpha = 0.95f), fontSize = 13.sp,
                        lineHeight = 20.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun blurEffect(radiusPixels: Float): androidx.compose.ui.graphics.RenderEffect? =
    if (Build.VERSION.SDK_INT >= 31) {
        RenderEffect.createBlurEffect(radiusPixels, radiusPixels, Shader.TileMode.CLAMP).asComposeRenderEffect()
    } else null

private fun DrawScope.drawAurora(viewport: Size, seconds: Float, origin: Offset = Offset.Zero) {
    if (viewport.minDimension <= 0f) return
    val colors = listOf(Color(0xFF007AFF), Color(0xFFFF2D55), Color(0xFF5856D6))
    colors.forEachIndexed { index, color ->
        val angle = seconds * (0.35f + index * 0.08f) + index * 2.094f
        val center = Offset(viewport.width * (0.5f + 0.28f * cos(angle)),
            viewport.height * (0.5f + 0.22f * sin(angle))) - origin
        val radius = viewport.maxDimension * 0.55f
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.55f), Color.Transparent),
            center = center, radius = radius), radius = radius, center = center)
    }
}
