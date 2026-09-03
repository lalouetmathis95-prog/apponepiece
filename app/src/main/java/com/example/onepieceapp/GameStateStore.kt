package com.example.onepieceapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistance locale (SharedPreferences) de la partie en cours (ou mise en
 * pause, voir GameViewModel.pausedMode), pour qu'elle survive à une
 * fermeture de l'appli -- pas seulement à une rotation d'écran. Un
 * ViewModel Android survit à une recréation d'Activity, mais PAS à la
 * destruction du processus par le système (courant quand l'appli passe en
 * arrière-plan) ; sans cette sauvegarde, rouvrir l'appli recréait un
 * ViewModel tout neuf et perdait essais/indices/cible.
 *
 * Une seule partie sauvegardée à la fois (un joueur ne joue qu'un
 * univers/mode à la fois) : écrasée à chaque évolution de la partie en
 * cours, effacée en quittant définitivement l'univers ou le mode (voir
 * GameViewModel.backToUniverseSelection / backToModeSelection).
 */
object GameStateStore {
    private const val PREFS_NAME = "game_state"
    private const val KEY_STATE = "saved_game"

    data class SavedGame(
        val universe: Universe,
        val lang: Lang,
        val mode: GameMode,
        /** Vrai si la partie était en pause (retour arrière) plutôt qu'activement affichée. */
        val paused: Boolean,
        val targetName: String,
        /** Noms des entrées devinées, dans le même ordre que GameViewModel.guesses
         * (la plus récente en premier). */
        val guessedNames: List<String>,
        val guessCount: Int,
        val hintStage: Int,
        val won: Boolean,
        val revealed: Boolean,
        val dailyLocked: Boolean
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(context: Context, state: SavedGame) {
        val obj = JSONObject().apply {
            put("universe", state.universe.name)
            put("lang", state.lang.name)
            put("mode", state.mode.name)
            put("paused", state.paused)
            put("target", state.targetName)
            put("guessed", JSONArray(state.guessedNames))
            put("guessCount", state.guessCount)
            put("hintStage", state.hintStage)
            put("won", state.won)
            put("revealed", state.revealed)
            put("dailyLocked", state.dailyLocked)
        }
        prefs(context).edit().putString(KEY_STATE, obj.toString()).apply()
    }

    /** Renvoie null si aucune partie sauvegardée, ou si le JSON est corrompu/incompatible
     * (ex. après une mise à jour de l'appli qui aurait changé le format) -- dans ce
     * cas on abandonne simplement la restauration plutôt que de planter. */
    fun load(context: Context): SavedGame? {
        val raw = prefs(context).getString(KEY_STATE, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val guessedArray = obj.getJSONArray("guessed")
            val guessed = (0 until guessedArray.length()).map { guessedArray.getString(it) }
            SavedGame(
                universe = Universe.valueOf(obj.getString("universe")),
                lang = Lang.valueOf(obj.getString("lang")),
                mode = GameMode.valueOf(obj.getString("mode")),
                paused = obj.getBoolean("paused"),
                targetName = obj.getString("target"),
                guessedNames = guessed,
                guessCount = obj.getInt("guessCount"),
                hintStage = obj.getInt("hintStage"),
                won = obj.getBoolean("won"),
                revealed = obj.getBoolean("revealed"),
                dailyLocked = obj.getBoolean("dailyLocked")
            )
        }.getOrNull()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_STATE).apply()
    }
}
