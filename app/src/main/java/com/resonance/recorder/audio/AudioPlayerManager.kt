package com.resonance.recorder.audio

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager(
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var player: MediaPlayer? = null
    private var loadedPath: String? = null
    private var ticker: Job? = null

    fun load(file: File): Boolean {
        if (!file.isFile) return false
        releasePlayer()
        return runCatching {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    _duration.value = it.duration.toLong()
                    _currentPosition.value = 0L
                }
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPosition.value = _duration.value
                    ticker?.cancel()
                }
                prepare()
            }
            loadedPath = file.absolutePath
            true
        }.getOrElse {
            releasePlayer()
            false
        }
    }

    fun toggle(file: File) {
        if (loadedPath != file.absolutePath && !load(file)) return
        val active = player ?: return
        if (active.isPlaying) pause() else play()
    }

    fun play() {
        val active = player ?: return
        if (_currentPosition.value >= _duration.value && _duration.value > 0L) active.seekTo(0)
        active.start()
        _isPlaying.value = true
        startTicker()
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        _isPlaying.value = false
        ticker?.cancel()
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0L, _duration.value)
        player?.seekTo(target.toInt())
        _currentPosition.value = target
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && player?.isPlaying == true) {
                _currentPosition.value = player?.currentPosition?.toLong() ?: 0L
                delay(33L)
            }
        }
    }

    private fun releasePlayer() {
        ticker?.cancel()
        player?.release()
        player = null
        loadedPath = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    override fun close() {
        releasePlayer()
        scope.coroutineContext[Job]?.cancel()
    }
}
