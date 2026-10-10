package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

sealed interface AudioPlayResult {
    data class Success(val assetPath: String, val category: AudioCategory) : AudioPlayResult
    data class MissingFiles(val category: AudioCategory, val expectedPaths: List<String>) : AudioPlayResult
    data class Busy(val currentlyPlayingPath: String) : AudioPlayResult
    data object VoiceDisabled : AudioPlayResult
    data class Error(val message: String) : AudioPlayResult
}

class AudioEngine(private val context: Context) {
    private val tag = "LivingPhone_Audio"

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private val playerLock = Any()

    private var isBusyPlayingOrPreparing = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private val lastPlayedMap = ConcurrentHashMap<String, String>()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPlayingClip = MutableStateFlow<String?>(null)
    val currentPlayingClip: StateFlow<String?> = _currentPlayingClip.asStateFlow()

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                stopPlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                synchronized(playerLock) {
                    try { mediaPlayer?.setVolume(0.2f, 0.2f) } catch (_: Exception) {}
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                synchronized(playerLock) {
                    try { mediaPlayer?.setVolume(1.0f, 1.0f) } catch (_: Exception) {}
                }
            }
        }
    }

    fun playCategory(category: AudioCategory): AudioPlayResult {
        val availableClips = AudioCatalog.getAvailableClips(context, category)
        if (availableClips.isEmpty()) {
            return AudioPlayResult.MissingFiles(category, category.fullAssetPaths)
        }
        val lastPlayed = lastPlayedMap[category.id]
        val candidatePool = if (availableClips.size > 1 && lastPlayed != null) {
            availableClips.filter { it != lastPlayed }.ifEmpty { availableClips }
        } else {
            availableClips
        }
        val selectedPath = candidatePool.random()
        lastPlayedMap[category.id] = selectedPath
        return playAssetFile(selectedPath, category)
    }

    suspend fun playCategorySuspending(category: AudioCategory): AudioPlayResult {
        val availableClips = AudioCatalog.getAvailableClips(context, category)
        if (availableClips.isEmpty()) {
            return AudioPlayResult.MissingFiles(category, category.fullAssetPaths)
        }
        val lastPlayed = lastPlayedMap[category.id]
        val candidatePool = if (availableClips.size > 1 && lastPlayed != null) {
            availableClips.filter { it != lastPlayed }.ifEmpty { availableClips }
        } else {
            availableClips
        }
        val selectedPath = candidatePool.random()
        lastPlayedMap[category.id] = selectedPath
        return playAssetFileSuspending(selectedPath, category)
    }

    fun playAssetFile(assetPath: String, category: AudioCategory): AudioPlayResult {
        synchronized(playerLock) {
            val isCurrentlyPlaying = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
            if (isBusyPlayingOrPreparing || isCurrentlyPlaying) {
                return AudioPlayResult.Busy(_currentPlayingClip.value ?: "unknown")
            }
            if (!requestAudioFocus()) {
                return AudioPlayResult.Error("Audio focus rejected by system")
            }
            isBusyPlayingOrPreparing = true
            _currentPlayingClip.value = assetPath

            try {
                releasePlayerInternal()
                val player = MediaPlayer()
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build()
                )

                if (assetPath.startsWith("/")) {
                    player.setDataSource(assetPath)
                } else {
                    context.assets.openFd(assetPath).use { afd ->
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                }

                player.setOnPreparedListener { mp ->
                    synchronized(playerLock) {
                        if (mediaPlayer !== mp) {
                            try { mp.release() } catch (_: Exception) {}
                            return@synchronized
                        }
                        try {
                            mp.start()
                            _isPlaying.value = true
                        } catch (e: Exception) {
                            stopPlayback()
                        }
                    }
                }

                player.setOnCompletionListener { stopPlayback() }
                player.setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    true
                }

                mediaPlayer = player
                player.prepareAsync()
                return AudioPlayResult.Success(assetPath, category)
            } catch (e: Exception) {
                cleanupFailedAttempt()
                return AudioPlayResult.Error("Playback initialization failed: ${e.message}")
            }
        }
    }

    suspend fun playAssetFileSuspending(assetPath: String, category: AudioCategory): AudioPlayResult =
        suspendCancellableCoroutine { continuation ->
            val hasResumed = java.util.concurrent.atomic.AtomicBoolean(false)
            fun safeResume(result: AudioPlayResult) {
                if (hasResumed.compareAndSet(false, true)) {
                    if (continuation.isActive) continuation.resume(result)
                }
            }

            synchronized(playerLock) {
                val isCurrentlyPlaying = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
                if (isBusyPlayingOrPreparing || isCurrentlyPlaying) {
                    safeResume(AudioPlayResult.Busy(_currentPlayingClip.value ?: "unknown"))
                    return@suspendCancellableCoroutine
                }
                if (!requestAudioFocus()) {
                    safeResume(AudioPlayResult.Error("Audio focus rejected by system"))
                    return@suspendCancellableCoroutine
                }
                isBusyPlayingOrPreparing = true
                _currentPlayingClip.value = assetPath

                try {
                    releasePlayerInternal()
                    val player = MediaPlayer()
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                    )

                    if (assetPath.startsWith("/")) {
                        player.setDataSource(assetPath)
                    } else {
                        context.assets.openFd(assetPath).use { afd ->
                            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                    }

                    player.setOnPreparedListener { mp ->
                        synchronized(playerLock) {
                            if (mediaPlayer !== mp) {
                                try { mp.release() } catch (_: Exception) {}
                                return@synchronized
                            }
                            try {
                                mp.start()
                                _isPlaying.value = true
                            } catch (e: Exception) {
                                stopPlayback()
                                safeResume(AudioPlayResult.Error("Failed starting MediaPlayer: ${e.message}"))
                            }
                        }
                    }

                    player.setOnCompletionListener {
                        stopPlayback()
                        safeResume(AudioPlayResult.Success(assetPath, category))
                    }

                    player.setOnErrorListener { _, what, extra ->
                        stopPlayback()
                        safeResume(AudioPlayResult.Error("MediaPlayer error: what=$what, extra=$extra"))
                        true
                    }

                    mediaPlayer = player
                    player.prepareAsync()
                } catch (e: Exception) {
                    cleanupFailedAttempt()
                    safeResume(AudioPlayResult.Error("Playback initialization failed: ${e.message}"))
                }
            }

            continuation.invokeOnCancellation { stopPlayback() }
        }

    private fun cleanupFailedAttempt() {
        releasePlayerInternal()
        abandonAudioFocus()
        isBusyPlayingOrPreparing = false
        _isPlaying.value = false
        _currentPlayingClip.value = null
    }

    fun stopPlayback() {
        synchronized(playerLock) {
            releasePlayerInternal()
            abandonAudioFocus()
            isBusyPlayingOrPreparing = false
            _isPlaying.value = false
            _currentPlayingClip.value = null
        }
    }

    private fun releasePlayerInternal() {
        val player = mediaPlayer ?: return
        mediaPlayer = null
        try {
            try { player.setOnPreparedListener(null); player.setOnCompletionListener(null); player.setOnErrorListener(null) } catch (_: Exception) {}
            try { if (player.isPlaying) player.stop() } catch (_: Exception) {}
            try { player.reset() } catch (_: Exception) {}
            try { player.release() } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusChangeListener)
                    .build()
                audioFocusRequest = req
                audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusChangeListener,
                    AudioManager.STREAM_NOTIFICATION,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (_: Exception) { false }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(focusChangeListener)
            }
        } catch (_: Exception) {}
    }
}
