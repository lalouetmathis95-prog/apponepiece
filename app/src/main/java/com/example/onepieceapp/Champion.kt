package com.example.onepieceapp

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Un champion League of Legends, généré par datagen_lol/ (API officielle
 * Data Dragon de Riot, complétée par Community Dragon pour le type de
 * dégâts et le nombre de skins) : nom, titre, rôle(s), type de ressource,
 * type de portée, type de dégâts, nombre de skins.
 *
 * [difficulty] (note officielle Riot, 1 à 10) n'est PLUS affichée comme
 * colonne de comparaison dans le jeu -- elle ne sert plus qu'en interne pour
 * répartir les champions entre les modes Facile/Moyen/Difficile/Dieu (voir
 * GameViewModel.poolFor), remplacée dans le tableau par damageType/skinCount.
 */
data class Champion(
    override val name: String,
    val title: String,
    val role: String,
    val resource: String,
    val rangeType: String,
    val damageType: String,
    val skinCount: Int,
    override val difficulty: Int,
    override val imageFile: String? = null
) : Guessable {
    override val subtitle: String get() = title
    override val imageFolder: String get() = "images_lol"
}

object ChampionRepository {

    private var cache: List<Champion>? = null

    fun load(context: Context): List<Champion> {
        cache?.let { return it }

        val jsonText = context.assets.open("champions.json").use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }

        val array = JSONArray(jsonText)
        val result = ArrayList<Champion>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                Champion(
                    name = obj.getString("name"),
                    title = obj.getString("title"),
                    role = obj.getString("role"),
                    resource = obj.getString("resource"),
                    rangeType = obj.getString("rangeType"),
                    damageType = obj.optString("damageType", "Inconnu"),
                    skinCount = obj.optInt("skinCount", 0),
                    difficulty = obj.optInt("difficulty", 0),
                    imageFile = if (obj.has("imageFile") && !obj.isNull("imageFile")) obj.getString("imageFile") else null
                )
            )
        }
        cache = result
        return result
    }
}
