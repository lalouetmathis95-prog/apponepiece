package com.example.onepieceapp

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/**
 * Lecture et mise à jour des statistiques d'un joueur. Le pseudo reste sur le
 * document users/{uid} (identité partagée, utilisée pour les amis quel que
 * soit l'univers joué) ; les statistiques de jeu (parties, série, essais...)
 * vivent séparément par univers dans users/{uid}/universeStats/{univers},
 * pour que jouer à League of Legends ne mélange pas son classement/streak
 * avec celui de One Piece.
 */
object StatsRepository {

    private fun userDoc(uid: String) = FirebaseServices.firestore.collection(Collections.USERS).document(uid)

    private fun universeDoc(uid: String, universe: Universe) =
        userDoc(uid).collection(Collections.UNIVERSE_STATS).document(universe.name)

    /** Pseudo + avatar, les deux champs "identité" partagés entre univers,
     * lus en un seul accès au document users/{uid} (source de vérité, jamais
     * une copie en cache -- voir FriendsRepository). */
    data class Profile(
        val username: String = "",
        val avatarUniverse: Universe? = null,
        val avatarImageFolder: String? = null,
        val avatarImageFile: String? = null,
        val avatarName: String? = null
    )

    suspend fun loadProfile(uid: String): Profile = runCatching {
        val snap = userDoc(uid).get().await()
        Profile(
            username = snap.getString("username") ?: "",
            avatarUniverse = snap.getString("avatarUniverse")?.let { raw ->
                runCatching { Universe.valueOf(raw) }.getOrNull()
            },
            avatarImageFolder = snap.getString("avatarImageFolder"),
            avatarImageFile = snap.getString("avatarImageFile"),
            avatarName = snap.getString("avatarName")
        )
    }.getOrDefault(Profile())

    suspend fun loadUsername(uid: String): String = loadProfile(uid).username

    /** Enregistre l'avatar choisi par le joueur (un personnage/champion du jeu
     * disposant d'une image, quel que soit l'univers en cours -- voir
     * GameViewModel.setAvatar) sur son document partagé users/{uid}. */
    suspend fun setAvatar(uid: String, universe: Universe, entry: Guessable) {
        val imageFile = entry.imageFile ?: return
        userDoc(uid).set(
            mapOf(
                "avatarUniverse" to universe.name,
                "avatarImageFolder" to entry.imageFolder,
                "avatarImageFile" to imageFile,
                "avatarName" to entry.name
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    private fun toStats(snap: DocumentSnapshot, profile: Profile): UserStats {
        if (!snap.exists()) {
            return UserStats(
                username = profile.username,
                avatarUniverse = profile.avatarUniverse,
                avatarImageFolder = profile.avatarImageFolder,
                avatarImageFile = profile.avatarImageFile,
                avatarName = profile.avatarName
            )
        }
        @Suppress("UNCHECKED_CAST")
        val modeWinsRaw = snap.get("modeWins") as? Map<String, Any?> ?: emptyMap()
        val modeWins = modeWinsRaw.mapValues { (_, v) -> (v as? Number)?.toLong() ?: 0L }
        return UserStats(
            username = profile.username,
            avatarUniverse = profile.avatarUniverse,
            avatarImageFolder = profile.avatarImageFolder,
            avatarImageFile = profile.avatarImageFile,
            avatarName = profile.avatarName,
            gamesPlayed = (snap.getLong("gamesPlayed") ?: 0L).toInt(),
            gamesWon = (snap.getLong("gamesWon") ?: 0L).toInt(),
            currentStreak = (snap.getLong("currentStreak") ?: 0L).toInt(),
            maxStreak = (snap.getLong("maxStreak") ?: 0L).toInt(),
            lastWonDailyDate = snap.getString("lastWonDailyDate"),
            totalGuessesSum = (snap.getLong("totalGuessesSum") ?: 0L).toInt(),
            modeWins = modeWins
        )
    }

    suspend fun loadStats(uid: String, universe: Universe): UserStats {
        val profile = loadProfile(uid)
        val snap = universeDoc(uid, universe).get().await()
        return toStats(snap, profile)
    }

    /** Comme [loadStats], mais sans dépendre du profil (pseudo/avatar) : les
     * deux se chargent depuis des documents différents (users/{uid} vs
     * users/{uid}/universeStats/{univers}), et ne doivent donc pas échouer
     * "en bloc" -- voir GameViewModel.refreshStats, qui charge les deux
     * indépendamment pour qu'un échec transitoire de l'un (ex. juste après un
     * démarrage à froid de l'appli, le jeton d'authentification peut mettre un
     * instant à être prêt côté Firestore) n'efface pas ce que l'autre a réussi
     * à charger, notamment l'avatar. */
    suspend fun loadUniverseStats(uid: String, universe: Universe): UserStats {
        val snap = universeDoc(uid, universe).get().await()
        return toStats(snap, Profile())
    }

    /** Appelé à chaque lancement d'une partie (mode choisi), avant de connaître le résultat. */
    suspend fun recordGameStart(uid: String, universe: Universe) {
        universeDoc(uid, universe).set(
            mapOf("gamesPlayed" to FieldValue.increment(1)),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }

    /**
     * Appelé quand le joueur trouve le personnage/champion. Met à jour le nombre
     * de victoires, la moyenne d'essais, le décompte par mode, et — uniquement
     * pour le mode Quotidien — la série de jours consécutifs (streak), le tout
     * dans les statistiques de l'univers [universe] en cours.
     */
    suspend fun recordGameWin(
        uid: String,
        universe: Universe,
        mode: GameMode,
        guessCount: Int,
        todayKey: String,
        yesterdayKey: String
    ) {
        val ref = universeDoc(uid, universe)
        FirebaseServices.firestore.runTransaction { txn ->
            val snap = txn.get(ref)
            val current = toStats(snap, Profile())

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
