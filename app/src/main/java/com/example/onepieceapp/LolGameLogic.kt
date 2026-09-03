package com.example.onepieceapp

/**
 * Comparaison ligne par ligne pour l'univers League of Legends, pendant
 * homologue de [compareCharacters] pour One Piece -- mêmes règles de
 * couleurs (vert = trouvé, orange = partiellement correct, rouge = faux),
 * mais des colonnes propres aux champions LoL : rôle(s), ressource, type de
 * portée, type de dégâts, et nombre de skins (indice plus/moins, comme la
 * taille ou la prime côté One Piece).
 *
 * Pas de colonne "difficulté" : la note officielle Riot ne sert plus qu'en
 * interne pour répartir les champions entre modes (voir Champion.difficulty),
 * remplacée ici par des colonnes plus intéressantes à deviner. Pas non plus
 * d'indices "top" (arc/affiliation/fruit) ni de chronologie d'arcs pour cet
 * univers : [GameViewModel.topHints] renvoie simplement null quand l'univers
 * est League of Legends, ce qui masque la carte d'indices dans l'UI (voir
 * TopHintsCard dans MainActivity.kt).
 */
private fun splitTags(value: String): List<String> =
    value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

private fun skinCountHint(guessValue: Int, targetValue: Int, s: AppStrings): String {
    return if (targetValue > guessValue) "${s.moreThan} $guessValue" else "${s.lessThan} $guessValue"
}

fun compareChampions(target: Champion, guess: Champion, lang: Lang): GuessRow {
    val s = stringsFor(lang)
    val cells = mutableListOf<GuessCell>()

    // Nom
    cells += if (guess.name == target.name) {
        GuessCell(s.colPersonnage, guess.name, CellResult.MATCH)
    } else {
        GuessCell(s.colPersonnage, guess.name, CellResult.MISS)
    }

    // Rôle(s) -- comparaison partielle possible (un champion a souvent 2 tags)
    if (guess.role == target.role) {
        cells += GuessCell(s.colRole, guess.role, CellResult.MATCH)
    } else {
        val guessTags = splitTags(guess.role)
        val targetTags = splitTags(target.role)
        val hasCommon = guessTags.any { it in targetTags }
        cells += GuessCell(s.colRole, guess.role, if (hasCommon) CellResult.PARTIAL else CellResult.MISS)
    }

    // Ressource (Mana, Énergie, Aucune...)
    cells += if (guess.resource == target.resource) {
        GuessCell(s.colResource, guess.resource, CellResult.MATCH)
    } else {
        GuessCell(s.colResource, guess.resource, CellResult.MISS)
    }

    // Type de portée (Mêlée / Distance)
    cells += if (guess.rangeType == target.rangeType) {
        GuessCell(s.colRangeType, guess.rangeType, CellResult.MATCH)
    } else {
        GuessCell(s.colRangeType, guess.rangeType, CellResult.MISS)
    }

    // Type de dégâts (Physique / Magique / Mixte)
    cells += if (guess.damageType == target.damageType) {
        GuessCell(s.colDamageType, guess.damageType, CellResult.MATCH)
    } else {
        GuessCell(s.colDamageType, guess.damageType, CellResult.MISS)
    }

    // Nombre de skins
    cells += if (guess.skinCount == target.skinCount) {
        GuessCell(s.colSkinCount, guess.skinCount.toString(), CellResult.MATCH)
    } else {
        GuessCell(s.colSkinCount, skinCountHint(guess.skinCount, target.skinCount, s), CellResult.MISS)
    }

    val isWin = cells.all { it.result == CellResult.MATCH }
    return GuessRow(guess, cells, isWin)
}
