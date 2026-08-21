package com.example.onepieceapp

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import kotlin.random.Random

enum class GameMode {
    FACILE, MOYEN, DIFFICILE, QUOTIDIEN, DIEU
}

fun GameMode.label(s: AppStrings): String = when (this) {
    GameMode.FACILE -> s.modeFacile
    GameMode.MOYEN -> s.modeMoyen
    GameMode.DIFFICILE -> s.modeDifficile
    GameMode.QUOTIDIEN -> s.modeQuotidien
    GameMode.DIEU -> s.modeDieu
}

sealed class AuthUiState {
    object Loading : AuthUiState()
    object LoggedOut : AuthUiState()
    data class LoggedIn(val uid: String, val email: String) : AuthUiState()
}

private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase()

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private var allCharacters: List<Character> = emptyList()
    private var dailyPositions: List<Int> = emptyList()
    private var pool: List<Character> = emptyList()

    var isLoading by mutableStateOf(true)
        private set

    // --- Authentification ---------------------------------------------------

    var authState by mutableStateOf<AuthUiState>(AuthUiState.Loading)
        private set

    var authError by mutableStateOf<String?>(null)
        private set

    var authBusy by mutableStateOf(false)
        private set

    var userStats by mutableStateOf(UserStats())
        private set

    // --- Amis -----------------------------------------------------------------

    var incomingRequests by mutableStateOf<List<IncomingRequest>>(emptyList())
        private set

    var friends by mutableStateOf<List<FriendInfo>>(emptyList())
        private set

    var leaderboard by mutableStateOf<List<LeaderboardEntry>>(emptyList())
        private set

    var friendsBusy by mutableStateOf(false)
        private set

    var friendsError by mutableStateOf<String?>(null)
        private set

    private val authListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        authState = if (user == null) {
            AuthUiState.LoggedOut
        } else {
            AuthUiState.LoggedIn(user.uid, user.email ?: "")
        }
        if (user != null) {
            refreshStats(user.uid)
            refreshFriendsData(user.uid)
        } else {
            incomingRequests = emptyList()
            friends = emptyList()
            leaderboard = emptyList()
        }
    }

    init {
        FirebaseServices.auth.addAuthStateListener(authListener)
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = CharacterRepository.load(application)
            allCharacters = loaded
            dailyPositions = dailyPoolPositions(loaded)
            isLoading = false
        }
    }

    override fun onCleared() {
        FirebaseServices.auth.removeAuthStateListener(authListener)
        super.onCleared()
    }

    fun signUp(email: String, password: String, username: String) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            authError = "Email / mot de passe / pseudo requis"
            return
        }
        authBusy = true
        authError = null
        viewModelScope.launch {
            try {
                AuthRepository.signUp(email.trim(), password, username.trim())
            } catch (e: Exception) {
                authError = when (e) {
                    is UsernameTakenException -> e.message
                    else -> e.localizedMessage ?: e.toString()
                }
            } finally {
                authBusy = false
            }
        }
    }

    suspend fun isUsernameAvailable(username: String): Boolean =
        runCatching { AuthRepository.isUsernameAvailable(username) }.getOrDefault(true)

    /** Pour les comptes créés avant l'ajout des pseudos : leur permet d'en choisir un. */
    fun setUsername(username: String, onResult: (Boolean, String?) -> Unit) {
        val uid = (authState as? AuthUiState.LoggedIn)?.uid
        if (uid == null) {
            onResult(false, null)
            return
        }
        if (username.isBlank()) {
            onResult(false, "Pseudo requis")
            return
        }
        viewModelScope.launch {
            try {
                AuthRepository.claimUsername(uid, username.trim())
                userStats = userStats.copy(username = username.trim())
                onResult(true, null)
            } catch (e: Exception) {
                val message = when (e) {
                    is UsernameTakenException -> e.message
                    else -> e.localizedMessage ?: e.toString()
                }
                onResult(false, message)
            }
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            authError = "Email / mot de passe requis"
            return
        }
        authBusy = true
        authError = null
        viewModelScope.launch {
            try {
                AuthRepository.signIn(email.trim(), password)
            } catch (e: Exception) {
                authError = e.localizedMessage ?: e.toString()
            } finally {
                authBusy = false
            }
        }
    }

    fun signOut() {
        AuthRepository.signOut()
        backToModeSelection()
    }

    private fun refreshStats(uid: String) {
        viewModelScope.launch {
            try {
                userStats = StatsRepository.loadStats(uid)
            } catch (_: Exception) {
                // Hors-ligne ou règles Firestore pas encore prêtes : on garde les stats par défaut.
            }
        }
    }

    private fun refreshFriendsData(uid: String) {
        viewModelScope.launch {
            runCatching { incomingRequests = FriendsRepository.listIncomingRequests(uid) }
            runCatching { friends = FriendsRepository.listFriends(uid) }
            runCatching { leaderboard = FriendsRepository.fetchLeaderboard(uid) }
        }
    }

    fun refreshFriends() {
        val uid = (authState as? AuthUiState.LoggedIn)?.uid ?: return
        refreshFriendsData(uid)
    }

    fun sendFriendRequest(targetUsername: String, onResult: (Boolean, String?) -> Unit) {
        val uid = (authState as? AuthUiState.LoggedIn)?.uid ?: return
        val myUsername = userStats.username
        friendsBusy = true
        friendsError = null
        viewModelScope.launch {
            try {
                FriendsRepository.sendFriendRequest(uid, myUsername, targetUsername.trim())
                onResult(true, null)
            } catch (e: Exception) {
                val message = e.message ?: e.toString()
                friendsError = message
                onResult(false, message)
            } finally {
                friendsBusy = false
            }
        }
    }

    fun acceptRequest(request: IncomingRequest) {
        val uid = (authState as? AuthUiState.LoggedIn)?.uid ?: return
        val myUsername = userStats.username
        viewModelScope.launch {
            runCatching { FriendsRepository.acceptRequest(uid, myUsername, request) }
            refreshFriendsData(uid)
        }
    }

    fun declineRequest(request: IncomingRequest) {
        val uid = (authState as? AuthUiState.LoggedIn)?.uid ?: return
        viewModelScope.launch {
            runCatching { FriendsRepository.declineRequest(uid, request.fromUid) }
            refreshFriendsData(uid)
        }
    }

    fun removeFriend(friend: FriendInfo) {
        val uid = (authState as? AuthUiState.LoggedIn)?.uid ?: return
        viewModelScope.launch {
            runCatching { FriendsRepository.removeFriend(uid, friend.uid) }
            refreshFriendsData(uid)
        }
    }

    // --- Langue --------------------------------------------------------------

    var lang by mutableStateOf(Lang.FR)
        private set

    val strings: AppStrings get() = stringsFor(lang)

    /** Change la langue. Si une partie est en cours, elle repart à zéro dans la
     * nouvelle langue (les personnages ne sont pas les mêmes objets d'une langue
     * à l'autre, on ne peut donc pas continuer la partie telle quelle). */
    fun selectLanguage(newLang: Lang) {
        if (newLang == lang) return
        lang = newLang
        currentMode?.let { startMode(it) }
    }

    // --- Partie ---------------------------------------------------------------

    var currentMode by mutableStateOf<GameMode?>(null)
        private set

    var modeStarting by mutableStateOf(false)
        private set

    var target by mutableStateOf<Character?>(null)
        private set

    var previousDailyCharacterName by mutableStateOf<String?>(null)
        private set

    var dailyAggregate by mutableStateOf<DailyRepository.DailyAggregate?>(null)
        private set

    val guesses = mutableStateListOf<GuessRow>()

    var guessCount by mutableStateOf(0)
        private set

    /** Palier d'indice débloqué (1 = aucun, 4 = tout révélé). Chaque palier ne
     * devient débloquable qu'à partir d'un certain nombre d'essais (voir
     * [canAdvanceHint] : 4, 7 puis 10), et le débloquer coûte un malus. */
    var hintStage by mutableStateOf(1)
        private set

    var won by mutableStateOf(false)
        private set

    /** Vrai si le personnage a été révélé via une pub plutôt que trouvé par le joueur. */
    var revealed by mutableStateOf(false)
        private set

    /** Vrai pendant que la pub (simulée) de révélation est en cours de visionnage. */
    var watchingAd by mutableStateOf(false)
        private set

    /** Vrai quand la boîte de dialogue de confirmation "regarder une pub" doit s'afficher. */
    var showAdPrompt by mutableStateOf(false)
        private set

    /** Vrai si le joueur a déjà terminé le quotidien du jour (une seule tentative par jour). */
    var dailyLocked by mutableStateOf(false)
        private set

    var query by mutableStateOf("")

    val suggestions = mutableStateListOf<Character>()

    fun backToModeSelection() {
        currentMode = null
        target = null
        guesses.clear()
        query = ""
        suggestions.clear()
    }

    private fun poolFor(mode: GameMode): List<Character> {
        val base = charactersForLang(allCharacters, lang)
        return when (mode) {
            GameMode.FACILE -> base.filter { it.difficulty >= 6 }
            GameMode.MOYEN -> base.filter { it.difficulty >= 5 }
            GameMode.DIFFICILE -> base.filter { it.difficulty >= 1 }
            GameMode.QUOTIDIEN -> base.filter { it.difficulty >= 3 }
            GameMode.DIEU -> base.filter { it.difficulty <= 2 }
        }
    }

    fun startMode(mode: GameMode) {
        if (allCharacters.isEmpty()) return
        pool = poolFor(mode)
        currentMode = mode
        guesses.clear()
        guessCount = 0
        hintStage = 1
        won = false
        revealed = false
        watchingAd = false
        showAdPrompt = false
        query = ""
        suggestions.clear()
        previousDailyCharacterName = null
        dailyAggregate = null
        target = null

        // Le mode quotidien ne peut être joué qu'une seule fois par jour : si le
        // joueur l'a déjà trouvé aujourd'hui, on affiche l'écran "déjà joué" au
        // lieu de relancer une partie (et on ne recompte pas la partie dans les stats).
        dailyLocked = mode == GameMode.QUOTIDIEN && userStats.lastWonDailyDate == DailyRepository.todayKey()

        if (!dailyLocked) {
            val uid = (authState as? AuthUiState.LoggedIn)?.uid
            if (uid != null) {
                viewModelScope.launch { runCatching { StatsRepository.recordGameStart(uid) } }
            }
        }

        if (mode == GameMode.QUOTIDIEN) {
            startDailyMode()
        } else {
            target = pool[Random.nextInt(pool.size)]
        }
    }

    /** Le personnage du jour est partagé entre tous les joueurs via un document
     * Firestore : tout le monde tombe sur le même, quelle que soit sa langue. */
    private fun startDailyMode() {
        if (dailyPositions.isEmpty()) return
        modeStarting = true
        viewModelScope.launch {
            try {
                val todayKey = DailyRepository.todayKey()
                val yesterdayKey = DailyRepository.yesterdayKey()

                val todayIndex = DailyRepository.fetchOrCreateDailyIndex(todayKey, dailyPositions.size)
                target = characterAtPosition(allCharacters, lang, dailyPositions[todayIndex])

                runCatching {
                    val yesterdaySnap = FirebaseServices.firestore
                        .collection(Collections.DAILY_CHALLENGES).document(yesterdayKey).get().await()
                    val yesterdayIdx = yesterdaySnap.getLong("characterIndex")?.toInt()
                    if (yesterdayIdx != null && yesterdayIdx in dailyPositions.indices) {
                        previousDailyCharacterName =
                            characterAtPosition(allCharacters, lang, dailyPositions[yesterdayIdx]).name
                    }
                }

                runCatching { dailyAggregate = DailyRepository.fetchAggregate(todayKey) }
            } finally {
                modeStarting = false
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        query = newQuery
        val normalizedQuery = normalize(newQuery)
        if (normalizedQuery.isBlank()) {
            suggestions.clear()
            return
        }
        val parts = normalizedQuery.split(" ").filter { it.isNotBlank() }
        val already = guesses.map { it.character.name }.toSet()
        val filtered = pool.filter { c ->
            c.name !in already && run {
                // On cherche à la fois dans le nom et dans le surnom (ex. "Mr 0" pour
                // Crocodile), pour retrouver un personnage même en tapant son surnom.
                val normName = normalize(c.name)
                val normEpithet = normalize(c.epithet)
                parts.all { part -> normName.contains(part) || normEpithet.contains(part) }
            }
        }
        suggestions.clear()
        suggestions.addAll(filtered.take(30))
    }

    fun selectCharacter(character: Character) {
        if (dailyLocked) return
        val currentTarget = target ?: return
        query = ""
        suggestions.clear()
        guessCount += 1

        val row = compareCharacters(currentTarget, character, lang)
        guesses.add(0, row)

        if (row.isWin) {
            won = true
            val uid = (authState as? AuthUiState.LoggedIn)?.uid
            val mode = currentMode
            if (uid != null && mode != null) {
                val finishedGuessCount = guessCount
                viewModelScope.launch {
                    val todayKey = DailyRepository.todayKey()
                    val yesterdayKey = DailyRepository.yesterdayKey()
                    runCatching {
                        StatsRepository.recordGameWin(uid, mode, finishedGuessCount, todayKey, yesterdayKey)
                    }
                    if (mode == GameMode.QUOTIDIEN) {
                        runCatching { DailyRepository.recordDailyWin(uid, todayKey, finishedGuessCount) }
                        runCatching { dailyAggregate = DailyRepository.fetchAggregate(todayKey) }
                    }
                    refreshStats(uid)
                    runCatching { leaderboard = FriendsRepository.fetchLeaderboard(uid) }
                }
            }
        }
    }

    /** Débloquer un indice coûte un malus d'essais. */
    private val hintPenalty = 2

    fun requestHint() {
        if (canAdvanceHint(hintStage, guessCount)) {
            hintStage += 1
            guessCount += hintPenalty
        }
    }

    fun topHints(): TopHints? {
        val t = target ?: return null
        return computeTopHints(t, hintStage, guessCount, lang)
    }

    // --- Révélation via pub (tous les modes sauf Quotidien, à partir de 18 essais) -----------

    /** Vrai si la révélation du personnage (moyennant une pub) est proposable maintenant :
     * pas en mode Quotidien, partie en cours et non terminée, et seuil d'essais atteint. */
    fun canReveal(): Boolean {
        val mode = currentMode ?: return false
        return mode != GameMode.QUOTIDIEN && !won && !revealed && guessCount >= REVEAL_THRESHOLD
    }

    /** Ouvre la boîte de dialogue proposant de regarder une pub pour révéler le personnage. */
    fun requestReveal() {
        if (canReveal()) showAdPrompt = true
    }

    /** Ferme la boîte de dialogue sans lancer la pub. */
    fun dismissAdPrompt() {
        showAdPrompt = false
    }

    /** Appelé par l'UI juste avant de lancer l'affichage de la vraie pub AdMob
     * (voir [AdsManager.show] dans MainActivity.kt), le temps que la pub s'ouvre. */
    fun beginWatchingAd() {
        if (!canReveal()) {
            showAdPrompt = false
            return
        }
        watchingAd = true
    }

    /** L'utilisateur a regardé la pub jusqu'au bout : on révèle le personnage. */
    fun onAdRewardEarned() {
        watchingAd = false
        showAdPrompt = false
        revealCharacter()
    }

    /** Pub fermée trop tôt, échec d'affichage, ou aucune pub disponible pour l'instant :
     * on referme simplement la boîte de dialogue, le joueur peut réessayer. */
    fun onAdNotRewarded() {
        watchingAd = false
        showAdPrompt = false
    }

    private fun revealCharacter() {
        val currentTarget = target ?: return
        revealed = true
        val row = compareCharacters(currentTarget, currentTarget, lang)
        guesses.add(0, row)
    }
}
