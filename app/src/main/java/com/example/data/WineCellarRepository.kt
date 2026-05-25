package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class WineCellarRepository(private val dao: WineCellarDao) {

    val allBottles: Flow<List<WineBottle>> = dao.getAllBottles()

    val allTemperatureRecords: Flow<List<TemperatureRecord>> = dao.getAllTemperatureRecords()

    suspend fun insertBottle(bottle: WineBottle) {
        dao.insertBottle(bottle)
    }

    suspend fun replaceAllBottles(bottles: List<WineBottle>) {
        dao.clearAllBottles()
        dao.insertBottles(bottles)
    }

    suspend fun getBottlesOnce(): List<WineBottle> {
        return dao.getBottlesOnce()
    }

    suspend fun insertFromSync(
        row: Int,
        col: Int,
        winery: String,
        classification: String?,
        varietal: String,
        vintage: String,
        timestamp: Long,
        photoUri: String?,
        price: Double?,
        isAging: Boolean
    ) {
        val existing = dao.getBottleAt(row, col)
        if (existing == null) {
            // No bottle at coordinate: insert as new
            dao.insertBottle(
                WineBottle(
                    wineryName = winery,
                    classification = classification,
                    varietal = varietal,
                    vintage = vintage,
                    gridRow = row,
                    gridCol = col,
                    timestamp = timestamp,
                    photoUri = photoUri,
                    price = price,
                    isAging = isAging
                )
            )
        } else if (timestamp > existing.timestamp) {
            // Incoming is newer: overwrite/update
            dao.insertBottle(
                existing.copy(
                    wineryName = winery,
                    classification = classification,
                    varietal = varietal,
                    vintage = vintage,
                    timestamp = timestamp,
                    photoUri = photoUri,
                    price = price,
                    isAging = isAging
                )
            )
        }
    }

    suspend fun pushAllToFirebase(db: com.google.firebase.firestore.FirebaseFirestore, cellarId: String) {
        val localBottles = dao.getBottlesOnce()
        val batch = db.batch()
        for (bottle in localBottles) {
            val docId = "slot_${bottle.gridRow}_${bottle.gridCol}"
            val docRef = db.collection("cellars").document(cellarId).collection("bottles").document(docId)
            val wineMap = hashMapOf(
                "gridRow" to bottle.gridRow,
                "gridCol" to bottle.gridCol,
                "wineryName" to bottle.wineryName,
                "classification" to bottle.classification,
                "varietal" to bottle.varietal,
                "vintage" to bottle.vintage,
                "timestamp" to bottle.timestamp,
                "photoUri" to bottle.photoUri,
                "price" to bottle.price,
                "isAging" to bottle.isAging
            )
            batch.set(docRef, wineMap, com.google.firebase.firestore.SetOptions.merge())
        }
        try {
            batch.commit().await()
        } catch (e: Exception) {
            android.util.Log.e("RepositorySync", "Bootstrap upload error: ${e.message}", e)
        }
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
