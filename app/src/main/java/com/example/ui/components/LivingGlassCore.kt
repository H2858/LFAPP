package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.IcyBlueGlow
import com.example.ui.theme.IcyBluePrimary
import com.example.ui.theme.SilverLuminous
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LivingGlassCore(
    isLiving: Boolean,
    isReacting: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
    onTap: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val touchPulse = remember { Animatable(0f) }

    // Organic breathing transition
    val transition = rememberInfiniteTransition(label = "livingBreathing")
    val breathingPulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "coreBreath"
    )

    val shimmerRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reflectionSweep"
    )

    // Trigger pulse on reaction
    LaunchedEffect(isReacting) {
        if (isReacting) {
            touchPulse.snapTo(0f)
            touchPulse.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
            )
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = if (isLiving) "Living Phone Core Active" else "Living Phone Core Paused"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button
            ) {
                coroutineScope.launch {
                    touchPulse.snapTo(0f)
                    touchPulse.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
                    )
                }
                onTap()
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (this.size.minDimension / 2f) * 0.82f

            val effectiveScale = if (isLiving) breathingPulse else 0.95f
            val scaledRadius = radius * effectiveScale
            val pulseProgress = touchPulse.value

            // 1. Reactive Expansion Ripple
            if (pulseProgress > 0f && pulseProgress < 1f) {
                val rippleRadius = scaledRadius + (pulseProgress * radius * 0.65f)
                val rippleAlpha = (1f - pulseProgress) * 0.45f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            IcyBlueGlow.copy(alpha = rippleAlpha * 0.7f),
                            Color(0x8038BDF8).copy(alpha = rippleAlpha)
                        ),
                        center = center,
                        radius = rippleRadius
                    ),
                    radius = rippleRadius,
                    center = center,
                    style = Stroke(width = 2.dp.toPx() * (1f - pulseProgress * 0.5f))
                )
            }

            // 2. Ambient Aura Glow behind the glass orb
            val auraColor = if (isLiving) {
                IcyBluePrimary.copy(alpha = 0.22f * effectiveScale)
            } else {
                Color(0x1894A3B8)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor,
                        auraColor.copy(alpha = auraColor.alpha * 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = scaledRadius * 1.35f
                ),
                radius = scaledRadius * 1.35f,
                center = center
            )

            // 3. Frosted Translucent Glass Base Disk
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x351E293B),
                        Color(0x220F172A),
                        Color(0x40020617)
                    ),
                    center = center,
                    radius = scaledRadius
                ),
                radius = scaledRadius,
                center = center
            )

            // 4. Inner Luminous Nucleus (The Living Core)
            val innerCoreRadius = scaledRadius * 0.42f
            val coreColor = if (isLiving) {
                IcyBlueGlow.copy(alpha = 0.85f * (0.8f + 0.2f * sin(breathingPulse * Math.PI.toFloat())))
            } else {
                SilverLuminous.copy(alpha = 0.35f)
            }

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        coreColor,
                        IcyBluePrimary.copy(alpha = if (isLiving) 0.6f else 0.2f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = innerCoreRadius * 1.25f
                ),
                radius = innerCoreRadius * 1.25f,
                center = center
            )

            // 5. Concentric Refraction Rings
            val ringCount = 3
            for (i in 1..ringCount) {
                val ringRadius = innerCoreRadius + (scaledRadius - innerCoreRadius) * (i / (ringCount + 0.5f))
                val ringAlpha = if (isLiving) 0.25f - (i * 0.05f) else 0.12f
                drawCircle(
                    color = SilverLuminous.copy(alpha = ringAlpha),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // 6. Rotating Light Reflection on Glass Perimeter
            rotate(shimmerRotation, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            SilverLuminous.copy(alpha = if (isLiving) 0.75f else 0.35f),
                            IcyBlueGlow.copy(alpha = if (isLiving) 0.85f else 0.3f),
                            Color.Transparent
                        ),
                        center = center
                    ),
                    radius = scaledRadius,
                    center = center,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }

            // 7. Realistic Curvature Specular Highlight (Upper-Left Reflection)
            val highlightCenter = Offset(
                center.x - scaledRadius * 0.38f,
                center.y - scaledRadius * 0.38f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isLiving) 0.55f else 0.25f),
                        Color.White.copy(alpha = if (isLiving) 0.18f else 0.08f),
                        Color.Transparent
                    ),
                    center = highlightCenter,
                    radius = scaledRadius * 0.55f
                ),
                radius = scaledRadius * 0.55f,
                center = highlightCenter
            )

            // 8. Outer Precision Glass Rim
            drawCircle(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SilverLuminous.copy(alpha = 0.65f),
                        IcyBluePrimary.copy(alpha = 0.25f),
                        Color(0x15FFFFFF)
                    ),
                    startY = center.y - scaledRadius,
                    endY = center.y + scaledRadius
                ),
                radius = scaledRadius,
                center = center,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }
}
