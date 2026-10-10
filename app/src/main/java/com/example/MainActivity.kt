package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.service.LivingPhoneService
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.LivingPhoneTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LivingPhoneTheme {
                RequestPermissionsIfNeeded()

                val settings by viewModel.settings.collectAsState()
                val currentScreen by viewModel.currentScreen.collectAsState()

                // Start or stop background service based on master living switch
                LaunchedEffect(settings.isLivingPhoneEnabled) {
                    if (settings.isLivingPhoneEnabled) {
                        LivingPhoneService.start(this@MainActivity)
                    } else {
                        LivingPhoneService.stop(this@MainActivity)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CharcoalDeep)
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "screenTransition"
                    ) { screen ->
                        when (screen) {
                            AppScreen.HOME -> HomeScreen(
                                settings = settings,
                                onToggleLiving = { viewModel.setLivingEnabled(it) },
                                onToggleVoice = { viewModel.setVoiceEnabled(it) },
                                onOpenSettings = { viewModel.navigateTo(AppScreen.SETTINGS) },
                                reactionController = viewModel.reactionController
                            )
                            AppScreen.SETTINGS -> SettingsScreen(
                                settings = settings,
                                onUpdateLivingEnabled = { viewModel.setLivingEnabled(it) },
                                onUpdateVoiceEnabled = { viewModel.setVoiceEnabled(it) },
                                onUpdateFrequency = { viewModel.setFrequency(it) },
                                onClose = { viewModel.navigateTo(AppScreen.HOME) },
                                reactionController = viewModel.reactionController
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RequestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { _ ->
                // Notification permission result handled
            }

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
