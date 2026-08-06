package com.techilyfly.tfplans.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.techilyfly.tfplans.ui.theme.PrimaryColor
import com.techilyfly.tfplans.ui.theme.SecondaryColor

@Composable
fun InternetRequiredDialog(
    message: String = "An active internet connection is required to perform this action. Please check your connection and try again.",
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Internet Required", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = PrimaryColor) },
        text = { Text(message, color = SecondaryColor, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Text("OK", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large
    )
}
