package com.megatransportes.yokoh.ui.screens.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.dp
import com.megatransportes.yokoh.data.repository.YokohamaRepository
import com.megatransportes.yokoh.ui.components.Logo
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import com.megatransportes.yokoh.utils.ErrorUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    repository: YokohamaRepository,
    onLoginSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val inputScale = remember { Animatable(0f) }
    val logoAlpha by animateFloatAsState(
        targetValue = if (inputScale.value == 1f) 1f else 0f,
        animationSpec = tween(durationMillis = 1000)
    )

    LaunchedEffect(Unit) {
        inputScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000)
        )
    }

    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp) // Increased spacing between image and inputs
        ) {
            Logo(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .graphicsLayer(alpha = logoAlpha)
            )

            Column(
                modifier = Modifier.fillMaxWidth(), // Removed input animation
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo Electrónico") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                // Show open eye when password is visible, closed/slashed eye when hidden
                                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                val desc = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña"
                            Icon(imageVector = image, contentDescription = desc)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Por favor ingrese correo y contraseña"
                            return@Button
                        }

                        if (!isValidEmail(email)) {
                            errorMessage = "Ingrese un correo electrónico válido"
                            return@Button
                        }

                        coroutineScope.launch {
                            isLoading = true
                            errorMessage = null

                            repository.login(email, password)
                                .onSuccess {
                                    isLoading = false
                                    onLoginSuccess()
                                }
                                .onFailure {
                                    isLoading = false
                                    errorMessage = ErrorUtils.userMessage(it, "Error de inicio de sesión")
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("INICIAR SESIÓN")
                    }
                }
            }
        }
    }
}



// Simple, conservative email validation usable in commonMain.
fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    if (trimmed.isEmpty()) return false
    // Basic RFC-like pattern: local@domain.tld (keeps it conservative to avoid over-rejecting)
    val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()
    return emailRegex.matches(trimmed)
}
