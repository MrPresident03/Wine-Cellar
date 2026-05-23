package com.example

import android.app.Application
import com.example.data.WineCellarDatabase
import com.example.data.WineCellarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class WineCellarApplication : Application() {
    // No DI framework needed, we use simple manual dependency injection.
    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { WineCellarDatabase.getDatabase(this, applicationScope) }
    val repository by lazy { WineCellarRepository(database.wineCellarDao()) }
}
