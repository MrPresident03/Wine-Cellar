package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WineCellarDao {

    // Bottles queries
    @Query("SELECT * FROM wine_bottles ORDER BY varietal ASC, vintage DESC")
    fun getAllBottles(): Flow<List<WineBottle>>

    @Query("SELECT * FROM wine_bottles WHERE gridRow = :row AND gridCol = :col LIMIT 1")
    suspend fun getBottleAt(row: Int, col: Int): WineBottle?

    @Query("SELECT * FROM wine_bottles")
    suspend fun getBottlesOnce(): List<WineBottle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBottle(bottle: WineBottle)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBottles(bottles: List<WineBottle>)

    @Query("DELETE FROM wine_bottles")
    suspend fun clearAllBottles()

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
