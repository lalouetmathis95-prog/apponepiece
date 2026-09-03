package com.example.onepieceapp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RulesDialog(viewModel: GameViewModel, pirateFont: FontFamily, onDismiss: () -> Unit) {
    val s = viewModel.strings
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ParchmentCard,
        titleContentColor = HeaderRed,
        textContentColor = Color(0xFF2B2B2B),
        title = { Text(s.rulesTitle, fontFamily = pirateFont, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                RuleSection(s.rulesColorsTitle, s.rulesColorsBody, pirateFont)
                Spacer(Modifier.height(14.dp))
                RuleSection(s.rulesHintsTitle, s.rulesHintsBody, pirateFont)
                Spacer(Modifier.height(14.dp))
                RuleSection(s.rulesModesTitle, s.rulesModesBody, pirateFont)
                Spacer(Modifier.height(14.dp))
                RuleSection(s.rulesDailyTitle, s.rulesDailyBody, pirateFont)
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

@Composable
private fun RuleSection(title: String, body: String, pirateFont: FontFamily) {
    Text(title, fontFamily = pirateFont, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = HeaderRed)
    Spacer(Modifier.height(4.dp))
    Text(body, fontSize = 13.sp)
}
