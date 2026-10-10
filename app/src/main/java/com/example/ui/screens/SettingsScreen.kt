package com.example.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioCatalog
import com.example.audio.AudioCategory
import com.example.audio.AudioPlayResult
import com.example.audio.CategoryGroup
import com.example.data.LivingSettings
import com.example.data.ReactionFrequency
import com.example.service.ReactionController
import com.example.ui.components.glassShimmerBorder
import com.example.ui.components.glassSurface
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.IcyBlueGlow
import com.example.ui.theme.IcyBlueLight
import com.example.ui.theme.IcyBluePrimary
import com.example.ui.theme.SilverBorder
import com.example.ui.theme.SilverLuminous
import com.example.ui.theme.SilverMuted

@Composable
fun SettingsScreen(
    settings: LivingSettings,
    onUpdateLivingEnabled: (Boolean) -> Unit,
    onUpdateVoiceEnabled: (Boolean) -> Unit,
    onUpdateFrequency: (ReactionFrequency) -> Unit,
    onClose: () -> Unit,
    reactionController: ReactionController
) {
    BackHandler { onClose() }

    val context = LocalContext.current
    var testFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var testFeedbackIsError by remember { mutableStateOf(false) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDeep)
    ) {
        // Ambient glass radial glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x1538BDF8),
                            Color(0x060E1726),
                            Color.Transparent
                        ),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset, bottom = bottomInset)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .glassSurface(shape = CircleShape)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to home",
                        tint = SilverLuminous
                    )
                }

                Text(
                    text = "SETTINGS",
                    color = SilverLuminous,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 3.sp
                )

                // Placeholder for symmetric layout
                Spacer(modifier = Modifier.size(44.dp))
            }

            // Status message toast banner
            AnimatedVisibility(
                visible = testFeedbackMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                testFeedbackMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .glassSurface(
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = if (testFeedbackIsError) Color(0x22F43F5E) else Color(0x220284C7),
                                borderColor = if (testFeedbackIsError) Color(0x66F43F5E) else Color(0x6638BDF8)
                            )
                            .padding(14.dp)
                    ) {
                        Text(
                            text = msg,
                            color = SilverLuminous,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Section 1: Core System Controls
                item {
                    SettingsSectionCard(title = "CORE CONTROLS") {
                        // Master Living Phone Toggle
                        SettingsToggleRow(
                            title = "Living Phone",
                            subtitle = if (settings.isLivingPhoneEnabled) "Active and monitoring ambient rhythm" else "Paused and dormant",
                            checked = settings.isLivingPhoneEnabled,
                            onCheckedChange = onUpdateLivingEnabled,
                            testTag = "setting_toggle_living"
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Voice Reactions Toggle
                        SettingsToggleRow(
                            title = "Voice Reactions",
                            subtitle = if (settings.isVoiceEnabled) "Egyptian Arabic voice clips enabled" else "Muted (silent ambient monitoring)",
                            checked = settings.isVoiceEnabled,
                            onCheckedChange = onUpdateVoiceEnabled,
                            testTag = "setting_toggle_voice"
                        )
                    }
                }

                // Section 2: Reaction Frequency
                item {
                    SettingsSectionCard(title = "REACTION FREQUENCY") {
                        Text(
                            text = "Controls cooldown duration between ambient voice reactions.",
                            color = SilverMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ReactionFrequency.entries.forEach { freq ->
                                val isSelected = settings.frequency == freq
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) Color(0x3038BDF8) else Color(0x10FFFFFF)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) IcyBlueGlow.copy(alpha = 0.7f) else SilverBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { onUpdateFrequency(freq) }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = freq.title.uppercase(),
                                            color = if (isSelected) IcyBlueGlow else SilverLuminous,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = when (freq) {
                                                ReactionFrequency.INSTANT -> "2 sec"
                                                ReactionFrequency.LOW -> "10 min"
                                                ReactionFrequency.NORMAL -> "3 min"
                                                ReactionFrequency.HIGH -> "45 sec"
                                            },
                                            color = SilverMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Audio Verification & Testing
                item {
                    SettingsSectionCard(title = "AUDIO VERIFICATION & TESTING") {
                        val totalInstalled = AudioCatalog.getTotalInstalledClips(context)
                        val totalExpected = AudioCatalog.TOTAL_EXPECTED_CLIPS

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Installed Assets",
                                color = SilverMuted,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "$totalInstalled / $totalExpected files",
                                color = if (totalInstalled > 0) IcyBlueLight else SilverMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (totalInstalled == 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassSurface(
                                        shape = RoundedCornerShape(12.dp),
                                        backgroundColor = Color(0x10FFFFFF),
                                        borderColor = Color(0x20FFFFFF)
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "MP3 files will be uploaded later into app/src/main/assets/audio/. Testing buttons below check for their presence safely without crashing.",
                                    color = SilverMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // List Category Groups for testing
                items(CategoryGroup.entries) { group ->
                    AudioGroupTestCard(
                        group = group,
                        context = context,
                        onTestCategory = { category ->
                            reactionController.testReaction(category) { result ->
                                when (result) {
                                    is AudioPlayResult.Success -> {
                                        testFeedbackIsError = false
                                        val fileName = result.assetPath.substringAfterLast('/')
                                        testFeedbackMessage = "Playing: $fileName"
                                    }
                                    is AudioPlayResult.MissingFiles -> {
                                        testFeedbackIsError = true
                                        testFeedbackMessage = "Clip not found: Upload file to ${result.expectedPaths.firstOrNull() ?: category.folderPath}"
                                    }
                                    is AudioPlayResult.Busy -> {
                                        testFeedbackIsError = true
                                        testFeedbackMessage = "Audio player busy playing another voice clip"
                                    }
                                    is AudioPlayResult.Error -> {
                                        testFeedbackIsError = true
                                        testFeedbackMessage = "Playback error: ${result.message}"
                                    }
                                    AudioPlayResult.VoiceDisabled -> {
                                        testFeedbackIsError = true
                                        testFeedbackMessage = "Voice playback is disabled"
                                    }
                                }
                            }
                        }
                    )
                }

                // Section 4: Privacy & About Section
                item {
                    SettingsSectionCard(title = "PRIVACY & ABOUT") {
                        Text(
                            text = "Zero Telemetry Architecture",
                            color = SilverLuminous,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Living Phone runs 100% on-device. It requires no user account, no cloud servers, and no AI API calls. Ambient triggers are computed locally using standard Android hardware sensors and broadcast receivers.",
                            color = SilverMuted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Voice Library: Egyptian Arabic (Pre-recorded)",
                            color = IcyBlueLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(20.dp),
                backgroundColor = Color(0x12FFFFFF),
                borderColor = Color(0x28FFFFFF)
            )
            .padding(18.dp)
    ) {
        Column {
            Text(
                text = title,
                color = IcyBlueLight.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SilverLuminous,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = SilverMuted,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = CharcoalDeep,
                checkedTrackColor = IcyBluePrimary,
                uncheckedThumbColor = SilverMuted,
                uncheckedTrackColor = Color(0x25FFFFFF)
            )
        )
    }
}

@Composable
fun AudioGroupTestCard(
    group: CategoryGroup,
    context: Context,
    onTestCategory: (AudioCategory) -> Unit
) {
    val installedCount = AudioCatalog.getInstalledCount(context, group)
    val expectedCount = group.totalExpectedClips()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                backgroundColor = Color(0x0EFFFFFF),
                borderColor = Color(0x20FFFFFF)
            )
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        color = SilverLuminous,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = group.description,
                        color = SilverMuted,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x15FFFFFF))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$installedCount/$expectedCount",
                        color = if (installedCount > 0) IcyBlueLight else SilverMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subcategory test action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.categories.forEach { category ->
                    val hasClips = AudioCatalog.getAvailableClips(context, category).isNotEmpty()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (hasClips) Color(0x2038BDF8) else Color(0x10FFFFFF))
                            .border(
                                width = 1.dp,
                                color = if (hasClips) Color(0x4038BDF8) else Color(0x18FFFFFF),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onTestCategory(category) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Test ${category.displayName}",
                                tint = if (hasClips) IcyBlueGlow else SilverMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = category.displayName.take(12),
                                color = if (hasClips) SilverLuminous else SilverMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
