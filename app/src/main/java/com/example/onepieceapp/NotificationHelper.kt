package com.example.onepieceapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Rappel quotidien pour inciter à jouer le mode Quotidien : une notification
 * locale (pas de serveur push), programmée par [NotificationScheduler] et
 * déclenchée par [DailyReminderWorker] uniquement si le joueur n'a pas encore
 * gagné le quotidien du jour (voir [GameViewModel.markDailyPlayedLocally]).
 */
object NotificationHelper {
    const val CHANNEL_ID = "daily_reminder"
    const val PREFS_NAME = "onepiecedle_prefs"
    private const val NOTIFICATION_ID = 1001

    /** Clé de préférence "dernier quotidien gagné", séparée par univers (le
     * streak local du Quotidien One Piece est indépendant de celui de League
     * of Legends -- voir [GameViewModel.markDailyPlayedLocally]). */
    fun keyLastDailyWin(universe: Universe): String = "last_daily_win_date_${universe.name}"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rappel quotidien",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Rappel pour jouer le mode Quotidien d'InfiniteDle"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun showDailyReminder(context: Context) {
        // Depuis Android 13 (TIRAMISU), POST_NOTIFICATIONS est une permission à
        // l'exécution : si le joueur l'a refusée, on n'affiche simplement rien.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Le personnage/champion du jour t'attend 🎯")
            .setContentText("Tu n'as pas encore joué tous tes modes Quotidien aujourd'hui !")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}
