package com.example.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.IcyBlueGlow
import com.example.ui.theme.IcyBluePrimary
import com.example.ui.theme.SilverBorder
import com.example.ui.theme.SilverLuminous
import com.example.ui.theme.SilverMuted
import com.example.ui.theme.StatusLivingGreen
import com.example.ui.theme.StatusPausedAmber

/**
 * Premium Futuristic Glass Master Switch (ON/OFF)
 */
@Composable
fun GlassMasterToggle(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val thumbOffset by animateDpAsState(
        targetValue = if (isOn) 86.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "thumbOffset"
    )

    val auraColor by animateColorAsState(
        targetValue = if (isOn) IcyBluePrimary else Color(0x3394A3B8),
        animationSpec = tween(durationMillis = 350),
        label = "auraColor"
    )

    val shape = RoundedCornerShape(32.dp)

    Box(
        modifier = modifier
            .width(168.dp)
            .height(56.dp)
            .minimumInteractiveComponentSize()
            .testTag("master_toggle")
            .semantics {
                contentDescription = if (isOn) "Turn off Living Phone" else "Turn on Living Phone"
            }
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0x1AFFFFFF),
                        Color(0x0AFFFFFF)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (isOn) IcyBlueGlow.copy(alpha = 0.6f) else SilverBorder,
                        Color(0x10FFFFFF)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggle(!isOn)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Background text labels inside track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SLEEP",
                color = if (!isOn) SilverLuminous else SilverMuted.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp
            )
            Text(
                text = "AWAKEN",
                color = if (isOn) IcyBlueGlow else SilverMuted.copy(alpha = 0.4f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp
            )
        }

        // Sliding Glass Pill Thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(76.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isOn) IcyBluePrimary.copy(alpha = 0.85f) else Color(0x35FFFFFF),
                            if (isOn) Color(0xFF0284C7).copy(alpha = 0.8f) else Color(0x18FFFFFF)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.8f),
                            if (isOn) IcyBlueGlow.copy(alpha = 0.5f) else Color(0x22FFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            // Small luminous inner dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOn) Color.White else SilverMuted)
            )
        }
    }
}

/**
 * Minimalist Edge Glass Icon Button (for top corners and bottom edges)
 */
@Composable
fun GlassEdgeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "edge_icon_button",
    active: Boolean = true,
    highlightColor: Color = IcyBluePrimary
) {
    val view = LocalView.current
    val shape = CircleShape

    Box(
        modifier = modifier
            .size(48.dp)
            .minimumInteractiveComponentSize()
            .testTag(testTag)
            .clip(shape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x1EFFFFFF),
                        Color(0x0CFFFFFF)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        if (active) highlightColor.copy(alpha = 0.6f) else Color(0x26FFFFFF),
                        Color(0x0AFFFFFF)
                    )
                ),
                shape = shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (active) SilverLuminous else SilverMuted.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Small Elegant Status Indicator Badge (Living / Paused)
 */
@Composable
fun GlassStatusPill(
    isLiving: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isLiving) StatusLivingGreen else StatusPausedAmber
    val text = if (isLiving) "LIVING" else "PAUSED"
    val shape = RoundedCornerShape(20.dp)

    Row(
        modifier = modifier
            .clip(shape)
            .background(Color(0x10FFFFFF))
            .border(
                width = 1.dp,
                color = Color(0x20FFFFFF),
                shape = shape
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Glowing status dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = text,
            color = SilverLuminous,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.5.sp
        )
    }
}
