package com.example.onepieceapp

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Programme un rappel quotidien (voir [DailyReminderWorker]) une fois pour
 * toutes à l'installation : WorkManager persiste le planning à travers les
 * redémarrages/mises à jour de l'appli, donc on ne fait qu'enregistrer le
 * travail avec [ExistingPeriodicWorkPolicy.KEEP] pour ne pas le reprogrammer
 * (et donc décaler son horaire) à chaque lancement.
 */
object NotificationScheduler {
    private const val WORK_NAME = "daily_reminder"
    private const val REMINDER_HOUR = 19

    fun schedule(context: Context) {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REMINDER_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        val initialDelayMs = target.timeInMillis - now.timeInMillis

        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
