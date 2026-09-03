package com.example.onepieceapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sélecteur d'avatar : laisse le joueur choisir, comme photo de profil, un
 * personnage/champion du jeu disposant d'une image -- peu importe l'univers
 * actuellement actif (onglets One Piece / League of Legends), puisque
 * l'avatar est un attribut du profil partagé (voir UserStats.avatarXxx),
 * pas d'un univers en particulier.
 */
@Composable
fun AvatarPickerDialog(viewModel: GameViewModel, pirateFont: FontFamily, onDismiss: () -> Unit) {
    val s = viewModel.strings
    LaunchedEffect(Unit) { viewModel.openAvatarPicker() }

    var tab by remember { mutableStateOf(Universe.ONE_PIECE) }
    var query by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ParchmentCard,
        titleContentColor = HeaderRed,
        title = {
            Column {
                Text(s.chooseAvatarTitle, fontFamily = pirateFont, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                TabRow(
                    selectedTabIndex = if (tab == Universe.ONE_PIECE) 0 else 1,
                    containerColor = Color.Transparent,
                    contentColor = HeaderRed
                ) {
                    Tab(
                        selected = tab == Universe.ONE_PIECE,
                        onClick = { tab = Universe.ONE_PIECE },
                        text = { Text(s.universeOnePieceName, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = tab == Universe.LEAGUE_OF_LEGENDS,
                        onClick = { tab = Universe.LEAGUE_OF_LEGENDS },
                        text = { Text(s.universeLolName, fontSize = 12.sp) }
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text(s.avatarSearchHint) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        text = {
            Box(Modifier.heightIn(min = 200.dp, max = 360.dp)) {
                if (viewModel.avatarPickerLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HeaderRed)
                    }
                } else {
                    val entries = viewModel.avatarPickerEntries[tab].orEmpty().filter { entry ->
                        query.isBlank() || entry.name.contains(query, ignoreCase = true)
                    }
                    if (entries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(s.avatarNoResults, color = Color(0xFF8A8A8A))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(entries) { entry ->
                                Column(
                                    modifier = Modifier
                                        .clickable {
                                            viewModel.setAvatar(tab, entry)
                                            onDismiss()
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier.background(
                                            Color(0x11000000),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    ) {
                                        EntryImage(entry, 64.dp)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        entry.name,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        color = Color(0xFF2B2B2B),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = HeaderRed)
            ) { Text(s.close, fontFamily = pirateFont) }
        }
    )
}
