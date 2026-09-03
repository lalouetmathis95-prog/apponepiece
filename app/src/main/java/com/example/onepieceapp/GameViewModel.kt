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

    private var allEntries: List<Guessable> = emptyList()
    private var dailyPositions: List<Int> = emptyList()
    private var pool: List<Guessable> = emptyList()

    /** Vrai pendant le chargement des données de l'univers choisi (une fois
     * seulement, la première fois qu'on le sélectionne -- voir [selectUniverse]).
     * Ne concerne pas l'écran de choix d'univers lui-même, affiché avant même
     * qu'un univers -- et donc un chargement -- n'existe. */
    var isLoading by mutableStateOf(false)
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

    // --- Avatar de profil -------------------------------------------------------

    /** Personnages/champions choisissables comme avatar, groupés par univers --
     * uniquement ceux ayant une image (voir [openAvatarPicker]). Chargés à la
     * demande (les deux univers, indépendamment de celui actuellement actif :
     * l'avatar peut venir de n'importe lequel), pas au lancement de l'appli. */
    var avatarPickerEntries by mutableStateOf<Map<Universe, List<Guessable>>>(emptyMap())
        private set

    var avatarPickerLoading by mutableStateOf(false)
        private set

    /** Charge (une seule fois, mis en cache par CharacterRepository/ChampionRepository)
     * les entrées avec image des deux univers, pour le sélecteur d'avatar. Lecture
     * locale pure (assets embarqués) : pas besoin de réseau, contrairement au
     * reste du profil. */
    fun openAvatarPicker() {
        if (avatarPickerEntries.isNotEmpty() || avatarPickerLoading) return
        avatarPickerLoading = true
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            // Important : on filtre "a une image" APRÈS avoir découpé par langue
            // (entriesForLang), pas avant -- entriesForLang suppose que la liste
            // qu'on lui passe est encore "FR concaténé avec EN" à parts égales
            // (voir Guessable.kt), ce qu'un filtre préalable casserait.
            val onePiece = entriesForLang(CharacterRepository.load(application), lang)
                .filter { it.imageFile != null }
            val lol = entriesForLang(ChampionRepository.load(application), lang)
                .filter { it.imageFile != null }
            avatarPickerEntries = mapOf(
                Universe.ONE_PIECE to onePiece,
                Universe.LEAGUE_OF_LEGENDS to lol
            )
            avatarPickerLoading = false
        }
    }

    /** Choisit [entry] (de l'univers [universe], pas forcément celui actif en ce
     * moment) comme avatar de profil -- visible du joueur lui-même comme de ses
     * amis (classement, liste d'amis). Mise à jour locale optimiste immédiate,
     * sans attendre l'écriture Firestore (voir StatsRepository.setAvatar). */
    fun setAvatar(universe: Universe, entry: Guessable) {
        val uid = (authState as? AuthUiState.LoggedIn)?.uid ?: return
        if (entry.imageFile == null) return
        userStats = userStats.copy(
            avatarUniverse = universe,
            avatarImageFolder = entry.imageFolder,
            avatarImageFile = entry.imageFile,
            avatarName = entry.name
        )
        viewModelScope.launch {
            // Une tentative, puis une seule reprise après une courte pause si la
            // première échoue (ex. juste après une reconnexion/relance, le jeton
            // d'authentification peut mettre un instant à être prêt côté
            // Firestore) : sans quoi l'écriture pouvait échouer silencieusement,
            // laissant l'avatar affiché seulement en local (mise à jour optimiste
            // ci-dessus) jusqu'à la prochaine fermeture de l'appli, où il
            // "disparaissait" puisque jamais réellement enregistré côté serveur.
            var result = runCatching { StatsRepository.setAvatar(uid, universe, entry) }
            if (result.isFailure) {
                android.util.Log.w("GameViewModel", "setAvatar: 1st attempt failed, retrying", result.exceptionOrNull())
                kotlinx.coroutines.delay(1500)
                result = runCatching { StatsRepository.setAvatar(uid, universe, entry) }
            }
            result.onFailure {
                android.util.Log.e("GameViewModel", "setAvatar: failed to persist avatar to Firestore", it)
            }
            // Le classement affiché est celui de l'univers ACTIF (this.universe),
            // pas forcément celui dont vient l'avatar choisi -- ex. avatar pris
            // dans League of Legends alors qu'on consulte le classement One Piece.
            val activeUniverse = this@GameViewModel.universe
            if (activeUniverse != null) {
                runCatching { leaderboard = FriendsRepository.fetchLeaderboard(uid, activeUniverse) }
            }
        }
    }

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
            refreshFriendsData(user.uid)
            // Les stats de jeu sont scopées par univers : rien à en charger tant
            // que le joueur n'en a pas choisi un (voir selectUniverse). En
            // revanche le PROFIL (pseudo + avatar, document users/{uid}) n'est
            // pas scopé par univers et doit se charger dès la connexion, même
            // sur l'écran de choix d'univers -- son bouton "profil" (voir
            // TopBarUniverseOnly) ouvre déjà le dialogue de stats à ce stade.
            // Avant ce correctif, userStats restait à sa valeur par défaut (donc
            // sans avatar) tant qu'aucun univers n'avait été sélectionné dans
            // CETTE session : après un redémarrage à froid, ouvrir le profil
            // depuis l'écran de choix d'univers montrait donc "aucun avatar",
            // ce qui ressemblait à un avatar qui "disparaît à la fermeture".
            val u = universe
            if (u != null) refreshStats(user.uid, u) else refreshProfile(user.uid)
        } else {
            incomingRequests = emptyList()
            friends = emptyList()
            leaderboard = emptyList()
        }
    }

    init {
        FirebaseServices.auth.addAuthStateListener(authListener)
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
        backToUniverseSelection()
    }

    /** Confirmation affichée après l'envoi réussi de l'email de réinitialisation. */
    var passwordResetSent by mutableStateOf(false)
        private set

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            authError = "Email requis"
            return
        }
        authBusy = true
        authError = null
        viewModelScope.launch {
            try {
                AuthRepository.sendPasswordReset(email.trim())
                passwordResetSent = true
            } catch (e: Exception) {
                authError = e.localizedMessage ?: e.toString()
            } finally {
                authBusy = false
            }
        }
    }

    /** Réinitialise l'état de l'écran "mot de passe oublié" (en le quittant, ou en y rentrant). */
    fun clearPasswordResetState() {
        passwordResetSent = false
        authError = null
    }

    /** Charge le profil (pseudo + avatar, document users/{uid}) -- indépendant
     * de tout univers, donc appelable dès la connexion, avant même que le
     * joueur n'ait choisi un univers (voir l'écouteur d'authentification). */
    private fun refreshProfile(uid: String) {
        viewModelScope.launch {
            runCatching { StatsRepository.loadProfile(uid) }.onSuccess { profile ->
                userStats = userStats.copy(
                    username = profile.username,
                    avatarUniverse = profile.avatarUniverse,
                    avatarImageFolder = profile.avatarImageFolder,
                    avatarImageFile = profile.avatarImageFile,
                    avatarName = profile.avatarName
                )
            }
        }
    }

    private fun refreshStats(uid: String, universe: Universe) {
        // Profil (pseudo + avatar) et stats de l'univers actif (sous-document
        // universeStats/{univers}) sont chargés et fusionnés INDÉPENDAMMENT
        // dans userStats : un échec de l'un des deux appels Firestore (ex.
        // juste après un démarrage à froid, le jeton d'authentification peut
        // mettre un instant à être prêt) n'efface plus ce que l'autre a réussi
        // à charger -- notamment l'avatar.
        refreshProfile(uid)
        viewModelScope.launch {
            runCatching { StatsRepository.loadUniverseStats(uid, universe) }.onSuccess { stats ->
                userStats = userStats.copy(
                    gamesPlayed = stats.gamesPlayed,
                    gamesWon = stats.gamesWon,
                    currentStreak = stats.currentStreak,
                    maxStreak = stats.maxStreak,
                    lastWonDailyDate = stats.lastWonDailyDate,
                    totalGuessesSum = stats.totalGuessesSum,
                    modeWins = stats.modeWins
                )
            }
        }
    }

    private fun refreshFriendsData(uid: String) {
        viewModelScope.launch {
            runCatching { incomingRequests = FriendsRepository.listIncomingRequests(uid) }
            runCatching { friends = FriendsRepository.listFriends(uid) }
        }
        refreshLeaderboard(uid)
    }

    /** Le classement est scopé par univers (voir FriendsRepository.fetchLeaderboard) --
     * ne fait rien tant qu'aucun univers n'est choisi. */
    private fun refreshLeaderboard(uid: String) {
        val u = universe ?: return
        viewModelScope.launch {
            runCatching { leaderboard = FriendsRepository.fetchLeaderboard(uid, u) }
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
     * nouvelle langue (les personnages/champions ne sont pas les mêmes objets
     * d'une langue à l'autre, on ne peut donc pas continuer la partie telle quelle). */
    fun selectLanguage(newLang: Lang) {
        if (newLang == lang) return
        lang = newLang
        currentMode?.let { startMode(it) }
        // Les entrées du sélecteur d'avatar (voir openAvatarPicker) sont figées
        // dans la langue au moment du chargement -- on les vide pour forcer un
        // rechargement (noms traduits) au prochain ouverture du sélecteur.
        avatarPickerEntries = emptyMap()
    }

    // --- Univers ---------------------------------------------------------------

    /** Univers choisi au lancement (One Piece ou League of Legends), avant même
     * le choix du mode de jeu. Nul tant que le joueur n'a rien choisi. */
    var universe by mutableStateOf<Universe?>(null)
        private set

    /** Seuils de difficulté par mode, propres à chaque univers : l'échelle One
     * Piece va de 0 à 8, celle de League of Legends (note officielle Riot) de
     * 1 à 10 -- les seuils sont donc différents, mais la forme est la même
     * (imbriquée, comme datagen/difficulty.py : Facile ⊂ Moyen ⊂ Difficile ⊂
     * Dieu, qui lui ne filtre jamais rien). */
    private fun facileThreshold(u: Universe) = if (u == Universe.ONE_PIECE) 6 else 8
    private fun moyenThreshold(u: Universe) = 5
    private fun difficileThreshold(u: Universe) = if (u == Universe.ONE_PIECE) 1 else 3

    fun selectUniverse(newUniverse: Universe) {
        universe = newUniverse
        isLoading = true
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val loaded: List<Guessable> = when (newUniverse) {
                Universe.ONE_PIECE -> CharacterRepository.load(application)
                Universe.LEAGUE_OF_LEGENDS -> ChampionRepository.load(application)
            }
            allEntries = loaded
            dailyPositions = dailyEntryPositions(loaded, moyenThreshold(newUniverse))
            // Reprend une éventuelle partie sauvegardée localement (voir
            // GameStateStore) -- ex. l'appli a été fermée/tuée par le système
            // pendant une partie en cours, ce qu'un ViewModel seul ne survit pas.
            restoreSavedGame(newUniverse)
            isLoading = false
        }
        val uid = (authState as? AuthUiState.LoggedIn)?.uid
        if (uid != null) {
            refreshStats(uid, newUniverse)
            refreshLeaderboard(uid)
        }
    }

    /** Retour à l'écran de choix d'univers, en réinitialisant aussi une éventuelle
     * partie en cours (contrairement à [backToModeSelection], qui la met en
     * pause plutôt que de la perdre) : en changeant d'univers, le pool et la
     * cible de la partie en cours ne veulent plus rien dire. */
    fun backToUniverseSelection() {
        universe = null
        allEntries = emptyList()
        dailyPositions = emptyList()
        backToModeSelection(allowPause = false)
    }

    /** Comparaison ligne par ligne, déléguée à l'implémentation propre à
     * l'univers en cours : les colonnes (et donc les types concrets attendus)
     * diffèrent entre Character (One Piece) et Champion (League of Legends). */
    private fun compareEntries(target: Guessable, guess: Guessable, lang: Lang): GuessRow =
        when (universe) {
            Universe.ONE_PIECE -> compareCharacters(target as Character, guess as Character, lang)
            Universe.LEAGUE_OF_LEGENDS -> compareChampions(target as Champion, guess as Champion, lang)
            null -> error("Aucun univers sélectionné")
        }

    /** Sauvegarde locale (voir [GameStateStore]) de la partie active OU mise en
     * pause, pour qu'elle survive à la fermeture/destruction de l'appli --
     * appelé à chaque évolution notable de la partie (essai, indice, victoire,
     * mise en pause...). S'il n'y a plus rien à sauvegarder (aucune partie
     * active ni en pause), efface simplement la sauvegarde précédente. */
    private fun persistGameState() {
        val application = getApplication<Application>()
        val mode = currentMode ?: pausedMode
        val u = universe
        val t = target
        if (mode == null || u == null || t == null) {
            GameStateStore.clear(application)
            return
        }
        GameStateStore.save(
            application,
            GameStateStore.SavedGame(
                universe = u,
                lang = lang,
                mode = mode,
                paused = currentMode == null,
                targetName = t.name,
                guessedNames = guesses.map { it.entry.name },
                guessCount = guessCount,
                hintStage = hintStage,
                won = won,
                revealed = revealed,
                dailyLocked = dailyLocked
            )
        )
    }

    /** Reprend, si elle existe et correspond à l'univers [u] qu'on vient de
     * charger, la partie sauvegardée localement (voir [GameStateStore]) --
     * typiquement après une fermeture/un "kill" de l'appli par le système en
     * pleine partie. Ne fait rien si aucune sauvegarde ne correspond (ex.
     * après une victoire déjà entièrement soldée, ou un changement de
     * personnages/champions entre deux mises à jour de l'appli). */
    private fun restoreSavedGame(u: Universe) {
        val application = getApplication<Application>()
        val saved = GameStateStore.load(application) ?: return
        if (saved.universe != u) return

        lang = saved.lang
        val entries = entriesForLang(allEntries, lang)
        val restoredTarget = entries.find { it.name == saved.targetName } ?: run {
            // La cible sauvegardée n'existe plus dans les données actuelles (ex.
            // personnage renommé/retiré lors d'une mise à jour de l'appli) : la
            // sauvegarde n'est plus exploitable, on l'efface pour ne pas
            // retenter cette restauration à chaque lancement.
            GameStateStore.clear(application)
            return
        }

        pool = poolFor(saved.mode)
        target = restoredTarget
        guesses.clear()
        guesses.addAll(
            saved.guessedNames.mapNotNull { name ->
                val guessedEntry = entries.find { it.name == name } ?: return@mapNotNull null
                compareEntries(restoredTarget, guessedEntry, lang)
            }
        )
        guessCount = saved.guessCount
        hintStage = saved.hintStage
        won = saved.won
        revealed = saved.revealed
        dailyLocked = saved.dailyLocked
        watchingAd = false
        showAdPrompt = false

        if (saved.paused) {
            pausedMode = saved.mode
            currentMode = null
        } else {
            currentMode = saved.mode
            pausedMode = null
        }

        // Le nom du personnage/champion de la veille et l'agrégat du jour ne sont
        // que des infos d'appoint (voir startDailyMode) -- on les rafraîchit en
        // tâche de fond, sans toucher à la cible/aux essais qu'on vient de restaurer.
        if (saved.mode == GameMode.QUOTIDIEN) {
            refreshDailyExtras(u)
        }
    }

    // --- Partie ---------------------------------------------------------------

    var currentMode by mutableStateOf<GameMode?>(null)
        private set

    var modeStarting by mutableStateOf(false)
        private set

    var target by mutableStateOf<Guessable?>(null)
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
     * [canAdvanceHint] : 4, 7 puis 10), et le débloquer coûte un malus.
     * Spécifique à l'univers One Piece -- voir [topHints]. */
    var hintStage by mutableStateOf(1)
        private set

    var won by mutableStateOf(false)
        private set

    /** Vrai si le personnage/champion a été révélé via une pub plutôt que trouvé par le joueur. */
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

    /** Mode dont la partie est actuellement "en pause" -- le joueur est revenu à
     * l'écran de choix de mode (bouton retour ou "↩ changer de mode") sans que
     * la partie soit terminée. Permet à [startMode] de la reprendre exactement
     * où elle en était (essais, indices, cible...) si le même mode est relancé,
     * plutôt que de perdre la progression à chaque retour arrière. Remis à null
     * dès qu'un AUTRE mode est lancé, ou que la partie mise en pause est
     * abandonnée pour de bon (voir [backToModeSelection]). */
    private var pausedMode: GameMode? = null

    /** Pour l'aperçu sur l'écran d'accueil, avant même de lancer le mode Quotidien. */
    val dailyAlreadyPlayedToday: Boolean
        get() = userStats.lastWonDailyDate == DailyRepository.todayKey()

    /** Flag local (indépendant de Firestore) consulté par [DailyReminderWorker]
     * pour savoir s'il doit encore rappeler de jouer le Quotidien aujourd'hui. */
    private fun markDailyPlayedLocally(universe: Universe, dateKey: String) {
        getApplication<Application>()
            .getSharedPreferences(NotificationHelper.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(NotificationHelper.keyLastDailyWin(universe), dateKey)
            .apply()
    }

    var query by mutableStateOf("")

    val suggestions = mutableStateListOf<Guessable>()

    /**
     * Retour à l'écran de choix de mode. Si une partie est en cours et pas
     * encore terminée (ni gagnée, ni révélée), elle est mise en pause plutôt
     * que perdue -- voir [pausedMode] et [startMode], qui la reprend si le même
     * mode est relancé. [allowPause] vaut false quand l'appelant sait que la
     * partie en cours n'a de toute façon plus de sens à reprendre (changement
     * d'univers, voir [backToUniverseSelection]) : dans ce cas, tout est
     * réinitialisé comme avant.
     */
    fun backToModeSelection(allowPause: Boolean = true) {
        val mode = currentMode
        if (allowPause && mode != null && !won && !revealed) {
            pausedMode = mode
        } else {
            pausedMode = null
            target = null
            guesses.clear()
            guessCount = 0
            hintStage = 1
            won = false
            revealed = false
        }
        currentMode = null
        query = ""
        suggestions.clear()
        persistGameState()
    }

    /**
     * Les pools de personnages/champions par mode sont imbriqués (comme sur le
     * site Onepiecedle d'origine, cf. datagen/difficulty.py) : Facile ⊂ Moyen ⊂
     * Difficile, chaque mode plus difficile élargissant simplement le pool du
     * précédent vers des entrées moins connues. Dieu ne filtre rien du tout :
     * il pioche dans TOUTES les entrées, y compris les plus obscures, ce qui en
     * fait le mode le plus dur. Quotidien reprend le pool du mode Moyen (le
     * quotidien doit rester d'une difficulté moyenne).
     *
     * Facile et Moyen doivent en plus être garantis d'avoir une image (voir
     * [withImageFallback]) : ce sont les modes "faciles", où le joueur doit
     * pouvoir s'appuyer sur le visuel. Difficile et Dieu n'imposent pas cette
     * contrainte.
     */
    private fun poolFor(mode: GameMode): List<Guessable> {
        val u = universe ?: return emptyList()
        val base = entriesForLang(allEntries, lang)
        return when (mode) {
            GameMode.FACILE -> withImageFallback(base.filter { it.difficulty >= facileThreshold(u) })
            GameMode.MOYEN -> withImageFallback(base.filter { it.difficulty >= moyenThreshold(u) })
            GameMode.DIFFICILE -> base.filter { it.difficulty >= difficileThreshold(u) }
            GameMode.QUOTIDIEN -> withImageFallback(base.filter { it.difficulty >= moyenThreshold(u) })
            GameMode.DIEU -> base
        }
    }

    /** Ne garde que les entrées ayant une image, sauf si ça viderait le pool
     * (import des images pas encore fait, ou toutes les entrées du pool en
     * sont dépourvues) -- dans ce cas on retombe sur le pool complet plutôt
     * que de bloquer le mode. */
    private fun withImageFallback(pool: List<Guessable>): List<Guessable> {
        val withImage = pool.filter { it.imageFile != null }
        return withImage.ifEmpty { pool }
    }

    fun startMode(mode: GameMode) {
        val u = universe ?: return
        if (allEntries.isEmpty()) return

        // Reprise d'une partie mise en pause (retour arrière sur ce même mode,
        // voir [backToModeSelection]) : on garde tout l'état tel quel (essais,
        // indices, cible, dailyLocked...) au lieu de recommencer de zéro.
        if (mode == pausedMode) {
            pausedMode = null
            currentMode = mode
            persistGameState()
            return
        }
        pausedMode = null

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
                viewModelScope.launch { runCatching { StatsRepository.recordGameStart(uid, u) } }
            }
        }

        if (mode == GameMode.QUOTIDIEN) {
            startDailyMode(u)
        } else {
            target = pool[Random.nextInt(pool.size)]
            persistGameState()
        }
    }

    /** Le personnage/champion du jour est partagé entre tous les joueurs via un
     * document Firestore (scopé par univers) : tout le monde tombe sur le même,
     * quelle que soit sa langue. */
    private fun startDailyMode(universe: Universe) {
        if (dailyPositions.isEmpty()) return
        modeStarting = true
        viewModelScope.launch {
            try {
                val todayKey = DailyRepository.todayKey()
                val todayIndex = DailyRepository.fetchOrCreateDailyIndex(universe, todayKey, dailyPositions.size)
                target = entryAtPosition(allEntries, lang, dailyPositions[todayIndex])
                persistGameState()

                loadDailyExtras(universe, todayKey)
            } finally {
                modeStarting = false
            }
        }
    }

    /** Nom du personnage/champion de la veille + agrégat du jour (nombre de
     * joueurs ayant trouvé, moyenne d'essais) : de simples infos d'appoint
     * affichées à côté du Quotidien, jamais nécessaires pour jouer -- d'où le
     * [runCatching] individuel sur chacune (une panne réseau sur l'une ne doit
     * pas priver l'autre, ni bloquer la partie elle-même). */
    private suspend fun loadDailyExtras(universe: Universe, todayKey: String) {
        val yesterdayKey = DailyRepository.yesterdayKey()
        runCatching {
            val yesterdaySnap = FirebaseServices.firestore
                .collection(Collections.DAILY_CHALLENGES).document(DailyRepository.dailyDocId(universe, yesterdayKey)).get().await()
            val yesterdayIdx = yesterdaySnap.getLong("characterIndex")?.toInt()
            if (yesterdayIdx != null && yesterdayIdx in dailyPositions.indices) {
                previousDailyCharacterName =
                    entryAtPosition<Guessable>(allEntries, lang, dailyPositions[yesterdayIdx]).name
            }
        }
        runCatching { dailyAggregate = DailyRepository.fetchAggregate(universe, todayKey) }
    }

    /** Rafraîchit les infos d'appoint du Quotidien (voir [loadDailyExtras]) sans
     * toucher à la cible/aux essais -- utilisé après restauration d'une partie
     * sauvegardée (voir [restoreSavedGame]), où la cible du jour est déjà
     * connue localement (calcul déterministe, voir DailyRepository) et ne doit
     * surtout pas être recalculée/réinitialisée. */
    private fun refreshDailyExtras(universe: Universe) {
        viewModelScope.launch {
            loadDailyExtras(universe, DailyRepository.todayKey())
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
        val already = guesses.map { it.entry.name }.toSet()
        val filtered = pool.filter { c ->
            c.name !in already && run {
                // On cherche à la fois dans le nom et dans le sous-titre (épithète
                // ou titre selon l'univers), pour retrouver une entrée même en
                // tapant un surnom (ex. "Mr 0" pour Crocodile).
                val normName = normalize(c.name)
                val normSubtitle = normalize(c.subtitle)
                parts.all { part -> normName.contains(part) || normSubtitle.contains(part) }
            }
        }
        suggestions.clear()
        suggestions.addAll(filtered.take(30))
    }

    fun selectCharacter(character: Guessable) {
        if (dailyLocked) return
        val currentTarget = target ?: return
        val u = universe ?: return
        query = ""
        suggestions.clear()
        guessCount += 1

        val row = compareEntries(currentTarget, character, lang)
        guesses.add(0, row)
        if (row.isWin) won = true
        persistGameState()

        if (row.isWin) {
            val uid = (authState as? AuthUiState.LoggedIn)?.uid
            val mode = currentMode
            if (uid != null && mode != null) {
                val finishedGuessCount = guessCount
                viewModelScope.launch {
                    val todayKey = DailyRepository.todayKey()
                    val yesterdayKey = DailyRepository.yesterdayKey()
                    runCatching {
                        StatsRepository.recordGameWin(uid, u, mode, finishedGuessCount, todayKey, yesterdayKey)
                    }
                    if (mode == GameMode.QUOTIDIEN) {
                        runCatching { DailyRepository.recordDailyWin(uid, u, todayKey, finishedGuessCount) }
                        runCatching { dailyAggregate = DailyRepository.fetchAggregate(u, todayKey) }
                        markDailyPlayedLocally(u, todayKey)
                    }
                    refreshStats(uid, u)
                    runCatching { leaderboard = FriendsRepository.fetchLeaderboard(uid, u) }
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
            persistGameState()
        }
    }

    /** Les indices "top" (arc/affiliation/fruit) n'existent que pour l'univers
     * One Piece -- League of Legends n'en a pas d'équivalent pour l'instant,
     * donc null ici cache simplement la carte d'indices dans l'UI. */
    fun topHints(): TopHints? {
        if (universe != Universe.ONE_PIECE) return null
        val t = target as? Character ?: return null
        return computeTopHints(t, hintStage, guessCount, lang)
    }

    // --- Révélation via pub (tous les modes sauf Quotidien, à partir de 18 essais) -----------

    /** Vrai si la révélation du personnage/champion (moyennant une pub) est proposable
     * maintenant : pas en mode Quotidien, partie en cours et non terminée, et seuil d'essais atteint. */
    fun canReveal(): Boolean {
        val mode = currentMode ?: return false
        return mode != GameMode.QUOTIDIEN && !won && !revealed && guessCount >= REVEAL_THRESHOLD
    }

    /** Ouvre la boîte de dialogue proposant de regarder une pub pour révéler le personnage/champion. */
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

    /** L'utilisateur a regardé la pub jusqu'au bout : on révèle le personnage/champion. */
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
        val row = compareEntries(currentTarget, currentTarget, lang)
        guesses.add(0, row)
        persistGameState()
    }
}
