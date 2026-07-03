package com.neww.tunebridge.core.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.neww.tunebridge.core.models.TrackModel
import com.neww.tunebridge.core.services.YouTubeScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

import com.neww.tunebridge.core.db.LocalLibraryRepository
import com.neww.tunebridge.core.db.SettingsRepository
import com.neww.tunebridge.core.services.DownloadRepository

class PlayerController(
    private val context: Context,
    private val youtubeScraper: YouTubeScraper,
    private val libraryRepository: LocalLibraryRepository,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository
) {
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<TrackModel?>(null)
    val currentTrack: StateFlow<TrackModel?> = _currentTrack.asStateFlow()
    
    private var _currentQueue = emptyList<TrackModel>()
    private var _currentIndex = -1

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            
            // Sync initial state
            mediaController?.let {
                _repeatMode.value = it.repeatMode
                _shuffleModeEnabled.value = it.shuffleModeEnabled
            }
            
            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startProgressPolling()
                    } else {
                        stopProgressPolling()
                        mediaController?.volume = 1f
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                    } else if (playbackState == Player.STATE_ENDED) {
                        playNextInQueue()
                    }
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    _repeatMode.value = repeatMode
                }

                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    _shuffleModeEnabled.value = shuffleModeEnabled
                }
            })
        }, MoreExecutors.directExecutor())
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = coroutineScope.launch {
            while (true) {
                mediaController?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        val dur = player.duration
                        _currentPosition.value = pos.coerceAtLeast(0L)
                        if (dur > 0) {
                            _duration.value = dur
                            
                            val fadeInSecs = settingsRepository.fadeInFlow.first()
                            val fadeOutSecs = settingsRepository.fadeOutFlow.first()
                            
                            var targetVol = 1.0f
                            if (fadeInSecs > 0 && pos < fadeInSecs * 1000) {
                                targetVol = pos.toFloat() / (fadeInSecs * 1000)
                            } else if (fadeOutSecs > 0 && dur - pos < fadeOutSecs * 1000) {
                                targetVol = (dur - pos).toFloat() / (fadeOutSecs * 1000)
                            }
                            
                            if (targetVol < 0f) targetVol = 0f
                            if (targetVol > 1f) targetVol = 1f
                            player.volume = targetVol
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
    }
    
    fun playQueue(queue: List<TrackModel>, startIndex: Int = 0) {
        if (queue.isEmpty()) return
        _currentQueue = queue
        _currentIndex = startIndex
        playInternal(queue[startIndex])
    }

    fun playTrack(track: TrackModel) {
        _currentQueue = listOf(track)
        _currentIndex = 0
        playInternal(track)
    }
    
    private fun playNextInQueue() {
        if (_currentQueue.isEmpty()) return
        _currentIndex++
        if (_currentIndex >= _currentQueue.size) {
            _currentIndex = 0
        }
        playInternal(_currentQueue[_currentIndex])
    }
    
    private fun playPreviousInQueue() {
        if (_currentQueue.isEmpty()) return
        _currentIndex--
        if (_currentIndex < 0) {
            _currentIndex = _currentQueue.size - 1
        }
        playInternal(_currentQueue[_currentIndex])
    }

    private fun playInternal(track: TrackModel) {
        _currentTrack.value = track
        _currentPosition.value = 0L
        checkIfLiked(track.id)
        
        coroutineScope.launch {
            libraryRepository.saveToHistory(track)
            
            val downloadedPath = downloadRepository.getDownloadedFilePath(track.id)
            val streamUrl = if (downloadedPath != null) {
                downloadedPath
            } else {
                val videoId = track.youtubeVideoId ?: youtubeScraper.searchVideoId(track.title, track.artist) ?: return@launch
                youtubeScraper.getStreamUrl(videoId) ?: return@launch
            }

            val mediaItem = MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(track.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(android.net.Uri.parse(track.albumArtUrl ?: ""))
                        .build()
                )
                .build()

            mediaController?.setMediaItem(mediaItem)
            mediaController?.prepare()
            mediaController?.play()
        }
    }

    fun togglePlayPause() {
        if (mediaController?.isPlaying == true) {
            mediaController?.pause()
        } else {
            mediaController?.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }

    fun skipToNext() {
        if (_currentQueue.size > 1) {
            playNextInQueue()
        } else {
            mediaController?.seekToNext()
        }
    }

    fun skipToPrevious() {
        if (_currentQueue.size > 1) {
            playPreviousInQueue()
        } else {
            mediaController?.seekToPrevious()
        }
    }

    fun toggleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = nextMode
    }

    fun toggleShuffleMode() {
        val enabled = !_shuffleModeEnabled.value
        mediaController?.shuffleModeEnabled = enabled
    }

    private fun checkIfLiked(trackId: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val songs = libraryRepository.getLikedSongs().first()
            _isLiked.value = songs.any { it.id == trackId }
        }
    }

    fun toggleLike() {
        val track = _currentTrack.value ?: return
        
        // Optimistic UI update
        val wasLiked = _isLiked.value
        _isLiked.value = !wasLiked
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (wasLiked) {
                    libraryRepository.removeLikedSong(track.id)
                } else {
                    libraryRepository.saveLikedSong(track)
                }
            } catch (e: Exception) {
                // Revert if failed
                _isLiked.value = wasLiked
            }
        }
    }

    private var sleepTimerJob: Job? = null
    val sleepTimerRemaining = MutableStateFlow<Long?>(null)

    fun startSleepTimer(minutes: Long) {
        sleepTimerJob?.cancel()
        sleepTimerJob = coroutineScope.launch {
            val endTime = System.currentTimeMillis() + (minutes * 60 * 1000)
            while (System.currentTimeMillis() < endTime) {
                sleepTimerRemaining.value = endTime - System.currentTimeMillis()
                delay(1000)
            }
            sleepTimerRemaining.value = null
            mediaController?.pause()
        }
    }
    
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerRemaining.value = null
    }

    fun release() {
        stopProgressPolling()
        cancelSleepTimer()
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
