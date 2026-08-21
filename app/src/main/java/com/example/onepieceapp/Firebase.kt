package com.example.onepieceapp

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Points d'accès partagés vers Firebase Auth et Firestore. */
object FirebaseServices {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
}

object Collections {
    const val USERS = "users"
    const val DAILY_CHALLENGES = "dailyChallenges"
    const val PLAYERS = "players"
    const val USERNAMES = "usernames"
    const val FRIENDS = "friends"
    const val INCOMING_REQUESTS = "incomingRequests"
}

/** Normalise un pseudo pour la clé d'unicité (insensible à la casse/accents). */
fun normalizeUsername(username: String): String =
    java.text.Normalizer.normalize(username.trim(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()

/** Statistiques d'un joueur, stockées sur son document users/{uid}. */
data class UserStats(
    val username: String = "",
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val currentStreak: Int = 0,
    val maxStreak: Int = 0,
    val lastWonDailyDate: String? = null,
    val totalGuessesSum: Int = 0,
    val modeWins: Map<String, Long> = emptyMap()
) {
    val winRatePercent: Int
        get() = if (gamesPlayed == 0) 0 else (gamesWon * 100 / gamesPlayed)

    val averageGuesses: Double
        get() = if (gamesWon == 0) 0.0 else totalGuessesSum.toDouble() / gamesWon
}
