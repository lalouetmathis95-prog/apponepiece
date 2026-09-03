package com.example.onepieceapp

/**
 * Une entité devinable dans un mode de jeu InfiniteDle, quel que soit
 * l'univers (personnage One Piece ou champion League of Legends). Le moteur
 * de jeu commun (GameViewModel, la recherche, le tableau de résultats...)
 * ne connaît que cette interface ; seule la comparaison ligne par ligne
 * (voir GameLogic.kt / LolGameLogic.kt) a besoin des champs spécifiques à
 * chaque univers (Character / Champion).
 */
sealed interface Guessable {
    val name: String
    /** Sous-titre affiché sous le nom dans les suggestions de recherche
     * (épithète pour One Piece, titre pour League of Legends). */
    val subtitle: String
    /** Nom de fichier image (dans le dossier [imageFolder] des assets), ou
     * null si aucune image n'a été trouvée pour cette entrée. */
    val imageFile: String?
    /** Dossier des assets contenant les images de cet univers ("images" ou
     * "images_lol"), pour construire le chemin complet sans avoir à
     * re-vérifier l'univers dans l'UI. */
    val imageFolder: String
    /** Score de notoriété/difficulté officielle propre à chaque univers
     * (0-8 pour One Piece, 1-10 pour League of Legends) -- les seuils par
     * mode sont définis séparément par univers dans GameViewModel, mais le
     * filtrage lui-même (poolFor, dailyEntryPositions) est commun. */
    val difficulty: Int
}

/** Ne garde que la moitié (FR ou EN) d'une liste [name] où les deux langues
 * sont concaténées dans le même ordre (première moitié FR, seconde EN) --
 * convention partagée par characters.json et champions.json. */
fun <T> entriesForLang(all: List<T>, lang: Lang): List<T> {
    val mid = all.size / 2
    return if (lang == Lang.FR) all.subList(0, mid) else all.subList(mid, all.size)
}

fun <T> entryAtPosition(all: List<T>, lang: Lang, position: Int): T {
    val mid = all.size / 2
    return if (lang == Lang.FR) all[position] else all[mid + position]
}

/**
 * Positions (dans la moitié de langue, 0 jusqu'à mid) éligibles au mode
 * Quotidien : mêmes seuils que le mode Moyen (voir [GameViewModel.poolFor]),
 * avec le même filet de sécurité "au moins une image" que Facile/Moyen.
 */
fun dailyEntryPositions(all: List<Guessable>, moyenThreshold: Int): List<Int> {
    val mid = all.size / 2
    val positions = (0 until mid).filter { all[it].difficulty >= moyenThreshold }
    val withImage = positions.filter { all[it].imageFile != null }
    return withImage.ifEmpty { positions }
}
