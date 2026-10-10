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

    // Concurrency guard to prevent race conditions during prepareAsync
    private var isBusyPlayingOrPreparing = false

    private var audioFocusRequest: AudioFocusRequest? = null

    // Tracks last played file per category to avoid immediate repetition
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
                    try {
                        mediaPlayer?.setVolume(0.2f, 0.2f)
                    } catch (_: Exception) {}
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                synchronized(playerLock) {
                    try {
                        mediaPlayer?.setVolume(1.0f, 1.0f)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Plays a clip from the specified category synchronously/fire-and-forget.
     * Selects randomly among available files, avoiding immediate repetition when possible.
     */
    fun playCategory(category: AudioCategory): AudioPlayResult {
        val availableClips = AudioCatalog.getAvailableClips(context, category)
        if (availableClips.isEmpty()) {
            Log.d(tag, "No voice clips found for category: ${category.displayName} (${category.folderPath})")
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

    /**
     * Suspending playback method that safely awaits asynchronous prepareAsync() and playback completion
     * or preparation/playback failure. Ideal for background/alarm executions where wake locks must be held.
     */
    suspend fun playCategorySuspending(category: AudioCategory): AudioPlayResult {
        val availableClips = AudioCatalog.getAvailableClips(context, category)
        if (availableClips.isEmpty()) {
            Log.d(tag, "No voice clips found for category: ${category.displayName} (${category.folderPath})")
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

    /**
     * Plays a specific asset file with audio focus and single-playback concurrency.
     * Safely closes AssetFileDescriptor immediately after setting data source.
     */
    fun playAssetFile(assetPath: String, category: AudioCategory): AudioPlayResult {
        synchronized(playerLock) {
            val isCurrentlyPlaying = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
            if (isBusyPlayingOrPreparing || isCurrentlyPlaying) {
                Log.d(tag, "Playback busy, another clip is currently active: ${_currentPlayingClip.value}")
                return AudioPlayResult.Busy(_currentPlayingClip.value ?: "unknown")
            }

            // Request Audio Focus
            val focusGranted = requestAudioFocus()
            if (!focusGranted) {
                Log.w(tag, "Audio focus request rejected by system")
                return AudioPlayResult.Error("Audio focus rejected by system")
            }

            // Mark engine as busy immediately to prevent race conditions during asynchronous preparation
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

                // Safely open and close AssetFileDescriptor using scoped .use block
                context.assets.openFd(assetPath).use { afd ->
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
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
                            Log.e(tag, "Error starting MediaPlayer on prepare: ${e.message}")
                            stopPlayback()
                        }
                    }
                }

                player.setOnCompletionListener {
                    stopPlayback()
                }

                player.setOnErrorListener { _, what, extra ->
                    Log.e(tag, "MediaPlayer error: what=$what, extra=$extra for $assetPath")
                    stopPlayback()
                    true
                }

                mediaPlayer = player
                player.prepareAsync()
                return AudioPlayResult.Success(assetPath, category)
            } catch (e: IOException) {
                Log.e(tag, "Failed to open or decode asset: $assetPath", e)
                cleanupFailedAttempt()
                return AudioPlayResult.Error("IO error reading $assetPath: ${e.message}")
            } catch (e: Exception) {
                Log.e(tag, "Unexpected error initializing playback for: $assetPath", e)
                cleanupFailedAttempt()
                return AudioPlayResult.Error("Playback initialization failed: ${e.message}")
            }
        }
    }

    /**
     * Suspending version of playAssetFile that safely bridges prepareAsync(), onCompletion,
     * and onError listeners to a coroutine continuation.
     */
    suspend fun playAssetFileSuspending(assetPath: String, category: AudioCategory): AudioPlayResult =
        suspendCancellableCoroutine { continuation ->
            val hasResumed = java.util.concurrent.atomic.AtomicBoolean(false)

            fun safeResume(result: AudioPlayResult) {
                if (hasResumed.compareAndSet(false, true)) {
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }

            synchronized(playerLock) {
                val isCurrentlyPlaying = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
                if (isBusyPlayingOrPreparing || isCurrentlyPlaying) {
                    Log.d(tag, "Playback busy, another clip is currently active: ${_currentPlayingClip.value}")
                    safeResume(AudioPlayResult.Busy(_currentPlayingClip.value ?: "unknown"))
                    return@suspendCancellableCoroutine
                }

                val focusGranted = requestAudioFocus()
                if (!focusGranted) {
                    Log.w(tag, "Audio focus request rejected by system")
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

                    context.assets.openFd(assetPath).use { afd ->
                        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
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
                                Log.e(tag, "Error starting MediaPlayer on prepare: ${e.message}")
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
                        Log.e(tag, "MediaPlayer error during async prepare or playback: what=$what, extra=$extra for $assetPath")
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

            continuation.invokeOnCancellation {
                stopPlayback()
            }
        }

    private fun cleanupFailedAttempt() {
        releasePlayerInternal()
        abandonAudioFocus()
        isBusyPlayingOrPreparing = false
        _isPlaying.value = false
        _currentPlayingClip.value = null
    }

    /**
     * Safely and idempotently stops playback and releases all resources.
     */
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
            // Null listeners first so no asynchronous callbacks fire during teardown
            try {
                player.setOnPreparedListener(null)
                player.setOnCompletionListener(null)
                player.setOnErrorListener(null)
            } catch (_: Exception) {}

            try {
                val isPlaying = try { player.isPlaying } catch (_: Exception) { false }
                if (isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {}

            try {
                player.reset()
            } catch (_: Exception) {}

            try {
                player.release()
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(tag, "Error releasing MediaPlayer: ${e.message}")
        }
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
        } catch (e: Exception) {
            Log.w(tag, "Failed to request audio focus: ${e.message}")
            false
        }
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
