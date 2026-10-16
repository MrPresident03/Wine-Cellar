package com.example.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.data.Prefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Checks the latest sensor reading every ~15 minutes (the shortest interval Android allows for
 * background work) and posts a notification when the cellar goes above the alert temperature.
 * It notifies once per warm spell and re-arms when the temperature drops 0.5°C below the limit.
 */
class ClimateAlertWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(Prefs.ALERTS_ENABLED, false)) return Result.success()
        val user = FirebaseAuth.getInstance().currentUser ?: return Result.success()

        return try {
            val db = FirebaseFirestore.getInstance()
            val cellarId = db.collection("users").document(user.uid).get().await().getString("cellarId")
            if (cellarId.isNullOrBlank()) return Result.success()

            val cellar = db.collection("cellars").document(cellarId).get().await()
            val threshold = cellar.getDouble("alertThreshold") ?: 18.0
            val latest = db.collection("cellars").document(cellarId).collection("climate")
                .orderBy("ts", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
                .documents
                .firstOrNull()

            val temperature = latest?.getDouble("temperature")
            val takenAt = latest?.getTimestamp("ts")?.toDate()?.time
            val alertActive = prefs.getBoolean(Prefs.ALERT_ACTIVE, false)
            val recent = takenAt != null && System.currentTimeMillis() - takenAt < 2 * 60 * 60 * 1000L

            if (temperature != null && recent) {
                if (temperature > threshold && !alertActive) {
                    Notifications.showTemperatureAlert(applicationContext, temperature, threshold)
                    prefs.edit().putBoolean(Prefs.ALERT_ACTIVE, true).apply()
                } else if (temperature <= threshold - 0.5 && alertActive) {
                    prefs.edit().putBoolean(Prefs.ALERT_ACTIVE, false).apply()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "climate-alert-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ClimateAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

object Notifications {
    const val CHANNEL_ID = "climate_alerts"
    private const val ALERT_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cellar temperature alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warns you when the cellar gets warmer than your alert limit"
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun showTemperatureAlert(context: Context, temperature: Double, threshold: Double) {
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Wine cellar is too warm")
            .setContentText(
                String.format(Locale.getDefault(), "%.1f°C, above your %.1f°C alert limit", temperature, threshold)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ALERT_ID, notification)
    }
}
