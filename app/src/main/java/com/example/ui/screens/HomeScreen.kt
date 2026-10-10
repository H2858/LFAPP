package com.example.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.LivingSettings
import com.example.service.ReactionController
import com.example.ui.components.GlassEdgeIconButton
import com.example.ui.components.GlassMasterToggle
import com.example.ui.components.GlassStatusPill
import com.example.ui.components.LivingGlassCore
import com.example.ui.components.glassSurface
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.IcyBlueGlow
import com.example.ui.theme.IcyBlueLight
import com.example.ui.theme.IcyBluePrimary
import com.example.ui.theme.SilverBorder
import com.example.ui.theme.SilverLuminous
import com.example.ui.theme.SilverMuted

@Composable
fun HomeScreen(
    settings: LivingSettings,
    onToggleLiving: (Boolean) -> Unit,
    onToggleVoice: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    reactionController: ReactionController
) {
    val context = LocalContext.current
    val isReacting by reactionController.isReacting.collectAsState()
    val latestReaction by reactionController.latestReaction.collectAsState()

    // Real-time battery status in foreground
    var batteryPercent by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        batteryPercent = (level * 100) / scale
                    }
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        stickyIntent?.let {
            val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) batteryPercent = (level * 100) / scale
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDeep)
    ) {
        // Deep Charcoal Ambient Lighting & Subtle Radial Atmosphere
        val ambientAuraColor by animateColorAsState(
            targetValue = if (settings.isLivingPhoneEnabled) Color(0x1838BDF8) else Color(0x06FFFFFF),
            animationSpec = tween(durationMillis = 800),
            label = "ambientAura"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ambientAuraColor,
                            Color(0x0A0F172A),
                            Color.Transparent
                        ),
                        radius = 1100f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding + 12.dp, bottom = bottomPadding + 16.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: Voice toggle + Wordmark + Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Voice Mute / Unmute quick toggle
                GlassEdgeIconButton(
                    icon = if (settings.isVoiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                    contentDescription = if (settings.isVoiceEnabled) "Mute voice reactions" else "Unmute voice reactions",
                    onClick = { onToggleVoice(!settings.isVoiceEnabled) },
                    active = settings.isVoiceEnabled,
                    testTag = "voice_toggle_button"
                )

                // Refined Living Phone Wordmark
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LIVING PHONE",
                        color = SilverLuminous,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 4.5.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "AMBIENT COMPANION",
                        color = SilverMuted.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 2.5.sp
                    )
                }

                // Discreet Settings Button
                GlassEdgeIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Open Settings",
                    onClick = onOpenSettings,
                    active = true,
                    testTag = "settings_button"
                )
            }

            // CENTER SECTION: Status Pill, Living Core, and Reaction feedback
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Elegant Status Indicator
                GlassStatusPill(
                    isLiving = settings.isLivingPhoneEnabled
                )

                // Distinctive Animated Central Glass Element
                LivingGlassCore(
                    isLiving = settings.isLivingPhoneEnabled,
                    isReacting = isReacting,
                    size = 250.dp,
                    onTap = {
                        // Tapping core produces an ambient pulse or awakens
                        if (!settings.isLivingPhoneEnabled) {
                            onToggleLiving(true)
                        }
                    }
                )

                // Subtle Reaction or State Readout
                Box(
                    modifier = Modifier
                        .height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isReacting && latestReaction != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        latestReaction?.let { reaction ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x1838BDF8))
                                    .border(1.dp, Color(0x3538BDF8), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = reaction.category.displayName.uppercase(),
                                    color = IcyBlueGlow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                    }

                    if (!isReacting) {
                        Text(
                            text = if (settings.isLivingPhoneEnabled) {
                                "RHYTHM ACTIVE"
                            } else {
                                "DORMANT"
                            },
                            color = SilverMuted.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }

            // BOTTOM CONTROLS & AMBIENT EDGE NODES
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                // Primary Master Control (Awaken / Sleep)
                GlassMasterToggle(
                    isOn = settings.isLivingPhoneEnabled,
                    onToggle = onToggleLiving
                )

                // Edge Minimal Status Indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Frequency Indicator
                    Row(
                        modifier = Modifier
                            .glassSurface(shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(IcyBlueLight)
                        )
                        Text(
                            text = "${settings.frequency.title.uppercase()} FREQ",
                            color = SilverMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.sp
                        )
                    }

                    // Right: Real-time Battery Ambient node
                    Row(
                        modifier = Modifier
                            .glassSurface(shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isCharging) Icons.Default.Bolt else Icons.Default.BatteryFull,
                            contentDescription = "Battery Status",
                            tint = if (batteryPercent <= 15) Color(0xFFF43F5E) else if (isCharging) IcyBlueGlow else SilverMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "$batteryPercent%",
                            color = SilverMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
