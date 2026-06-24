package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Composable reutilizable para mostrar errores de conexión con botón de reintentar.
 * Se usa cuando hay un error al cargar datos y necesitas ofrecerle al usuario
 * la opción de reintentar la operación.
 *
 * @param errorMessage El mensaje de error a mostrar
 * @param onRetry Función que se llama cuando el usuario presiona el botón "Reintentar"
 * @param modifier Modificador opcional para personalizar el layout
 */
@Composable
fun ErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (modifier == Modifier) 
                        Modifier else 
                        Modifier
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            // Icono de error
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Error de conexión",
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error
            )

            // Card con el mensaje de error
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = MaterialTheme.shapes.medium,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Título
                    Text(
                        text = "Error de conexión",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )

                    // Mensaje detallado
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Botón Reintentar
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "Reintentar",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Hint adicional
            Text(
                text = "Verifica tu conexión a internet e intenta nuevamente",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Composable para mostrar solo el card de error sin ocupar toda la pantalla.
 * Útil cuando tienes otros elementos en la pantalla además del error.
 *
 * @param errorMessage El mensaje de error a mostrar
 * @param onRetry Función que se llama cuando el usuario presiona el botón "Reintentar"
 * @param modifier Modificador opcional
 */
@Composable
fun ErrorCard(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Error de conexión",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )

                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Reintentar")
        }
    }
}
