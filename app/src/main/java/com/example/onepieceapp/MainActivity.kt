package com.example.onepieceapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

// Pas de "private" ici : ces couleurs forment la charte graphique de l'appli et
// sont réutilisées ailleurs (ex. StatsScreen.kt) pour que le profil/les amis
// aient le même rendu "carte au trésor" que le reste de l'appli.
val MatchGreen = Color(0xFF3EAE4A)
val PartialOrange = Color(0xFFE8912D)
val MissRed = Color(0xFFD9534F)
val HeaderRed = Color(0xFF7A1F1F)
val ParchmentGold = Color(0xFFE8C468)
val ParchmentCard = Color(0xE6FFF8E1)
val ScrimTop = Color(0xCC0B1F33)
val ScrimBottom = Color(0xE60B1F33)

// Police "One Piece" (fontspace.com), embarquée dans res/font.
private val PirateFont = FontFamily(Font(R.font.onepiece_font))

// Police "Optimus Princeps" pour l'univers League of Legends (fournie par
// l'utilisateur, embarquée dans res/font).
private val LolFont = FontFamily(
    Font(R.font.optimus_princeps),
    Font(R.font.optimus_princeps_semibold, FontWeight.SemiBold)
)

// Police "Remington Ragged" (Tension Type) pour l'écran de démarrage (avant
// le choix d'univers), fournie par l'utilisateur, embarquée dans res/font.
private val NeutralFont = FontFamily(Font(R.font.remington_ragged))

// Applique une police à TOUS les styles du thème Material : sans ça, seuls les
// Text() qui passent explicitement une fontFamily l'utilisaient, et tout le
// reste (stats, indices, suggestions de recherche, etc.) retombait sur la
// police par défaut de Compose. Chaque Text() hérite maintenant de la police
// passée via LocalTextStyle, sauf s'il précise lui-même une autre fontFamily.
private fun typographyWith(fontFamily: FontFamily): Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily)
    )
}

private val PirateTypography = typographyWith(PirateFont)
private val LolTypography = typographyWith(LolFont)
private val NeutralTypography = typographyWith(NeutralFont)

/** Police "titre" active selon l'univers en cours -- null (pas encore
 * d'univers choisi) veut dire l'écran de démarrage, avec la police neutre. */
private fun fontForUniverse(universe: Universe?): FontFamily = when (universe) {
    Universe.ONE_PIECE -> PirateFont
    Universe.LEAGUE_OF_LEGENDS -> LolFont
    null -> NeutralFont
}

private fun typographyForUniverse(universe: Universe?): Typography = when (universe) {
    Universe.ONE_PIECE -> PirateTypography
    Universe.LEAGUE_OF_LEGENDS -> LolTypography
    null -> NeutralTypography
}

/** Image de fond selon l'univers -- null pour l'écran de démarrage, qui
 * affiche un dégradé neutre plutôt qu'une image d'univers. */
private fun backgroundForUniverse(universe: Universe?): Int? = when (universe) {
    Universe.ONE_PIECE -> R.drawable.bg_onepiece
    Universe.LEAGUE_OF_LEGENDS -> R.drawable.bg_lol
    null -> null
}

/** Permet aux composables définis séparément (TopBar, cartes de mode, etc.)
 * d'accéder à la police "titre" de l'univers en cours sans avoir à la
 * recevoir en paramètre explicite. */
private val LocalTitleFont = compositionLocalOf { PirateFont }

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* accepté ou non, rien de plus à faire ici */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdsManager.init(this)

        // Rappel quotidien pour jouer le mode Quotidien (voir NotificationScheduler/Worker).
        NotificationHelper.createChannel(this)
        NotificationScheduler.schedule(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            OnePiecedleApp()
        }
    }
}

@Composable
fun OnePiecedleApp(viewModel: GameViewModel = viewModel()) {
    val universe = viewModel.universe
    val activeFont = fontForUniverse(universe)

    MaterialTheme(typography = typographyForUniverse(universe)) {
    CompositionLocalProvider(LocalTitleFont provides activeFont) {
    Box(Modifier.fillMaxSize()) {
        val backgroundRes = backgroundForUniverse(universe)
        if (backgroundRes != null) {
            Image(
                painter = painterResource(id = backgroundRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Écran de démarrage (avant choix d'univers) : dégradé neutre et
            // moderne plutôt que l'image d'un univers en particulier.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color(0xFF1B1F2A), Color(0xFF3A4152))
                        )
                    )
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(ScrimTop, ScrimBottom)
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (viewModel.authState is AuthUiState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ParchmentGold)
                }
                return@Column
            }

            if (viewModel.authState is AuthUiState.LoggedOut) {
                AuthScreen(viewModel, PirateFont)
                return@Column
            }

            var showStats by remember { mutableStateOf(false) }
            if (showStats) {
                StatsDialog(viewModel, activeFont) { showStats = false }
            }
            var showRules by remember { mutableStateOf(false) }
            if (showRules) {
                RulesDialog(viewModel, activeFont) { showRules = false }
            }

            val s = viewModel.strings

            if (universe == null) {
                TopBarUniverseOnly(viewModel, onProfileClick = { showStats = true }, onRulesClick = { showRules = true })
                UniverseSelectionScreen(s, activeFont) { viewModel.selectUniverse(it) }
                return@Column
            }

            if (viewModel.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ParchmentGold)
                }
                return@Column
            }

            TopBar(
                viewModel,
                onProfileClick = { showStats = true },
                onRulesClick = { showRules = true },
                onChangeUniverseClick = { viewModel.backToUniverseSelection() }
            )

            val mode = viewModel.currentMode

            // Retour arrière : au lieu de fermer l'appli directement, on
            // remonte d'un cran dans la navigation interne -- partie en cours
            // -> choix du mode -> choix de l'univers (l'accueil, voir plus
            // bas) -> et seulement là, le comportement système par défaut
            // (fermer l'appli) reprend la main.
            BackHandler(enabled = mode != null) { viewModel.backToModeSelection() }
            BackHandler(enabled = mode == null) { viewModel.backToUniverseSelection() }

            if (mode == null) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        s.chooseMode,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    DailyModeCard(viewModel)
                    Spacer(Modifier.height(24.dp))
                    ClassicModeSection(viewModel)
                }
                return@Column
            }

            // Bandeau mode actuel + bouton retour (le choix du mode ne reste
            // plus affiché pendant la partie, comme demandé).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    mode.label(s),
                    color = ParchmentGold,
                    fontFamily = activeFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                TextButton(onClick = { viewModel.backToModeSelection() }) {
                    Text("↩ ${s.changeMode}", color = Color.White)
                }
            }

            if (mode == GameMode.QUOTIDIEN && viewModel.dailyLocked) {
                DailyLockedCard(viewModel)
            } else {
                TopHintsCard(viewModel)

                Spacer(Modifier.height(8.dp))

                SearchBar(viewModel)

                Spacer(Modifier.height(8.dp))

                if (viewModel.won) {
                    val winner = viewModel.guesses.first().entry
                    WinBanner(winner, s.winMessage(winner.name))
                    Spacer(Modifier.height(8.dp))
                } else if (viewModel.revealed) {
                    val revealedEntry = viewModel.guesses.first().entry
                    WinBanner(revealedEntry, s.revealedMessage(revealedEntry.name))
                    Spacer(Modifier.height(8.dp))
                } else if (viewModel.canReveal()) {
                    Button(
                        onClick = { viewModel.requestReveal() },
                        colors = ButtonDefaults.buttonColors(containerColor = HeaderRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(s.revealButton, fontFamily = activeFont, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (viewModel.showAdPrompt) {
                    val activity = LocalActivity.current
                    AlertDialog(
                        onDismissRequest = { if (!viewModel.watchingAd) viewModel.dismissAdPrompt() },
                        title = { Text(s.watchAdTitle, fontFamily = activeFont) },
                        text = {
                            if (viewModel.watchingAd) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = HeaderRed)
                                    Spacer(Modifier.width(12.dp))
                                    Text(s.watchingAdMessage)
                                }
                            } else {
                                Text(s.watchAdMessage)
                            }
                        },
                        confirmButton = {
                            if (!viewModel.watchingAd) {
                                TextButton(onClick = {
                                    viewModel.beginWatchingAd()
                                    if (activity != null) {
                                        AdsManager.show(
                                            activity,
                                            onReward = { viewModel.onAdRewardEarned() },
                                            onNoRewardOrUnavailable = { viewModel.onAdNotRewarded() }
                                        )
                                    } else {
                                        viewModel.onAdNotRewarded()
                                    }
                                }) {
                                    Text(s.watchAdConfirm)
                                }
                            }
                        },
                        dismissButton = {
                            if (!viewModel.watchingAd) {
                                TextButton(onClick = { viewModel.dismissAdPrompt() }) {
                                    Text(s.watchAdCancel)
                                }
                            }
                        }
                    )
                }

                if (mode == GameMode.QUOTIDIEN) {
                    Text(
                        "${s.yesterdayCharacter} : ${viewModel.previousDailyCharacterName ?: "-"}",
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    val aggregate = viewModel.dailyAggregate
                    if (aggregate != null) {
                        Text(
                            s.dailyGlobalSolved(aggregate.solvedCount, aggregate.averageGuesses),
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                Text("${s.essais} : ${viewModel.guessCount}", color = Color.White, fontWeight = FontWeight.SemiBold)

                Spacer(Modifier.height(6.dp))

                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (viewModel.modeStarting || viewModel.target == null) {
                        CircularProgressIndicator(color = ParchmentGold)
                    } else {
                        GuessTable(viewModel.guesses)
                    }
                }
            }
        }
    }
    }
    }
}

/** Portrait d'une entrée (personnage ou champion), chargé depuis les assets
 * embarqués ("images/" pour One Piece, "images_lol/" pour League of
 * Legends). N'affiche rien si aucune image n'a été trouvée pour cette entrée.
 * Pas "private" : réutilisé depuis AvatarPicker.kt (sélecteur d'avatar). */
@Composable
fun EntryImage(entry: Guessable, size: Dp, modifier: Modifier = Modifier) {
    val file = entry.imageFile ?: return
    AsyncImage(
        model = "file:///android_asset/${entry.imageFolder}/$file",
        contentDescription = entry.name,
        contentScale = ContentScale.Crop,
        // Alignement en haut plutôt qu'au centre : beaucoup de portraits ont
        // la tête tout en haut du cadre, un crop carré centré la coupait donc
        // souvent. Ancrer le crop en haut garde le visage visible.
        alignment = Alignment.TopCenter,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
    )
}

/** Avatar de profil rond, affiché depuis les champs bruts (dossier/fichier)
 * sauvegardés sur le profil (voir UserStats.avatarImageFolder/avatarImageFile)
 * plutôt que depuis un [Guessable] complet -- l'avatar peut appartenir à un
 * univers différent de celui actuellement chargé, donc on ne dispose pas
 * forcément de l'objet Guessable correspondant. Affiche un "?" de repli tant
 * qu'aucun avatar n'a été choisi. */
@Composable
fun AvatarThumbnail(imageFolder: String?, imageFile: String?, size: Dp, modifier: Modifier = Modifier) {
    if (imageFolder != null && imageFile != null) {
        AsyncImage(
            model = "file:///android_asset/$imageFolder/$imageFile",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFFDDDDDD)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "?",
                fontSize = (size.value / 2.2f).sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8A8A8A)
            )
        }
    }
}

/** Barre du haut affichée même avant le choix d'univers (juste le profil, pas
 * de titre d'univers puisqu'aucun n'est encore choisi). */
@Composable
private fun TopBarUniverseOnly(viewModel: GameViewModel, onProfileClick: () -> Unit, onRulesClick: () -> Unit) {
    val s = viewModel.strings
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            s.appTitle,
            fontFamily = LocalTitleFont.current,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            color = ParchmentGold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.shadow(4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRulesClick) {
                Text("📜", fontSize = 18.sp)
            }
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Filled.Person, contentDescription = s.profile, tint = Color.White)
            }
        }
    }
}

@Composable
private fun TopBar(
    viewModel: GameViewModel,
    onProfileClick: () -> Unit,
    onRulesClick: () -> Unit,
    onChangeUniverseClick: () -> Unit
) {
    val s = viewModel.strings
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            titleFor(s, viewModel.universe),
            fontFamily = LocalTitleFont.current,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            color = ParchmentGold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.shadow(4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StreakBadge(viewModel)
            Spacer(Modifier.width(4.dp))
            LanguageToggle(viewModel)
            IconButton(onClick = onRulesClick) {
                Text("📜", fontSize = 18.sp)
            }
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Filled.Person, contentDescription = s.profile, tint = Color.White)
            }
        }
    }
    if (viewModel.currentMode == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "📜 ${s.rulesTitle}",
                fontSize = 12.sp,
                color = Color.White,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 4.dp)
                    .clickable { onRulesClick() }
            )
            Text(
                "  ·  ${s.changeUniverseLink}",
                fontSize = 12.sp,
                color = Color.White,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = 4.dp, bottom = 4.dp)
                    .clickable { onChangeUniverseClick() }
            )
        }
    }
}

/** Petit indicateur toujours visible de la série de jours consécutifs au Quotidien
 * (de l'univers en cours). */
@Composable
private fun StreakBadge(viewModel: GameViewModel) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color(0x33000000), shape = MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text("🔥", fontSize = 13.sp)
        Spacer(Modifier.width(3.dp))
        Text("${viewModel.userStats.currentStreak}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LanguageToggle(viewModel: GameViewModel) {
    Row(
        modifier = Modifier
            .background(Color(0x33000000), shape = MaterialTheme.shapes.small)
            .padding(2.dp)
    ) {
        LangButton("FR", viewModel.lang == Lang.FR) { viewModel.selectLanguage(Lang.FR) }
        LangButton("EN", viewModel.lang == Lang.EN) { viewModel.selectLanguage(Lang.EN) }
    }
}

@Composable
private fun LangButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) HeaderRed else Color.Transparent
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .background(bg, shape = MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Carte dédiée au mode Quotidien, bien séparée du Classique : un seul
 * personnage/champion par jour, mise en avant avec la série en cours. Pas de
 * photo ici avant d'avoir joué : ce serait révéler la réponse du jour à
 * l'avance (voir [DailyLockedCard] pour la photo, une fois déjà joué). */
@Composable
private fun DailyModeCard(viewModel: GameViewModel) {
    val s = viewModel.strings
    val alreadyPlayed = viewModel.dailyAlreadyPlayedToday
    Card(
        colors = CardDefaults.cardColors(containerColor = ParchmentCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable { viewModel.startMode(GameMode.QUOTIDIEN) }
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(s.modeQuotidien, fontFamily = LocalTitleFont.current, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HeaderRed)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (alreadyPlayed) s.dailyDoneTitle else s.dailyModeHint,
                    fontSize = 12.sp,
                    color = Color.DarkGray
                )
            }
            if (viewModel.userStats.currentStreak > 0) {
                Text(
                    "🔥 ${viewModel.userStats.currentStreak}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HeaderRed
                )
            }
        }
    }
}

/** Les 4 modes de difficulté classiques, regroupés à part du Quotidien. */
@Composable
private fun ClassicModeSection(viewModel: GameViewModel) {
    val s = viewModel.strings
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(
            s.classicModeTitle,
            color = Color.White,
            fontFamily = LocalTitleFont.current,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeButton(s.modeFacile, Modifier.weight(1f)) { viewModel.startMode(GameMode.FACILE) }
                ModeButton(s.modeMoyen, Modifier.weight(1f)) { viewModel.startMode(GameMode.MOYEN) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ModeButton(s.modeDifficile, Modifier.weight(1f)) { viewModel.startMode(GameMode.DIFFICILE) }
                ModeButton(s.modeDieu, Modifier.weight(1f)) { viewModel.startMode(GameMode.DIEU) }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = HeaderRed)
    ) {
        Text(label, fontFamily = LocalTitleFont.current, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DailyLockedCard(viewModel: GameViewModel) {
    val s = viewModel.strings
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xE6FFF8E1))) {
        Column(
            Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                s.dailyDoneTitle,
                fontFamily = LocalTitleFont.current,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = HeaderRed
            )
            Spacer(Modifier.height(10.dp))
            viewModel.target?.let { entry ->
                EntryImage(entry, 96.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    s.dailyDoneCharacter(entry.name),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
                Spacer(Modifier.height(8.dp))
            }
            viewModel.dailyAggregate?.let { aggregate ->
                Text(
                    s.dailyGlobalSolved(aggregate.solvedCount, aggregate.averageGuesses),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Black
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                s.comeBackTomorrow,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = Color.DarkGray
            )
        }
    }
}

@Composable
private fun TopHintsCard(viewModel: GameViewModel) {
    val hints = viewModel.topHints() ?: return
    val s = viewModel.strings
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xE6FFF8E1))) {
        Column(Modifier.padding(10.dp)) {
            Row(Modifier.fillMaxWidth()) {
                HintColumn(s.colArc, hints.arcHint, Modifier.weight(1f))
                HintColumn(s.colAffiliation, hints.affiliationHint, Modifier.weight(1f))
                HintColumn(s.colFruit, hints.fruitHint, Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { viewModel.requestHint() },
                enabled = canAdvanceHint(viewModel.hintStage, viewModel.guessCount),
                colors = ButtonDefaults.buttonColors(containerColor = HeaderRed),
                modifier = Modifier.height(36.dp)
            ) {
                Text(s.hintButton, fontFamily = LocalTitleFont.current, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HintColumn(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = HeaderRed)
        Text(value, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color.Black)
    }
}

@Composable
private fun SearchBar(viewModel: GameViewModel) {
    val s = viewModel.strings
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = viewModel.query,
            onValueChange = { viewModel.onQueryChange(it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(s.searchLabelDuringGame) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xF2FFFFFF),
                unfocusedContainerColor = Color(0xF2FFFFFF)
            ),
            trailingIcon = {
                if (viewModel.query.isNotEmpty()) {
                    IconButton(onClick = {
                        viewModel.onQueryChange("")
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )

        if (viewModel.suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp)
                    .heightIn(max = 240.dp)
                    .zIndex(10f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFAFFFFFF))
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    viewModel.suggestions.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCharacter(entry)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            EntryImage(entry, 32.dp)
                            if (entry.imageFile != null) Spacer(Modifier.width(10.dp))
                            Column {
                                Text(entry.name)
                                if (entry.subtitle.isNotBlank() &&
                                    entry.subtitle != "Inconnu" &&
                                    entry.subtitle != "Unknown"
                                ) {
                                    Text(
                                        entry.subtitle,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** Bandeau affiché une fois le personnage/champion trouvé (ou révélé) : la
 * photo est mise en grand ici (contrairement aux vignettes du tableau ou de
 * la recherche) pour bien "révéler" la réponse. */
@Composable
private fun WinBanner(entry: Guessable, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MatchGreen)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (entry.imageFile != null) {
                EntryImage(entry, 160.dp)
                Spacer(Modifier.height(12.dp))
            }
            Text(
                message,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontFamily = LocalTitleFont.current,
                textAlign = TextAlign.Center
            )
        }
    }
}

private val COLUMN_WIDTH = 120.dp
private val PHOTO_COLUMN_WIDTH = 70.dp

// Hauteur fixe pour TOUTES les lignes (en-tête comme lignes de résultat) : sans
// ça, une cellule dont le texte passe sur 2 lignes (ex. un rôle "Combattant,
// Tank") agrandissait sa Row entière tandis que la colonne photo, elle,
// gardait la hauteur de l'image -- ce qui donnait des lignes de tailles
// différentes et une photo mal centrée verticalement. En fixant la hauteur et
// en limitant le texte à 2 lignes avec ellipsis, chaque ligne a maintenant
// exactement la même taille.
private val ROW_HEIGHT = 56.dp

@Composable
private fun GuessTable(guesses: List<GuessRow>) {
    if (guesses.isEmpty()) return
    val labels = guesses.first().cells.map { it.label }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        Row(Modifier.height(ROW_HEIGHT)) {
            Box(
                Modifier
                    .width(PHOTO_COLUMN_WIDTH)
                    .fillMaxHeight()
                    .background(HeaderRed)
                    .padding(6.dp)
            ) {
                Text("", color = Color.White, fontSize = 12.sp)
            }
            labels.forEach { label ->
                Box(
                    Modifier
                        .width(COLUMN_WIDTH)
                        .fillMaxHeight()
                        .background(HeaderRed)
                        .padding(6.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        label,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        guesses.forEach { row ->
            Row(Modifier.height(ROW_HEIGHT)) {
                Box(
                    Modifier
                        .width(PHOTO_COLUMN_WIDTH)
                        .fillMaxHeight()
                        .background(Color(0xFF241010)),
                    contentAlignment = Alignment.Center
                ) {
                    EntryImage(row.entry, ROW_HEIGHT - 8.dp)
                }
                row.cells.forEach { cell ->
                    val bg = when (cell.result) {
                        CellResult.MATCH -> MatchGreen
                        CellResult.PARTIAL -> PartialOrange
                        CellResult.MISS -> MissRed
                    }
                    Box(
                        Modifier
                            .width(COLUMN_WIDTH)
                            .fillMaxHeight()
                            .background(bg)
                            .padding(6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            cell.value,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
