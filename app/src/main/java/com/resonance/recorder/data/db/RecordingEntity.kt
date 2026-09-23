package com.resonance.recorder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val durationMs: Long,
    val filePath: String,
    val detectedBPM: Int?,
    val detectedKey: String?,
    val centsDeviation: Int?,
    val waveformPoints: List<Float>,
    val transcriptSnippet: String
)

class FloatListConverter {
    @TypeConverter
    fun fromList(values: List<Float>): String = values.joinToString(",")

    @TypeConverter
    fun toList(value: String): List<Float> =
        if (value.isBlank()) emptyList()
        else value.split(',').mapNotNull(String::toFloatOrNull)
}
