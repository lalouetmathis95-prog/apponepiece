package com.example.onepieceapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Un univers jouable dans InfiniteDle. Chaque univers a son propre jeu de
 * données (personnages/champions), ses propres statistiques/streak/classement
 * (voir StatsRepository/DailyRepository), mais partage le même moteur de jeu
 * (recherche, tableau de résultats, Quotidien...). */
enum class Universe { ONE_PIECE, LEAGUE_OF_LEGENDS }

/** Premier écran de l'appli une fois connecté : on choisit l'univers avant
 * même le mode de jeu. */
@Composable
fun UniverseSelectionScreen(strings: AppStrings, pirateFont: FontFamily, onSelect: (Universe) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            strings.chooseUniverseTitle,
            color = Color.White,
            fontFamily = pirateFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        UniverseCard(
            title = strings.universeOnePieceName,
            subtitle = strings.universeOnePieceSubtitle,
            emoji = "🏴‍☠️",
            pirateFont = pirateFont,
            onClick = { onSelect(Universe.ONE_PIECE) }
        )
        Spacer(Modifier.height(16.dp))
        UniverseCard(
            title = strings.universeLolName,
            subtitle = strings.universeLolSubtitle,
            emoji = "⚔️",
            pirateFont = pirateFont,
            onClick = { onSelect(Universe.LEAGUE_OF_LEGENDS) }
        )
    }
}

@Composable
private fun UniverseCard(
    title: String,
    subtitle: String,
    emoji: String,
    pirateFont: FontFamily,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xE6FFF8E1)),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clickable { onClick() }
    ) {
        Row(
            Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, fontFamily = pirateFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = HeaderRed)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = Color.DarkGray)
            }
        }
    }
}
