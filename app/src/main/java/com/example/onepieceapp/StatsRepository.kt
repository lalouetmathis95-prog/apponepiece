package com.example.onepieceapp

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/** Lecture et mise à jour des statistiques d'un joueur (document users/{uid}). */
object StatsRepository {

    private fun userDoc(uid: String) = FirebaseServices.firestore.collection(Collections.USERS).document(uid)

    private fun toStats(snap: DocumentSnapshot): UserStats {
        if (!snap.exists()) return UserStats()
        @Suppress("UNCHECKED_CAST")
        val modeWinsRaw = snap.get("modeWins") as? Map<String, Any?> ?: emptyMap()
        val modeWins = modeWinsRaw.mapValues { (_, v) -> (v as? Number)?.toLong() ?: 0L }
        return UserStats(
            username = snap.getString("username") ?: "",
            gamesPlayed = (snap.getLong("gamesPlayed") ?: 0L).toInt(),
            gamesWon = (snap.getLong("gamesWon") ?: 0L).toInt(),
            currentStreak = (snap.getLong("currentStreak") ?: 0L).toInt(),
            maxStreak = (snap.getLong("maxStreak") ?: 0L).toInt(),
            lastWonDailyDate = snap.getString("lastWonDailyDate"),
            totalGuessesSum = (snap.getLong("totalGuessesSum") ?: 0L).toInt(),
            modeWins = modeWins
        )
    }

    suspend fun loadStats(uid: String): UserStats {
        val snap = userDoc(uid).get().await()
        return toStats(snap)
    }

    /** Appelé à chaque lancement d'une partie (mode choisi), avant de connaître le résultat. */
    suspend fun recordGameStart(uid: String) {
        userDoc(uid).set(
            mapOf("gamesPlayed" to FieldValue.increment(1)),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    /**
     * Appelé quand le joueur trouve le personnage. Met à jour le nombre de victoires,
     * la moyenne d'essais, le décompte par mode, et — uniquement pour le mode Quotidien —
     * la série de jours consécutifs (streak).
     */
    suspend fun recordGameWin(uid: String, mode: GameMode, guessCount: Int, todayKey: String, yesterdayKey: String) {
        val ref = userDoc(uid)
        FirebaseServices.firestore.runTransaction { txn ->
            val snap = txn.get(ref)
            val current = toStats(snap)

            var newStreak = current.currentStreak
            var newLastWon = current.lastWonDailyDate
            if (mode == GameMode.QUOTIDIEN) {
                newStreak = when (current.lastWonDailyDate) {
                    todayKey -> current.currentStreak // déjà compté aujourd'hui
                    yesterdayKey -> current.currentStreak + 1
                    else -> 1
                }
                newLastWon = todayKey
            }
            val newMax = maxOf(current.maxStreak, newStreak)

            val modeKey = mode.name
            val newModeWins = current.modeWins.toMutableMap()
            newModeWins[modeKey] = (newModeWins[modeKey] ?: 0L) + 1L

            txn.set(
                ref,
                mapOf(
                    "gamesWon" to FieldValue.increment(1),
                    "totalGuessesSum" to FieldValue.increment(guessCount.toLong()),
                    "currentStreak" to newStreak,
                    "maxStreak" to newMax,
                    "lastWonDailyDate" to newLastWon,
                    "modeWins" to newModeWins
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            null
        }.await()
    }
}
