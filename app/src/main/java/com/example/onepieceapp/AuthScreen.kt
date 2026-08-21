package com.example.onepieceapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(viewModel: GameViewModel, pirateFont: FontFamily) {
    val s = viewModel.strings
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var checkingUsername by remember { mutableStateOf(false) }

    LaunchedEffect(username, isSignUp) {
        if (!isSignUp || username.isBlank()) {
            usernameAvailable = null
        } else {
            checkingUsername = true
            delay(400) // anti-rebond : on évite une requête à chaque frappe
            usernameAvailable = viewModel.isUsernameAvailable(username)
            checkingUsername = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            s.appTitle,
            fontFamily = pirateFont,
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            color = Color(0xFFE8C468)
        )
        Spacer(Modifier.height(24.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xF2FFFFFF))) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(
                    s.authTitle,
                    fontFamily = pirateFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isSignUp) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(s.usernameLabel) },
                        singleLine = true,
                        isError = usernameAvailable == false,
                        supportingText = {
                            when {
                                checkingUsername -> Text("…")
                                usernameAvailable == false -> Text(s.usernameTaken, color = Color(0xFFB00020))
                                usernameAvailable == true -> Text(s.usernameAvailable, color = Color(0xFF2E7D32))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(s.emailLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(s.passwordLabel) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                viewModel.authError?.let { err ->
                    Text(err, color = Color(0xFFB00020), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (isSignUp) viewModel.signUp(email, password, username) else viewModel.signIn(email, password)
                    },
                    enabled = !viewModel.authBusy && (!isSignUp || usernameAvailable == true),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7A1F1F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (viewModel.authBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    } else {
                        Text(if (isSignUp) s.signUpButton else s.loginButton, fontFamily = pirateFont)
                    }
                }

                TextButton(
                    onClick = { isSignUp = !isSignUp },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isSignUp) s.switchToLogin else s.switchToSignUp)
                }
            }
        }
    }
}
