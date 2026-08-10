package com.megatransportes.yokoh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Teclado numérico propio de la app.
 *
 * Se muestra cuando hay un teclado físico (HID) conectado (p. ej. el calibrador
 * Bluetooth), porque Android/iOS ocultan el teclado en pantalla en ese caso.
 * Al ser UI de la app, funciona idéntico en iOS y Android sin depender del IME.
 *
 * @param onKey Callback con el valor presionado: dígitos, ".", "-", "del" o "clear".
 * @param onDone Callback al confirmar (escribe el valor en el campo enfocado).
 */
@Composable
fun NumericKeypad(
    onKey: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf("7", "8", "9"),
        listOf("4", "5", "6"),
        listOf("1", "2", "3"),
        listOf(".", "0", "del")
    )

    Column(modifier = modifier.fillMaxWidth().padding(top = 8.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { key ->
                    OutlinedButton(
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f).height(56.dp)
                    ) {
                        Text(
                            text = if (key == "del") "\u232B" else key,
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(text = "Listo")
        }
    }
}