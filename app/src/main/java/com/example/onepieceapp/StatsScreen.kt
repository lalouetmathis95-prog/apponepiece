package com.example.onepieceapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatsDialog(viewModel: GameViewModel, pirateFont: FontFamily, onDismiss: () -> Unit) {
    val s = viewModel.strings
    var tab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ParchmentCard,
        titleContentColor = HeaderRed,
        textContentColor = Color(0xFF2B2B2B),
        title = {
            Column {
                Text(s.statsTitle, fontFamily = pirateFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(10.dp))
                TabRow(
                    selectedTabIndex = tab,
                    containerColor = Color.Transparent,
                    contentColor = HeaderRed
                ) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(s.profileTab, fontFamily = pirateFont, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1; viewModel.refreshFriends() },
                        text = { Text(s.friendsTab, fontFamily = pirateFont, fontSize = 13.sp) }
                    )
                }
            }
        },
        text = {
            Box(Modifier.heightIn(max = 420.dp)) {
                if (tab == 0) ProfileTab(viewModel, pirateFont) else FriendsTab(viewModel, pirateFont)
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = HeaderRed)
            ) { Text(s.close, fontFamily = pirateFont) }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.signOut()
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8A8A8A))
            ) { Text(s.signOut, fontFamily = pirateFont) }
        }
    )
}

@Composable
private fun SectionTitle(text: String, pirateFont: FontFamily, modifier: Modifier = Modifier) {
    Text(
        text,
        fontFamily = pirateFont,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = HeaderRed,
        modifier = modifier
    )
}

@Composable
private fun ProfileTab(viewModel: GameViewModel, pirateFont: FontFamily) {
    val s = viewModel.strings
    val stats = viewModel.userStats

    Column {
        if (stats.username.isNotBlank()) {
            Text(
                stats.username,
                fontFamily = pirateFont,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = HeaderRed,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        } else {
            SetUsernameForm(viewModel, pirateFont)
            Spacer(Modifier.height(12.dp))
        }
        StatRow(s.gamesPlayed, stats.gamesPlayed.toString())
        StatRow(s.gamesWon, stats.gamesWon.toString())
        StatRow(s.winRate, "${stats.winRatePercent}%")
        StatRow(s.currentStreak, stats.currentStreak.toString())
        StatRow(s.maxStreak, stats.maxStreak.toString())
        StatRow(s.averageGuesses, "%.1f".format(stats.averageGuesses))
    }
}

@Composable
private fun SetUsernameForm(viewModel: GameViewModel, pirateFont: FontFamily) {
    val s = viewModel.strings
    var username by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            s.chooseUsernamePrompt,
            fontSize = 13.sp,
            color = Color(0xFF5A5A5A),
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; error = null },
                label = { Text(s.usernameLabel) },
                singleLine = true,
                isError = error != null,
                colors = themedFieldColors(),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = username.isNotBlank() && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = HeaderRed),
                onClick = {
                    busy = true
                    viewModel.setUsername(username) { success, err ->
                        busy = false
                        if (!success) error = err
                    }
                }
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                } else {
                    Text(s.saveUsername, fontFamily = pirateFont)
                }
            }
        }
        error?.let {
            Text(it, fontSize = 12.sp, color = Color(0xFFB00020), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun themedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = HeaderRed,
    focusedLabelColor = HeaderRed,
    cursorColor = HeaderRed
)

@Composable
private fun FriendsTab(viewModel: GameViewModel, pirateFont: FontFamily) {
    val s = viewModel.strings
    var addFriendQuery by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            SectionTitle(s.addFriend, pirateFont, Modifier.padding(top = 4.dp, bottom = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = addFriendQuery,
                    onValueChange = { addFriendQuery = it; feedback = null },
                    label = { Text(s.addFriendHint) },
                    singleLine = true,
                    colors = themedFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = addFriendQuery.isNotBlank() && !viewModel.friendsBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = HeaderRed),
                    onClick = {
                        val query = addFriendQuery
                        viewModel.sendFriendRequest(query) { success, error ->
                            feedback = if (success) s.friendRequestSent else error
                            if (success) addFriendQuery = ""
                        }
                    }
                ) { Text(s.sendRequest, fontFamily = pirateFont) }
            }
            feedback?.let {
                Text(it, fontSize = 12.sp, color = HeaderRed, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            SectionTitle(s.incomingRequests, pirateFont, Modifier.padding(bottom = 4.dp))
        }
        if (viewModel.incomingRequests.isEmpty()) {
            item { Text(s.noIncomingRequests, fontSize = 13.sp, color = Color(0xFF8A8A8A)) }
        } else {
            items(viewModel.incomingRequests) { req ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(req.fromUsername, fontSize = 14.sp, color = Color(0xFF2B2B2B))
                    Row {
                        TextButton(
                            onClick = { viewModel.acceptRequest(req) },
                            colors = ButtonDefaults.textButtonColors(contentColor = HeaderRed)
                        ) { Text(s.accept) }
                        TextButton(
                            onClick = { viewModel.declineRequest(req) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8A8A8A))
                        ) { Text(s.decline) }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        item {
            SectionTitle(s.myFriends, pirateFont, Modifier.padding(bottom = 4.dp))
        }
        if (viewModel.friends.isEmpty()) {
            item { Text(s.noFriends, fontSize = 13.sp, color = Color(0xFF8A8A8A)) }
        } else {
            items(viewModel.friends) { friend ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(friend.username, fontSize = 14.sp, color = Color(0xFF2B2B2B))
                    TextButton(
                        onClick = { viewModel.removeFriend(friend) },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF8A8A8A))
                    ) { Text(s.removeFriend) }
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }

        item {
            SectionTitle(s.leaderboardTitle, pirateFont, Modifier.padding(bottom = 4.dp))
        }
        items(viewModel.leaderboard.withIndex().toList()) { (index, entry) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}.", modifier = Modifier.padding(end = 6.dp), color = Color(0xFF8A8A8A))
                    if (entry.isMe) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = ParchmentGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        if (entry.isMe) "${entry.username} (${s.you})" else entry.username,
                        fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp,
                        color = if (entry.isMe) HeaderRed else Color(0xFF2B2B2B)
                    )
                }
                Text("${entry.gamesWon} · ${entry.maxStreak}🔥", fontSize = 13.sp, color = Color(0xFF2B2B2B))
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF2B2B2B))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = HeaderRed)
    }
}
