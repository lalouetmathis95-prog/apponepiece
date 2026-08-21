package com.example.onepieceapp

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class UsernameTakenException : Exception("Ce pseudo est déjà pris")

/** Authentification email / mot de passe + création du profil Firestore associé. */
object AuthRepository {

    val currentUser: FirebaseUser? get() = FirebaseServices.auth.currentUser

    suspend fun isUsernameAvailable(username: String): Boolean {
        val key = normalizeUsername(username)
        if (key.isBlank()) return false
        val snap = FirebaseServices.firestore.collection(Collections.USERNAMES).document(key).get().await()
        return !snap.exists()
    }

    suspend fun signUp(email: String, password: String, username: String): FirebaseUser {
        val usernameKey = normalizeUsername(username)
        if (usernameKey.isBlank()) error("Pseudo invalide")

        val result = FirebaseServices.auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Création du compte impossible")

        val usernameRef = FirebaseServices.firestore.collection(Collections.USERNAMES).document(usernameKey)
        val userRef = FirebaseServices.firestore.collection(Collections.USERS).document(user.uid)

        try {
            FirebaseServices.firestore.runTransaction { txn ->
                val existing = txn.get(usernameRef)
                if (existing.exists()) throw UsernameTakenException()

                txn.set(usernameRef, mapOf("uid" to user.uid))
                txn.set(
                    userRef,
                    mapOf(
                        "email" to email,
                        "username" to username.trim(),
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "gamesPlayed" to 0L,
                        "gamesWon" to 0L,
                        "currentStreak" to 0L,
                        "maxStreak" to 0L,
                        "lastWonDailyDate" to null,
                        "totalGuessesSum" to 0L,
                        "modeWins" to emptyMap<String, Long>()
                    ),
                    SetOptions.merge()
                )
                null
            }.await()
        } catch (e: Exception) {
            // Le pseudo a été pris entre-temps (ou une autre erreur Firestore) : on
            // supprime le compte Auth fraîchement créé pour ne pas laisser un compte
            // orphelin sans profil, et on laisse l'utilisateur réessayer.
            runCatching { user.delete() }
            throw e
        }

        return user
    }

    /** Permet à un compte déjà existant (créé avant l'ajout des pseudos) d'en choisir un. */
    suspend fun claimUsername(uid: String, username: String) {
        val usernameKey = normalizeUsername(username)
        if (usernameKey.isBlank()) error("Pseudo invalide")

        val usernameRef = FirebaseServices.firestore.collection(Collections.USERNAMES).document(usernameKey)
        val userRef = FirebaseServices.firestore.collection(Collections.USERS).document(uid)

        FirebaseServices.firestore.runTransaction { txn ->
            val existing = txn.get(usernameRef)
            if (existing.exists()) throw UsernameTakenException()

            txn.set(usernameRef, mapOf("uid" to uid))
            txn.set(userRef, mapOf("username" to username.trim()), SetOptions.merge())
            null
        }.await()
    }

    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = FirebaseServices.auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("Connexion impossible")
    }

    fun signOut() {
        FirebaseServices.auth.signOut()
    }
}
