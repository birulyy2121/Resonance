package com.resonance.recorder.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

data class RecordingResult(
    val file: File,
    val durationMs: Long,
    val waveformPoints: List<Float>,
    val analysisSamples: ShortArray,
    val rmsWindows: FloatArray,
    val rmsWindowDurationSeconds: Double
)

class AudioRecorderManager(
    context: Context,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AutoCloseable {
    companion object {
        const val SAMPLE_RATE = 44_100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val WAV_HEADER_BYTES = 44
        private const val ANALYSIS_SAMPLE_COUNT = 16_384
    }

    private val appContext = context.applicationContext
    private val recordingsDir = File(appContext.filesDir, "recordings").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val lifecycleMutex = Mutex()
    private val _liveRmsFlow = MutableStateFlow(0f)
    val liveRmsFlow: StateFlow<Float> = _liveRmsFlow.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private var session: Session? = null

    private data class Session(
        val startedAt: Long,
        val pcmFile: File,
        val wavFile: File,
        val envelope: MutableList<Float> = mutableListOf(),
        val rmsWindows: MutableList<Float> = mutableListOf(),
        val analysisRing: ArrayDeque<Short> = ArrayDeque(ANALYSIS_SAMPLE_COUNT),
        var totalSamples: Long = 0,
        var rmsFrameSumSquares: Double = 0.0,
        var rmsFrameSamples: Int = 0
    )

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        appContext,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun startRecording(): Boolean = lifecycleMutex.withLock {
        if (recordingJob?.isActive == true || !hasPermission()) return@withLock false

        val minBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBytes <= 0) return@withLock false
        val bufferBytes = max(minBytes, 4_096) * 2
        val recorder = createRecorder(bufferBytes) ?: return@withLock false
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return@withLock false
        }

        val stem = "resonance_${System.currentTimeMillis()}"
        val current = Session(
            startedAt = SystemClock.elapsedRealtime(),
            pcmFile = File(recordingsDir, "$stem.pcm"),
            wavFile = File(recordingsDir, "$stem.wav")
        )
        audioRecord = recorder
        session = current
        recorder.startRecording()
        recordingJob = scope.launch { capture(recorder, current, minOf(bufferBytes / 2, 1_024)) }
        true
    }

    private suspend fun capture(recorder: AudioRecord, current: Session, bufferSamples: Int) {
        val buffer = ShortArray(bufferSamples)
        BufferedOutputStream(FileOutputStream(current.pcmFile)).use { output ->
            val byteBuffer = ByteBuffer.allocate(buffer.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            while (scope.isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read <= 0) continue

                var sumSquares = 0.0
                var peak = 0
                byteBuffer.clear()
                repeat(read) { index ->
                    val sample = buffer[index]
                    byteBuffer.putShort(sample)
                    val magnitude = kotlin.math.abs(sample.toInt())
                    peak = max(peak, magnitude)
                    sumSquares += sample.toDouble() * sample.toDouble()
                    current.rmsFrameSumSquares += sample.toDouble() * sample.toDouble()
                    current.rmsFrameSamples += 1
                    if (current.rmsFrameSamples == SAMPLE_RATE / 10) {
                        current.rmsWindows += (
                            sqrt(current.rmsFrameSumSquares / current.rmsFrameSamples) / Short.MAX_VALUE
                        ).toFloat().coerceIn(0f, 1f)
                        current.rmsFrameSumSquares = 0.0
                        current.rmsFrameSamples = 0
                    }
                    if (current.analysisRing.size == ANALYSIS_SAMPLE_COUNT) {
                        current.analysisRing.removeFirst()
                    }
                    current.analysisRing.addLast(sample)
                }
                output.write(byteBuffer.array(), 0, read * 2)
                val rms = (sqrt(sumSquares / read) / Short.MAX_VALUE).toFloat().coerceIn(0f, 1f)
                _liveRmsFlow.value = rms
                current.envelope += (peak.toFloat() / Short.MAX_VALUE).coerceIn(0f, 1f)
                current.totalSamples += read
            }
        }
    }

    private fun createRecorder(bufferBytes: Int): AudioRecord? {
        val sources = intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
        for (source in sources) {
            val candidate = runCatching {
                AudioRecord(source, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferBytes)
            }.getOrNull() ?: continue
            if (candidate.state == AudioRecord.STATE_INITIALIZED) return candidate
            candidate.release()
        }
        return null
    }

    suspend fun stopRecording(cancel: Boolean = false): RecordingResult? = lifecycleMutex.withLock {
        val current = session ?: return@withLock null
        val recorder = audioRecord
        runCatching { recorder?.stop() }
        recordingJob?.join()
        recorder?.release()
        audioRecord = null
        recordingJob = null
        session = null
        _liveRmsFlow.value = 0f

        if (cancel || current.totalSamples == 0L) {
            current.pcmFile.delete()
            current.wavFile.delete()
            return@withLock null
        }

        wrapPcmAsWav(current.pcmFile, current.wavFile, current.totalSamples)
        current.pcmFile.delete()
        RecordingResult(
            file = current.wavFile,
            durationMs = current.totalSamples * 1_000L / SAMPLE_RATE,
            waveformPoints = resampleEnvelope(current.envelope, 64),
            analysisSamples = ShortArray(current.analysisRing.size).also { target ->
                current.analysisRing.forEachIndexed { index, sample -> target[index] = sample }
            },
            rmsWindows = current.rmsWindows.toFloatArray(),
            rmsWindowDurationSeconds = 0.1
        )
    }

    private fun wrapPcmAsWav(pcm: File, wav: File, totalSamples: Long) {
        val dataBytes = totalSamples * 2L
        require(dataBytes <= UInt.MAX_VALUE.toLong()) { "Recording exceeds WAV size limit" }
        BufferedOutputStream(FileOutputStream(wav)).use { output ->
            val header = ByteBuffer.allocate(WAV_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt((36L + dataBytes).toInt())
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(1)
            header.putInt(SAMPLE_RATE)
            header.putInt(SAMPLE_RATE * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes.toInt())
            output.write(header.array())
            BufferedInputStream(FileInputStream(pcm)).use { input -> input.copyTo(output) }
        }
    }

    private fun resampleEnvelope(values: List<Float>, targetSize: Int): List<Float> {
        if (values.isEmpty()) return List(targetSize) { 0f }
        val stride = values.size.toDouble() / targetSize
        return List(targetSize) { index ->
            val from = (index * stride).toInt().coerceAtMost(values.lastIndex)
            val to = ceil((index + 1) * stride).toInt().coerceIn(from + 1, values.size)
            values.subList(from, to).maxOrNull() ?: 0f
        }
    }

    override fun close() {
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
        session?.pcmFile?.delete()
        session = null
        scope.coroutineContext[Job]?.cancel()
    }
}
