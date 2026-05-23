package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

class WineCellarRepository(private val dao: WineCellarDao) {

    val allBottles: Flow<List<WineBottle>> = dao.getAllBottles()

    val allTemperatureRecords: Flow<List<TemperatureRecord>> = dao.getAllTemperatureRecords()

    suspend fun insertBottle(bottle: WineBottle) {
        dao.insertBottle(bottle)
    }

    suspend fun deleteBottle(bottle: WineBottle) {
        dao.deleteBottle(bottle)
    }

    suspend fun recordClimate(temperature: Float, humidity: Float) {
        val record = TemperatureRecord(
            timestamp = System.currentTimeMillis(),
            temperature = temperature,
            humidity = humidity
        )
        dao.insertTemperatureRecord(record)
    }

    // Simulate syncing from our microcontroller (Arduino Mega / ESP8266)
    suspend fun syncMicrocontroller(targetTemp: Float = 13.0f, baseHumidity: Float = 68.0f): TemperatureRecord {
        // Generates small random noise around comfortable wine vault numbers
        val temperatureNoise = (Random.nextFloat() - 0.5f) * 0.6f
        val humidityNoise = (Random.nextFloat() - 0.5f) * 2.0f

        val finalTemp = targetTemp + temperatureNoise
        val finalHumidity = baseHumidity + humidityNoise

        val newRecord = TemperatureRecord(
            timestamp = System.currentTimeMillis(),
            temperature = finalTemp,
            humidity = finalHumidity
        )
        dao.insertTemperatureRecord(newRecord)
        return newRecord
    }
}
