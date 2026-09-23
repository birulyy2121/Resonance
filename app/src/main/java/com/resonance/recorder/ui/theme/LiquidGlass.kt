package com.resonance.recorder.ui.theme

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SpecularBrush = Brush.linearGradient(
    colorStops = arrayOf(
        0.0f to Color.White.copy(alpha = 0.35f),
        0.4f to Color.White.copy(alpha = 0.10f),
        1.0f to Color.Transparent
    )
)

enum class GlassContrast { CLEAR, TINTED }

/**
 * Applies the optical layer of a Liquid Glass surface. For controls, prefer
 * [LiquidGlassPill] or [LiquidGlassCard], which keep foreground content sharp
 * by placing this modifier on a dedicated background layer.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(percent = 50),
    contrast: GlassContrast = GlassContrast.TINTED,
    blurRadius: Dp = if (contrast == GlassContrast.CLEAR) 22.dp else 36.dp,
    tintColor: Color? = null,
    elevation: Dp = 20.dp,
    forceOpaque: Boolean = false
): Modifier = composed {
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !forceOpaque
    val density = LocalDensity.current
    val blurPixels = with(density) { blurRadius.toPx() }
    val blurEffect = if (supportsBlur) remember(blurPixels) {
        RenderEffect.createBlurEffect(blurPixels, blurPixels, Shader.TileMode.CLAMP).asComposeRenderEffect()
    } else null
    val dark = isSystemInDarkTheme()
    val designedTint = tintColor ?: when (contrast) {
        GlassContrast.CLEAR -> if (dark) Color.Black.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.16f)
        GlassContrast.TINTED -> if (dark) Color(0xFF202025).copy(alpha = 0.78f) else Color(0xFFF2F1F4).copy(alpha = 0.80f)
    }
    val resolvedTint = if (supportsBlur) designedTint else {
        if (dark) Color(0xFF202025).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f)
    }

    this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .graphicsLayer {
            clip = true
            this.shape = shape
            // This layer contains only the optical fill; foreground content is a sibling.
            renderEffect = blurEffect
        }
        .background(resolvedTint, shape)
        .border(width = 0.75.dp, brush = SpecularBrush, shape = shape)
        .clip(shape)
}

@Composable
fun LiquidGlassPill(
    modifier: Modifier = Modifier,
    contrast: GlassContrast = GlassContrast.TINTED,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    forceOpaque: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .liquidGlass(
                    shape = shape,
                    contrast = contrast,
                    elevation = 22.dp,
                    forceOpaque = forceOpaque
                )
        )
        Box(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    outerRadius: Dp = 28.dp,
    contrast: GlassContrast = GlassContrast.TINTED,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    forceOpaque: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(outerRadius)
    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .liquidGlass(
                    shape = shape,
                    contrast = contrast,
                    elevation = 20.dp,
                    forceOpaque = forceOpaque
                )
        )
        Box(Modifier.padding(contentPadding), content = content)
    }
}
