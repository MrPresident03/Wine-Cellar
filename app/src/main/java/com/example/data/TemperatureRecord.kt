package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "temperature_records")
data class TemperatureRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val temperature: Float,
    val humidity: Float
)
