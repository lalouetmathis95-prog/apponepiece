package com.example.onepieceapp

/** Couleur d'une case de résultat, comme dans le jeu web d'origine. */
enum class CellResult { MATCH, PARTIAL, MISS }

data class GuessCell(val label: String, val value: String, val result: CellResult)

data class GuessRow(val character: Character, val cells: List<GuessCell>, val isWin: Boolean)

/**
 * Ordre chronologique des arcs, utilisé pour l'indice "Avant/Après". Il existe une
 * version par langue car les champs "arc" du jeu de données ne sont pas les mêmes
 * chaînes de caractères en français et en anglais.
 */
val ARC_ORDER_FR = listOf(
    "Romance Dawn",
    "Village d'Orange",
    "Village de Sirop",
    "Baratie",
    "Arlong Park",
    "Loguetown",
    "Reverse Mountain",
    "Whiskey Peak",
    "Little Garden",
    "Île de Drum",
    "Alabasta",
    "Jaya",
    "Skypiea",
    "Long Ring Long Land",
    "Water Seven",
    "Enies Lobby",
    "Post-Enies Lobby",
    "Thriller Bark",
    "Archipel des Sabaody",
    "Amazon Lily",
    "Impel Down",
    "Marine Ford",
    "Post-Guerre",
    "Retour à Sabaody",
    "Île des Hommes-Poissons",
    "Punk Hazard",
    "Dressrosa",
    "Zo",
    "Île Tougato",
    "Rêverie",
    "Pays des Wa",
    "Egg Head",
    "Erbaf"
)

val ARC_ORDER_EN = listOf(
    "Romance Dawn",
    "Orange Village",
    "Syrup Village",
    "Baratie",
    "Arlong Park",
    "Loguetown",
    "Reverse Mountain",
    "Whiskey Peak",
    "Little Garden",
    "Drum Island",
    "Alabasta",
    "Jaya",
    "Skypiea",
    "Long Ring Long Land",
    "Water Seven",
    "Enies Lobby",
    "Post-Enies Lobby",
    "Thriller Bark",
    "Sabaody Archipelago",
    "Amazon Lily",
    "Impel Down",
    "Marine Ford",
    "Post-War",
    "Back to Sabaody",
    "Fish-Man Island",
    "Punk Hazard",
    "Dressrosa",
    "Zo",
    "Tougato Island",
    "Reverie",
    "Land of the Wa",
    "Egg Head",
    "Erbaf"
)

fun arcOrderFor(lang: Lang): List<String> = if (lang == Lang.FR) ARC_ORDER_FR else ARC_ORDER_EN

/** Prime formatée pour l'affichage ("3 B Berry", "50 M Berry", "1200000 Berry"). */
private fun formatBounty(value: Long): String = when {
    value >= 1_000_000_000 -> "${"%.3g".format(value / 1_000_000_000.0).trimEnd('0').trimEnd('.')} B Berry"
    value >= 1_000_000 -> "${"%.3g".format(value / 1_000_000.0).trimEnd('0').trimEnd('.')} M Berry"
    else -> "$value Berry"
}

/** Taille formatée pour l'affichage ("1.74 m"), ou "???" si inconnue. */
private fun formatHeight(meters: Double?): String = meters?.let { "%.2f m".format(it) } ?: "???"

private fun bountyHint(guessValue: Long, targetValue: Long, s: AppStrings): String {
    val text = formatBounty(guessValue)
    return if (targetValue > guessValue) "${s.moreThan} $text" else "${s.lessThan} $text"
}

private fun heightHint(guessMeters: Double?, targetMeters: Double?, s: AppStrings): String {
    if (guessMeters == null || targetMeters == null) return "???"
    val text = formatHeight(guessMeters)
    return if (targetMeters > guessMeters) "${s.moreThan} $text" else "${s.lessThan} $text"
}

private fun arcHint(guessArc: String, targetArc: String, lang: Lang, s: AppStrings): String {
    if (guessArc == targetArc) return guessArc
    for (arc in arcOrderFor(lang)) {
        if (arc == guessArc) return "${s.after} $guessArc"
        if (arc == targetArc) return "${s.before} $guessArc"
    }
    return guessArc
}

private fun splitTags(value: String): List<String> =
    value.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/**
 * Compare un personnage deviné au personnage à trouver, colonne par colonne,
 * en reproduisant fidèlement les règles du jeu web original (liste.txt).
 */
fun compareCharacters(target: Character, guess: Character, lang: Lang): GuessRow {
    val s = stringsFor(lang)
    val cells = mutableListOf<GuessCell>()

    // Nom
    cells += if (guess.name == target.name) {
        GuessCell(s.colPersonnage, guess.name, CellResult.MATCH)
    } else {
        GuessCell(s.colPersonnage, guess.name, CellResult.MISS)
    }

    // Type de fruit du démon
    cells += if (guess.fruitType == target.fruitType) {
        GuessCell(s.colFruit, guess.fruitType, CellResult.MATCH)
    } else {
        GuessCell(s.colFruit, guess.fruitType, CellResult.MISS)
    }

    // Haki / traits (comparaison partielle possible)
    if (guess.haki == target.haki) {
        cells += GuessCell(s.colHaki, guess.haki, CellResult.MATCH)
    } else {
        val guessTags = splitTags(guess.haki)
        val targetTags = splitTags(target.haki)
        val hasCommon = guessTags.any { it in targetTags }
        cells += GuessCell(
            s.colHaki,
            guess.haki,
            if (hasCommon) CellResult.PARTIAL else CellResult.MISS
        )
    }

    // Premier arc d'apparition
    cells += if (guess.arc == target.arc) {
        GuessCell(s.colArc, guess.arc, CellResult.MATCH)
    } else {
        GuessCell(s.colArc, arcHint(guess.arc, target.arc, lang, s), CellResult.MISS)
    }

    // Prime
    cells += if (guess.bountyValue == target.bountyValue) {
        GuessCell(s.colPrime, formatBounty(guess.bountyValue), CellResult.MATCH)
    } else {
        GuessCell(s.colPrime, bountyHint(guess.bountyValue, target.bountyValue, s), CellResult.MISS)
    }

    // Affiliation / équipage
    cells += if (guess.affiliation == target.affiliation) {
        GuessCell(s.colAffiliation, guess.affiliation, CellResult.MATCH)
    } else {
        GuessCell(s.colAffiliation, guess.affiliation, CellResult.MISS)
    }

    // Taille
    cells += if (guess.heightMeters == target.heightMeters) {
        GuessCell(s.colTaille, formatHeight(guess.heightMeters), CellResult.MATCH)
    } else {
        GuessCell(s.colTaille, heightHint(guess.heightMeters, target.heightMeters, s), CellResult.MISS)
    }

    val isWin = cells.all { it.result == CellResult.MATCH }
    return GuessRow(guess, cells, isWin)
}

/**
 * Les 3 indices du haut (arc, affiliation, fruit) avec leur texte de déblocage
 * progressif, fidèle au système "prochain indice dans N essais" du jeu original.
 */
data class TopHints(val arcHint: String, val affiliationHint: String, val fruitHint: String)

/** Seuils d'essais à partir desquels chaque indice devient débloquable (voir [canAdvanceHint]). */
private const val ARC_THRESHOLD = 4
private const val AFFILIATION_THRESHOLD = 7
private const val FRUIT_THRESHOLD = 10

/** Nombre d'essais à partir duquel la révélation du personnage (via pub) devient possible,
 * dans tous les modes sauf le Quotidien (voir [GameViewModel.canReveal]). */
const val REVEAL_THRESHOLD = 18

fun computeTopHints(target: Character, hintStage: Int, guessCount: Int, lang: Lang): TopHints {
    val s = stringsFor(lang)

    fun cellText(revealedAtStage: Int, threshold: Int, revealedValue: String): String {
        if (hintStage >= revealedAtStage) return revealedValue
        val remaining = threshold - guessCount
        return if (remaining <= 0) s.hintDisponible else s.hintTries(remaining)
    }

    val arcText = cellText(2, ARC_THRESHOLD, target.arc)
    val affText = cellText(3, AFFILIATION_THRESHOLD, target.affiliation)
    val fruitText = cellText(4, FRUIT_THRESHOLD, target.fruitName)
    return TopHints(arcText, affText, fruitText)
}

/** Chaque indice n'est débloquable (moyennant le malus, voir GameViewModel) qu'à
 * partir d'un certain nombre d'essais : le 1er à 4, le 2e à 7, le 3e à 10. */
fun canAdvanceHint(hintStage: Int, guessCount: Int): Boolean = when (hintStage) {
    1 -> guessCount >= ARC_THRESHOLD
    2 -> guessCount >= AFFILIATION_THRESHOLD
    3 -> guessCount >= FRUIT_THRESHOLD
    else -> false
}
