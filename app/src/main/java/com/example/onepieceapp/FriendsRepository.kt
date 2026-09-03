package com.example.onepieceapp

import kotlinx.coroutines.tasks.await

/** Une demande d'ami reçue, en attente d'acceptation ou de refus. */
data class IncomingRequest(
    val fromUid: String,
    val fromUsername: String
)

/** Un ami confirmé. */
data class FriendInfo(
    val uid: String,
    val username: String,
    val avatarImageFolder: String? = null,
    val avatarImageFile: String? = null
)

/** Une ligne du classement entre amis. */
data class LeaderboardEntry(
    val uid: String,
    val username: String,
    val avatarImageFolder: String? = null,
    val avatarImageFile: String? = null,
    val gamesWon: Int,
    val maxStreak: Int,
    val winRatePercent: Int,
    val isMe: Boolean
)

class UserNotFoundException : Exception("Aucun joueur avec ce pseudo")
class AlreadyFriendsException : Exception("Vous êtes déjà amis")
class RequestToSelfException : Exception("Vous ne pouvez pas vous ajouter vous-même")

/** Gestion des demandes d'amis et du classement, basée sur Cloud Firestore. */
object FriendsRepository {

    private fun usersCol() = FirebaseServices.firestore.collection(Collections.USERS)

    private fun incomingRequestsCol(uid: String) =
        usersCol().document(uid).collection(Collections.INCOMING_REQUESTS)

    private fun friendsCol(uid: String) =
        usersCol().document(uid).collection(Collections.FRIENDS)

    /** Résout un pseudo vers l'uid du joueur correspondant, ou null si introuvable. */
    private suspend fun resolveUsername(username: String): String? {
        val key = normalizeUsername(username)
        if (key.isBlank()) return null
        val snap = FirebaseServices.firestore.collection(Collections.USERNAMES).document(key).get().await()
        if (!snap.exists()) return null
        return snap.getString("uid")
    }

    /** Pseudo actuel d'un joueur, lu directement sur son profil (source de vérité) :
     * on ne se fie jamais à une copie mise en cache dans un autre document, pour
     * éviter d'afficher un pseudo vide/périmé si le profil a changé depuis. */
    private suspend fun currentUsername(uid: String): String =
        StatsRepository.loadUsername(uid).ifBlank { "?" }

    /** Pseudo + avatar actuels d'un joueur (voir currentUsername -- même
     * principe, toujours lu en direct sur son profil). */
    private suspend fun currentProfile(uid: String): StatsRepository.Profile =
        StatsRepository.loadProfile(uid)

    suspend fun sendFriendRequest(myUid: String, myUsername: String, targetUsername: String) {
        val targetUid = resolveUsername(targetUsername) ?: throw UserNotFoundException()
        if (targetUid == myUid) throw RequestToSelfException()

        val alreadyFriend = friendsCol(myUid).document(targetUid).get().await().exists()
        if (alreadyFriend) throw AlreadyFriendsException()

        incomingRequestsCol(targetUid).document(myUid).set(
            mapOf(
                "fromUid" to myUid,
                "fromUsername" to myUsername,
                "createdAt" to com.google.firebase.Timestamp.now()
            )
        ).await()
    }

    suspend fun listIncomingRequests(uid: String): List<IncomingRequest> {
        val snap = incomingRequestsCol(uid).get().await()
        return snap.documents.map { doc ->
            val fromUid = doc.getString("fromUid") ?: doc.id
            // On relit le pseudo actuel de l'expéditeur plutôt que la copie stockée
            // dans la demande, au cas où elle aurait été vide au moment de l'envoi
            // (ex : pseudo pas encore chargé) ou changée depuis.
            IncomingRequest(fromUid = fromUid, fromUsername = currentUsername(fromUid))
        }
    }

    suspend fun acceptRequest(myUid: String, myUsername: String, request: IncomingRequest) {
        val now = com.google.firebase.Timestamp.now()
        friendsCol(myUid).document(request.fromUid).set(mapOf("since" to now)).await()
        friendsCol(request.fromUid).document(myUid).set(mapOf("since" to now)).await()
        incomingRequestsCol(myUid).document(request.fromUid).delete().await()
    }

    suspend fun declineRequest(uid: String, fromUid: String) {
        incomingRequestsCol(uid).document(fromUid).delete().await()
    }

    suspend fun listFriends(uid: String): List<FriendInfo> {
        val snap = friendsCol(uid).get().await()
        return snap.documents.map { doc ->
            // Toujours lu en direct depuis le profil de l'ami (voir currentProfile) :
            // aucune valeur en cache pouvant être vide ou périmée n'est utilisée ici.
            val profile = currentProfile(doc.id)
            FriendInfo(
                uid = doc.id,
                username = profile.username.ifBlank { "?" },
                avatarImageFolder = profile.avatarImageFolder,
                avatarImageFile = profile.avatarImageFile
            )
        }
    }

    suspend fun removeFriend(myUid: String, friendUid: String) {
        friendsCol(myUid).document(friendUid).delete().await()
        friendsCol(friendUid).document(myUid).delete().await()
    }

    /** Classement du joueur et de ses amis pour l'univers [universe] en cours,
     * trié par nombre de victoires -- séparé par univers, comme les stats. */
    suspend fun fetchLeaderboard(myUid: String, universe: Universe): List<LeaderboardEntry> {
        val friends = listFriends(myUid)
        val uids = listOf(myUid) + friends.map { it.uid }
        val entries = uids.mapNotNull { uid ->
            runCatching {
                val stats = StatsRepository.loadStats(uid, universe)
                LeaderboardEntry(
                    uid = uid,
                    username = stats.username.ifBlank { "?" },
                    avatarImageFolder = stats.avatarImageFolder,
                    avatarImageFile = stats.avatarImageFile,
                    gamesWon = stats.gamesWon,
                    maxStreak = stats.maxStreak,
                    winRatePercent = stats.winRatePercent,
                    isMe = uid == myUid
                )
            }.getOrNull()
        }
        return entries.sortedWith(compareByDescending<LeaderboardEntry> { it.gamesWon }.thenByDescending { it.maxStreak })
    }
}
