package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WineCellarDao {

    // Bottles queries
    @Query("SELECT * FROM wine_bottles ORDER BY varietal ASC, vintage DESC")
    fun getAllBottles(): Flow<List<WineBottle>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBottle(bottle: WineBottle)

    @Delete
    suspend fun deleteBottle(bottle: WineBottle)

    // Temperature queries
    @Query("SELECT * FROM temperature_records ORDER BY timestamp ASC")
    fun getAllTemperatureRecords(): Flow<List<TemperatureRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemperatureRecord(record: TemperatureRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemperatureRecords(records: List<TemperatureRecord>)

    @Query("DELETE FROM temperature_records")
    suspend fun clearAllTemperatureRecords()
}
