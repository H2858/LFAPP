package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Creates an ultra-premium glassmorphism surface with translucent frosted fill
 * and luminous reflective borders.
 */
fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    backgroundColor: Color = Color(0x14FFFFFF),
    borderWidth: Dp = 1.dp,
    borderColor: Color = Color(0x33FFFFFF)
): Modifier = this
    .clip(shape)
    .background(
        brush = Brush.verticalGradient(
            colors = listOf(
                backgroundColor.copy(alpha = (backgroundColor.alpha * 1.3f).coerceAtMost(1f)),
                backgroundColor.copy(alpha = (backgroundColor.alpha * 0.6f))
            )
        )
    )
    .border(
        width = borderWidth,
        brush = Brush.verticalGradient(
            colors = listOf(
                borderColor.copy(alpha = 0.55f),
                borderColor.copy(alpha = 0.12f),
                Color(0x08FFFFFF)
            )
        ),
        shape = shape
    )

/**
 * Animated light reflection traveling along glass edge
 */
@Composable
fun Modifier.glassShimmerBorder(
    shape: Shape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
    enabled: Boolean = true
): Modifier {
    if (!enabled) {
        return this.glassSurface(shape = shape)
    }

    val transition = rememberInfiniteTransition(label = "glassShimmer")
    val shimmerOffset by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerProgress"
    )

    return this
        .clip(shape)
        .background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0x18FFFFFF),
                    Color(0x0CFFFFFF)
                )
            )
        )
        .border(
            width = borderWidth,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x22FFFFFF),
                    Color(0x8038BDF8),
                    Color(0xFFE2E8F0),
                    Color(0x8038BDF8),
                    Color(0x15FFFFFF)
                ),
                start = androidx.compose.ui.geometry.Offset(shimmerOffset * 500f, 0f),
                end = androidx.compose.ui.geometry.Offset((shimmerOffset + 1f) * 500f, 600f)
            ),
            shape = shape
        )
}
