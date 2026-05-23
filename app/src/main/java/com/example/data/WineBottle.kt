package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wine_bottles")
data class WineBottle(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val wineryName: String,
    val varietal: String,
    val vintage: String,
    val gridRow: Int,
    val gridCol: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val photoUri: String? = null,
    val price: Double? = null,
    val isAging: Boolean = false
)
