package com.resonance.recorder.audio

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class PitchResult(
    val frequencyHz: Double,
    val noteName: String,
    val centsDeviation: Int
)

object HarmonicAnalyzer {
    private const val MIN_FREQUENCY = 60.0
    private const val MAX_FREQUENCY = 1_000.0
    private val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun detectPitch(samples: ShortArray, sampleRate: Int = AudioRecorderManager.SAMPLE_RATE): PitchResult? {
        if (samples.size < sampleRate / MIN_FREQUENCY * 3) return null
        val size = minOf(samples.size, 8_192)
        val offset = samples.size - size
        val mean = (offset until samples.size).sumOf { samples[it].toDouble() } / size
        val signal = DoubleArray(size) { samples[offset + it] - mean }
        val energy = signal.sumOf { it * it }
        if (energy / size < 20_000.0) return null

        val minLag = (sampleRate / MAX_FREQUENCY).toInt().coerceAtLeast(1)
        val maxLag = (sampleRate / MIN_FREQUENCY).toInt().coerceAtMost(size / 2)
        val correlations = DoubleArray(maxLag + 1)
        var strongest = 0.0
        for (lag in minLag..maxLag) {
            var numerator = 0.0
            var leftEnergy = 0.0
            var rightEnergy = 0.0
            for (index in 0 until size - lag) {
                val left = signal[index]
                val right = signal[index + lag]
                numerator += left * right
                leftEnergy += left * left
                rightEnergy += right * right
            }
            val denominator = sqrt(leftEnergy * rightEnergy)
            correlations[lag] = if (denominator > 0.0) numerator / denominator else 0.0
            strongest = maxOf(strongest, correlations[lag])
        }
        if (strongest < 0.45) return null

        val threshold = strongest * 0.90
        var bestLag = (minLag..maxLag).maxByOrNull { correlations[it] } ?: return null
        for (lag in minLag + 1 until maxLag) {
            if (correlations[lag] >= threshold &&
                correlations[lag] >= correlations[lag - 1] &&
                correlations[lag] > correlations[lag + 1]
            ) {
                bestLag = lag
                break
            }
        }

        val previous = correlations[(bestLag - 1).coerceAtLeast(minLag)]
        val center = correlations[bestLag]
        val next = correlations[(bestLag + 1).coerceAtMost(maxLag)]
        val denominator = previous - (2.0 * center) + next
        val refinedLag = if (denominator != 0.0) {
            bestLag + 0.5 * (previous - next) / denominator
        } else bestLag.toDouble()
        return frequencyToNote(sampleRate / refinedLag)
    }

    fun frequencyToNote(frequencyHz: Double): PitchResult? {
        if (!frequencyHz.isFinite() || frequencyHz <= 0.0) return null
        val exactMidi = 12.0 * (ln(frequencyHz / 440.0) / ln(2.0)) + 69.0
        val midi = exactMidi.roundToInt()
        val targetFrequency = 440.0 * 2.0.pow((midi - 69) / 12.0)
        val cents = (1_200.0 * (ln(frequencyHz / targetFrequency) / ln(2.0)))
            .roundToInt()
            .coerceIn(-50, 50)
        val octave = (midi / 12) - 1
        val noteIndex = ((midi % 12) + 12) % 12
        return PitchResult(frequencyHz, "${noteNames[noteIndex]}$octave", cents)
    }

    fun estimateTempo(
        rmsWindows: FloatArray,
        windowDurationSeconds: Double
    ): Int? {
        if (rmsWindows.size < 8 || windowDurationSeconds <= 0.0) return null
        val mean = rmsWindows.average()
        val deviation = sqrt(rmsWindows.sumOf { (it - mean).pow(2) } / rmsWindows.size)
        val threshold = mean + deviation * 1.25
        val minimumGap = (0.25 / windowDurationSeconds).roundToInt().coerceAtLeast(1)
        val peaks = mutableListOf<Int>()
        var lastPeak = -minimumGap
        for (index in 1 until rmsWindows.lastIndex) {
            val value = rmsWindows[index]
            if (value > threshold && value >= rmsWindows[index - 1] && value > rmsWindows[index + 1] &&
                index - lastPeak >= minimumGap
            ) {
                peaks += index
                lastPeak = index
            }
        }
        if (peaks.size < 3) return null
        val intervals = peaks.zipWithNext { first, second ->
            (second - first) * windowDurationSeconds
        }.sorted()
        var bpm = 60.0 / intervals[intervals.size / 2]
        while (bpm < 60.0) bpm *= 2.0
        while (bpm > 200.0) bpm /= 2.0
        return bpm.roundToInt().takeIf { it in 40..240 }
    }
}
