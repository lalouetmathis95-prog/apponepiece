package com.example.onepieceapp

import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Choisit et fait persister le personnage du jour dans Firestore, pour que tous
 * les joueurs (quelle que soit leur langue ou leur appareil) tombent exactement
 * sur le même personnage — comme un Wordle. La date est calculée en UTC pour que
 * le changement de personnage se fasse au même instant pour tout le monde,
 * indépendamment du fuseau horaire de chacun.
 */
object DailyRepository {

    private val UTC_DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun todayKey(): String = UTC_DAY_FORMAT.format(Date())

    fun yesterdayKey(): String = UTC_DAY_FORMAT.format(Date(System.currentTimeMillis() - 86_400_000L))

    /** Index déterministe et stable (même résultat sur tout appareil) dans une liste de taille [poolSize]. */
    private fun deterministicIndex(dateKey: String, poolSize: Int): Int {
        if (poolSize <= 0) return 0
        return abs(dateKey.hashCode()) % poolSize
    }

    /**
     * Renvoie l'index (dans la liste "pool" du mode Quotidien, indépendant de la langue)
     * du personnage du jour, en le créant dans Firestore s'il n'existe pas encore.
     */
    suspend fun fetchOrCreateDailyIndex(dateKey: String, poolSize: Int): Int {
        val docRef = FirebaseServices.firestore.collection(Collections.DAILY_CHALLENGES).document(dateKey)
        return FirebaseServices.firestore.runTransaction { txn ->
            val snap = txn.get(docRef)
            if (snap.exists()) {
                (snap.getLong("characterIndex") ?: 0L).toInt()
            } else {
                val idx = deterministicIndex(dateKey, poolSize)
                txn.set(
                    docRef,
                    mapOf(
                        "characterIndex" to idx,
                        "solvedCount" to 0L,
                        "guessesSum" to 0L,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                )
                idx
            }
        }.await()
    }

    data class DailyAggregate(val solvedCount: Int, val averageGuesses: Double)

    suspend fun fetchAggregate(dateKey: String): DailyAggregate {
        val snap = FirebaseServices.firestore.collection(Collections.DAILY_CHALLENGES).document(dateKey).get().await()
        val solved = (snap.getLong("solvedCount") ?: 0L).toInt()
        val sum = (snap.getLong("guessesSum") ?: 0L).toInt()
        val avg = if (solved == 0) 0.0 else sum.toDouble() / solved
        return DailyAggregate(solved, avg)
    }

    /**
     * Enregistre la victoire d'un joueur pour le quotidien du jour [dateKey], une seule
     * fois par joueur (idempotent via le sous-document players/{uid}).
     */
    suspend fun recordDailyWin(uid: String, dateKey: String, guessCount: Int): Boolean {
        val dayRef = FirebaseServices.firestore.collection(Collections.DAILY_CHALLENGES).document(dateKey)
        val playerRef = dayRef.collection(Collections.PLAYERS).document(uid)

        return FirebaseServices.firestore.runTransaction { txn ->
            val playerSnap = txn.get(playerRef)
            if (playerSnap.exists()) {
                false
            } else {
                txn.set(playerRef, mapOf("guessCount" to guessCount, "wonAt" to com.google.firebase.Timestamp.now()))
                txn.set(
                    dayRef,
                    mapOf(
                        "solvedCount" to FieldValue.increment(1),
                        "guessesSum" to FieldValue.increment(guessCount.toLong())
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                true
            }
        }.await()
    }
}
