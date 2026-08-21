package com.example.onepieceapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AdsManager.init(this)
        setContent {
            MaterialTheme {
                OnePiecedleApp()
            }
        }
    }
}

@Composable
fun OnePiecedleApp(viewModel: GameViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.bg_onepiece),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
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
            if (viewModel.authState is AuthUiState.Loading || viewModel.isLoading) {
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
                StatsDialog(viewModel, PirateFont) { showStats = false }
            }

            TopBar(viewModel) { showStats = true }

            val mode = viewModel.currentMode
            val s = viewModel.strings

            if (mode == null) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        s.chooseMode,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                    ModeButtons(viewModel)
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
                    fontFamily = PirateFont,
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
                    WinBanner(s.winMessage(viewModel.guesses.first().character.name))
                    Spacer(Modifier.height(8.dp))
                } else if (viewModel.revealed) {
                    WinBanner(s.revealedMessage(viewModel.guesses.first().character.name))
                    Spacer(Modifier.height(8.dp))
                } else if (viewModel.canReveal()) {
                    Button(
                        onClick = { viewModel.requestReveal() },
                        colors = ButtonDefaults.buttonColors(containerColor = HeaderRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(s.revealButton, fontFamily = PirateFont, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (viewModel.showAdPrompt) {
                    val activity = LocalActivity.current
                    AlertDialog(
                        onDismissRequest = { if (!viewModel.watchingAd) viewModel.dismissAdPrompt() },
                        title = { Text(s.watchAdTitle, fontFamily = PirateFont) },
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

@Composable
private fun TopBar(viewModel: GameViewModel, onProfileClick: () -> Unit) {
    val s = viewModel.strings
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            s.appTitle,
            fontFamily = PirateFont,
            fontWeight = FontWeight.Black,
            fontSize = 28.sp,
            color = ParchmentGold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.shadow(4.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            LanguageToggle(viewModel)
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Filled.Person, contentDescription = s.profile, tint = Color.White)
            }
        }
    }
    if (viewModel.currentMode == null) {
        Text(
            s.subtitle,
            fontSize = 12.sp,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
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

@Composable
private fun ModeButtons(viewModel: GameViewModel) {
    val s = viewModel.strings
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton(s.modeFacile, Modifier.weight(1f)) { viewModel.startMode(GameMode.FACILE) }
            ModeButton(s.modeMoyen, Modifier.weight(1f)) { viewModel.startMode(GameMode.MOYEN) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton(s.modeDifficile, Modifier.weight(1f)) { viewModel.startMode(GameMode.DIFFICILE) }
            ModeButton(s.modeQuotidien, Modifier.weight(1f)) { viewModel.startMode(GameMode.QUOTIDIEN) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ModeButton(s.modeDieu, Modifier.weight(1f)) { viewModel.startMode(GameMode.DIEU) }
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
        Text(label, fontFamily = PirateFont, fontWeight = FontWeight.Bold)
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
                fontFamily = PirateFont,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = HeaderRed
            )
            Spacer(Modifier.height(8.dp))
            viewModel.target?.let { character ->
                Text(
                    s.dailyDoneCharacter(character.name),
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
                Text(s.hintButton, fontFamily = PirateFont, fontSize = 13.sp)
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
                    viewModel.suggestions.forEach { character ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.selectCharacter(character)
                                    focusManager.clearFocus()
                                    keyboardController?.hide()
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(character.name)
                            if (character.epithet.isNotBlank() &&
                                character.epithet != "Inconnu" &&
                                character.epithet != "Unknown"
                            ) {
                                Text(
                                    character.epithet,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun WinBanner(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MatchGreen)) {
        Text(
            message,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontFamily = PirateFont,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

private val COLUMN_WIDTH = 120.dp

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
        Row {
            labels.forEach { label ->
                Box(
                    Modifier
                        .width(COLUMN_WIDTH)
                        .background(HeaderRed)
                        .padding(6.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        guesses.forEach { row ->
            Row {
                row.cells.forEach { cell ->
                    val bg = when (cell.result) {
                        CellResult.MATCH -> MatchGreen
                        CellResult.PARTIAL -> PartialOrange
                        CellResult.MISS -> MissRed
                    }
                    Box(
                        Modifier
                            .width(COLUMN_WIDTH)
                            .background(bg)
                            .padding(6.dp)
                    ) {
                        Text(cell.value, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
