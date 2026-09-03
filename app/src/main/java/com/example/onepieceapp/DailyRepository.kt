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
 * les joueurs d'un même fuseau horaire (quelle que soit leur langue ou leur
 * appareil) tombent exactement sur le même personnage — comme un Wordle. La
 * date est calculée dans le fuseau horaire local de l'appareil pour que le
 * changement de personnage (et le déverrouillage du quotidien) se fasse
 * effectivement à minuit pour le joueur, plutôt qu'à minuit UTC (ce qui,
 * décalé de 1h ou 2h selon la saison pour un joueur en France, donnait
 * l'impression que le quotidien "ne se réinitialisait pas bien à minuit").
 *
 * Le format de date n'est PAS conservé dans un champ partagé : SimpleDateFormat
 * n'est pas thread-safe, et todayKey()/yesterdayKey() peuvent être appelées
 * depuis plusieurs coroutines en parallèle (démarrage du quotidien, victoire,
 * stats...). Une nouvelle instance est donc créée à chaque appel.
 */
object DailyRepository {

    private fun dayFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

    fun todayKey(): String = dayFormat().format(Date())

    fun yesterdayKey(): String = dayFormat().format(Date(System.currentTimeMillis() - 86_400_000L))

    /** Clé de document Firestore composée univers + date : les défis quotidiens
     * de chaque univers sont totalement indépendants (personnage du jour One
     * Piece ≠ champion du jour League of Legends), même à date identique.
     * Publique pour que GameViewModel puisse construire la même clé lors de sa
     * propre lecture directe du document de la veille. */
    fun dailyDocId(universe: Universe, dateKey: String): String = "${universe.name}_$dateKey"

    /** Index déterministe et stable (même résultat sur tout appareil) dans une liste de taille [poolSize]. */
    private fun deterministicIndex(dateKey: String, poolSize: Int): Int {
        if (poolSize <= 0) return 0
        return abs(dateKey.hashCode()) % poolSize
    }

    /**
     * Renvoie l'index (dans la liste "pool" du mode Quotidien, indépendant de la langue)
     * du personnage du jour, en le créant dans Firestore s'il n'existe pas encore.
     */
    suspend fun fetchOrCreateDailyIndex(universe: Universe, dateKey: String, poolSize: Int): Int {
        val docRef = FirebaseServices.firestore.collection(Collections.DAILY_CHALLENGES).document(dailyDocId(universe, dateKey))
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

    suspend fun fetchAggregate(universe: Universe, dateKey: String): DailyAggregate {
        val snap = FirebaseServices.firestore.collection(Collections.DAILY_CHALLENGES).document(dailyDocId(universe, dateKey)).get().await()
        val solved = (snap.getLong("solvedCount") ?: 0L).toInt()
        val sum = (snap.getLong("guessesSum") ?: 0L).toInt()
        val avg = if (solved == 0) 0.0 else sum.toDouble() / solved
        return DailyAggregate(solved, avg)
    }

    /**
     * Enregistre la victoire d'un joueur pour le quotidien du jour [dateKey], une seule
     * fois par joueur (idempotent via le sous-document players/{uid}).
     */
    suspend fun recordDailyWin(uid: String, universe: Universe, dateKey: String, guessCount: Int): Boolean {
        val dayRef = FirebaseServices.firestore.collection(Collections.DAILY_CHALLENGES).document(dailyDocId(universe, dateKey))
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
