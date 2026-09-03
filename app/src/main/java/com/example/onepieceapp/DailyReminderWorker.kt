package com.example.onepieceapp

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Tourne une fois par jour (voir [NotificationScheduler]) : si le joueur n'a
 * pas encore gagné le Quotidien d'AU MOINS UN des univers aujourd'hui, on lui
 * envoie un rappel (un seul rappel générique, pas un par univers). On se
 * base sur un flag local (SharedPreferences) plutôt que sur Firestore pour que
 * ça marche même hors-ligne et sans dépendre du ViewModel/de l'auth.
 */
class DailyReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(NotificationHelper.PREFS_NAME, Context.MODE_PRIVATE)
        val today = DailyRepository.todayKey()

        val missedAtLeastOne = Universe.entries.any { universe ->
            prefs.getString(NotificationHelper.keyLastDailyWin(universe), null) != today
        }

        if (missedAtLeastOne) {
            NotificationHelper.showDailyReminder(applicationContext)
        }

        return Result.success()
    }
}
