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
    val name: String,
    val epithet: String,
    val fruitName: String,
    val fruitType: String,
    val haki: String,
    val arc: String,
    val bountyValue: Long,
    val affiliation: String,
    val heightMeters: Double?,
    val difficulty: Int
)

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
                    difficulty = obj.optInt("difficulty", 0)
                )
            )
        }
        cache = result
        return result
    }
}

/**
 * characters.json contient la liste complète : la première moitié en français,
 * la seconde moitié (mêmes personnages, même ordre) en anglais — les deux
 * langues restent donc toujours strictement séparées au sein d'une même partie.
 */
fun charactersForLang(all: List<Character>, lang: Lang): List<Character> {
    val mid = all.size / 2
    return if (lang == Lang.FR) all.subList(0, mid) else all.subList(mid, all.size)
}

/**
 * Positions (dans la moitié de langue, 0 jusqu'à mid) éligibles au mode Quotidien
 * (difficulté >= 3). Une position désigne le même personnage quelle que soit la
 * langue, ce qui permet de choisir un seul personnage du jour côté Firestore et
 * de l'afficher dans la langue de chaque joueur.
 */
fun dailyPoolPositions(all: List<Character>): List<Int> {
    val mid = all.size / 2
    return (0 until mid).filter { all[it].difficulty >= 3 }
}

fun characterAtPosition(all: List<Character>, lang: Lang, position: Int): Character {
    val mid = all.size / 2
    return if (lang == Lang.FR) all[position] else all[mid + position]
}
