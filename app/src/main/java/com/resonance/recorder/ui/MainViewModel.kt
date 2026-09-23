package com.resonance.recorder.ui

import android.Manifest
import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.resonance.recorder.audio.AudioPlayerManager
import com.resonance.recorder.audio.AudioRecorderManager
import com.resonance.recorder.audio.HarmonicAnalyzer
import com.resonance.recorder.data.db.AppDatabase
import com.resonance.recorder.data.db.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).recordingDao()
    private val recorder = AudioRecorderManager(application)
    private val player = AudioPlayerManager()

    val recordings: StateFlow<List<RecordingEntity>> = dao.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val liveRmsFlow = recorder.liveRmsFlow
    val isPlaying = player.isPlaying
    val playbackPosition = player.currentPosition
    val playbackDuration = player.duration

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()
    private val _selectedRecordingId = MutableStateFlow<Long?>(null)
    val selectedRecordingId: StateFlow<Long?> = _selectedRecordingId.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var timerJob: Job? = null

    fun hasMicrophonePermission(): Boolean = recorder.hasPermission()

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording() {
        if (_isRecording.value) return
        player.pause()
        _selectedRecordingId.value = null
        viewModelScope.launch {
            if (!recorder.startRecording()) {
                _errorMessage.value = "The microphone could not be started."
                return@launch
            }
            _isRecording.value = true
            val started = android.os.SystemClock.elapsedRealtime()
            timerJob?.cancel()
            timerJob = launch {
                while (true) {
                    _elapsedMs.value = android.os.SystemClock.elapsedRealtime() - started
                    delay(100L)
                }
            }
        }
    }

    fun finishRecording() = finalizeRecording(cancel = false)

    fun cancelRecording() = finalizeRecording(cancel = true)

    private fun finalizeRecording(cancel: Boolean) {
        if (!_isRecording.value) return
        _isRecording.value = false
        timerJob?.cancel()
        timerJob = null
        viewModelScope.launch {
            val result = recorder.stopRecording(cancel)
            _elapsedMs.value = 0L
            if (result == null) return@launch

            val pitch = withContext(Dispatchers.Default) {
                HarmonicAnalyzer.detectPitch(result.analysisSamples)
            }
            val bpm = withContext(Dispatchers.Default) {
                HarmonicAnalyzer.estimateTempo(
                    result.rmsWindows,
                    result.rmsWindowDurationSeconds
                )
            }
            val createdAt = System.currentTimeMillis()
            val title = "Field ${SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(createdAt))}"
            val id = dao.insert(
                RecordingEntity(
                    title = title,
                    createdAt = createdAt,
                    durationMs = result.durationMs,
                    filePath = result.file.absolutePath,
                    detectedBPM = bpm,
                    detectedKey = pitch?.noteName,
                    centsDeviation = pitch?.centsDeviation,
                    waveformPoints = result.waveformPoints,
                    transcriptSnippet = localAnalysisSnippet(pitch?.noteName, pitch?.centsDeviation, bpm)
                )
            )
            _selectedRecordingId.value = id
            player.load(result.file)
        }
    }

    fun selectRecording(recording: RecordingEntity) {
        if (_selectedRecordingId.value == recording.id) return
        player.pause()
        if (player.load(File(recording.filePath))) {
            _selectedRecordingId.value = recording.id
        } else {
            _errorMessage.value = "That recording is no longer available."
        }
    }

    fun togglePlayback(recording: RecordingEntity) {
        if (_selectedRecordingId.value != recording.id) selectRecording(recording)
        player.toggle(File(recording.filePath))
    }

    fun seekPlayback(positionMs: Long) = player.seekTo(positionMs)

    fun dismissPlayback() {
        player.pause()
        _selectedRecordingId.value = null
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch {
            if (_selectedRecordingId.value == recording.id) dismissPlayback()
            dao.delete(recording)
            withContext(Dispatchers.IO) { File(recording.filePath).delete() }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun localAnalysisSnippet(key: String?, cents: Int?, bpm: Int?): String = buildList {
        key?.let { add("Detected $it${cents?.let { value -> " ${if (value > 0) "+" else ""}$value¢" } ?: ""}") }
        bpm?.let { add("estimated $it BPM") }
    }.joinToString(" · ")

    override fun onCleared() {
        recorder.close()
        player.close()
        super.onCleared()
    }
}
