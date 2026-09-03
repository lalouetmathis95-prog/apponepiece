package com.example.onepieceapp

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Un personnage jouable, généré par le pipeline `datagen` (scrape Wikipédia) du
 * projet Onepiecedle : nom, épithète, fruit du démon, fluide (haki), premier arc
 * d'apparition, prime, affiliation, taille, et un niveau de difficulté (0 à 8,
 * plus le niveau est élevé, plus le personnage est connu/facile à deviner).
 */
data class Character(
    override val name: String,
    val epithet: String,
    val fruitName: String,
    val fruitType: String,
    val haki: String,
    val arc: String,
    val bountyValue: Long,
    val affiliation: String,
    val heightMeters: Double?,
    override val difficulty: Int,
    /** Nom de fichier de portrait (ex. "monkey_d_luffy.jpg"), récupéré par
     * datagen/fetch_images.py puis fusionné dans ce fichier -- null tant
     * qu'aucune image n'a été trouvée pour ce personnage. */
    override val imageFile: String? = null
) : Guessable {
    override val subtitle: String get() = epithet
    override val imageFolder: String get() = "images"
}

object CharacterRepository {

    private var cache: List<Character>? = null

    fun load(context: Context): List<Character> {
        cache?.let { return it }

        val jsonText = context.assets.open("characters.json").use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        }

        val array = JSONArray(jsonText)
        val result = ArrayList<Character>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                Character(
                    name = obj.getString("name"),
                    epithet = obj.getString("epithet"),
                    fruitName = obj.getString("fruitName"),
                    fruitType = obj.getString("fruitType"),
                    haki = obj.getString("haki"),
                    arc = obj.getString("arc"),
                    bountyValue = obj.optLong("bountyValue", 0L),
                    affiliation = obj.getString("affiliation"),
                    heightMeters = if (obj.isNull("heightMeters")) null else obj.optDouble("heightMeters"),
                    difficulty = obj.optInt("difficulty", 0),
                    imageFile = if (obj.has("imageFile") && !obj.isNull("imageFile")) obj.getString("imageFile") else null
                )
            )
        }
        cache = result
        return result
    }
}
