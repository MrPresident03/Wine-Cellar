package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

@Database(entities = [WineBottle::class, TemperatureRecord::class], version = 4, exportSchema = false)
abstract class WineCellarDatabase : RoomDatabase() {

    abstract fun wineCellarDao(): WineCellarDao

    companion object {
        @Volatile
        private var INSTANCE: WineCellarDatabase? = null

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wine_bottles ADD COLUMN classification TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): WineCellarDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WineCellarDatabase::class.java,
                    "wine_cellar_database"
                )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .addCallback(WineCellarDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class WineCellarDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database.wineCellarDao())
                }
            }
        }

        suspend fun populateDatabase(dao: WineCellarDao) {
            // Populate initial premium wine bottles
            val initialBottles = listOf(
                WineBottle(wineryName = "Château Margaux", varietal = "Cabernet Sauvignon", vintage = "2015", gridRow = 1, gridCol = 1, price = 650.0, isAging = true),
                WineBottle(wineryName = "Domaine de la Romanée-Conti", varietal = "Pinot Noir", vintage = "2018", gridRow = 2, gridCol = 3, price = 2500.0, isAging = true),
                WineBottle(wineryName = "Stag's Leap Wine Cellars", varietal = "Cabernet Sauvignon", vintage = "2019", gridRow = 1, gridCol = 4, price = 120.0, isAging = false),
                WineBottle(wineryName = "Kistler Vineyards", varietal = "Chardonnay", vintage = "2021", gridRow = 4, gridCol = 2, price = 95.0, isAging = false),
                WineBottle(wineryName = "Penfolds Grange", varietal = "Shiraz", vintage = "2016", gridRow = 3, gridCol = 5, price = 85.0, isAging = true),
                WineBottle(wineryName = "Veuve Clicquot", varietal = "Champagne", vintage = "2012", gridRow = 5, gridCol = 1, price = 150.0, isAging = false),
                WineBottle(wineryName = "Cloudy Bay", varietal = "Sauvignon Blanc", vintage = "2023", gridRow = 4, gridCol = 5, price = 35.0, isAging = false)
            )
            for (bottle in initialBottles) {
                dao.insertBottle(bottle)
            }

            // Generate climate history for the last 30 days
            // Day historical range: fluctuations between 12.0°C and 14.5°C, humidity 62% - 72%
            val calendar = Calendar.getInstance()
            val nowTime = calendar.timeInMillis
            val records = mutableListOf<TemperatureRecord>()

            // Generate 1 record per hour for the past 30 days
            // 30 days * 24 hours = 720 records (which is standard and very performant in Room)
            // Let's generate records with a realistic sinusoidal wave representing daily night/day fluctuations
            for (i in 0 until 180) { // Let's do 4-hour intervals for 30 days = 180 records to keep it ultra light and responsive
                calendar.timeInMillis = nowTime
                calendar.add(Calendar.HOUR_OF_DAY, -i * 4)

                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                // Base temp around 13.0°C
                // Adds a daily fluctuation cycling with the hour of the day
                val tempFluctuation = Math.sin(hour * Math.PI / 12).toFloat() * 0.8f
                // Add a slight weekly fluctuation cycling with day
                val weeklyFluctuation = Math.cos(day * Math.PI / 7).toFloat() * 0.4f
                val temperature = 13.2f + tempFluctuation + weeklyFluctuation

                // Humidity inverse correlation with warmth
                val humidityFluctuation = -Math.sin(hour * Math.PI / 12).toFloat() * 3.0f
                val humidity = 68.0f + humidityFluctuation + (weeklyFluctuation * 2.0f)

                records.add(
                    TemperatureRecord(
                        timestamp = calendar.timeInMillis,
                        temperature = temperature,
                        humidity = humidity
                    )
                )
            }

            dao.insertTemperatureRecords(records)
        }
    }
}
