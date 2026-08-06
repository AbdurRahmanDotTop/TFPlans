package com.techilyfly.tfplans.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import com.techilyfly.tfplans.ui.theme.PrimaryColor
import com.techilyfly.tfplans.ui.theme.SecondaryColor
import com.techilyfly.tfplans.ui.theme.BackgroundColor

val PredefinedColors = listOf(
    Color(0xFFFFFFFF), // White
    Color(0xFFF2F2F2), // Gray
    Color(0xFFFFF475), // Yellow
    Color(0xFFF28B82), // Red
    Color(0xFFFDCFE8), // Pink
    Color(0xFFD7AEFB), // Purple
    Color(0xFFAECBFA), // Blue
    Color(0xFFCBF0F8), // Cyan
    Color(0xFFCCFF90), // Green
    Color(0xFFA7FFEB), // Teal
    Color(0xFFE6C9A8)  // Brown
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedColorPicker(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(if (currentColor == 0) Color(0xFFFFFFFF).toArgb() else currentColor) }
    var showCustomPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Note Color")
        },
        text = {
            Column {
                if (showCustomPicker) {
                    var red by remember { mutableFloatStateOf(android.graphics.Color.red(selectedColor) / 255f) }
                    var green by remember { mutableFloatStateOf(android.graphics.Color.green(selectedColor) / 255f) }
                    var blue by remember { mutableFloatStateOf(android.graphics.Color.blue(selectedColor) / 255f) }
                    
                    var hexText by remember { mutableStateOf(String.format("#%02X%02X%02X", (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())) }
                    var isError by remember { mutableStateOf(false) }

                    fun updateFromHex(hex: String) {
                        hexText = hex
                        if (hex.length == 7 && hex.startsWith("#")) {
                            try {
                                val parsed = android.graphics.Color.parseColor(hex)
                                red = android.graphics.Color.red(parsed) / 255f
                                green = android.graphics.Color.green(parsed) / 255f
                                blue = android.graphics.Color.blue(parsed) / 255f
                                selectedColor = parsed
                                isError = false
                            } catch (e: Exception) {
                                isError = true
                            }
                        } else {
                            isError = true
                        }
                    }

                    fun updateFromRgb(r: Float, g: Float, b: Float) {
                        red = r
                        green = g
                        blue = b
                        selectedColor = Color(r, g, b).toArgb()
                        hexText = String.format("#%02X%02X%02X", (r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
                        isError = false
                    }
                    
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(selectedColor))
                                .border(1.dp, PrimaryColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        )
                        
                        OutlinedTextField(
                            value = hexText,
                            onValueChange = { 
                                if (it.length <= 7) updateFromHex(it.uppercase()) 
                            },
                            label = { Text("HEX Color", fontSize = 12.sp) },
                            isError = isError,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            textStyle = TextStyle(fontSize = 14.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryColor,
                                focusedLabelColor = PrimaryColor
                            )
                        )
                        
                        Text("Red: ${(red * 255).toInt()}", fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        Slider(value = red, onValueChange = { updateFromRgb(it, green, blue) })
                        
                        Text("Green: ${(green * 255).toInt()}", fontSize = 12.sp)
                        Slider(value = green, onValueChange = { updateFromRgb(red, it, blue) })
                        
                        Text("Blue: ${(blue * 255).toInt()}", fontSize = 12.sp)
                        Slider(value = blue, onValueChange = { updateFromRgb(red, green, it) })
                        
                        TextButton(onClick = { showCustomPicker = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Back to Palette", color = PrimaryColor)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(PredefinedColors) { color ->
                            val colorInt = color.toArgb()
                            val isSelected = selectedColor == colorInt
                            
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(if (isSelected) 3.dp else 1.dp, PrimaryColor.copy(alpha = if (isSelected) 1f else 0.4f), CircleShape)
                                    .clickable {
                                        selectedColor = colorInt
                                        onColorSelected(colorInt)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = PrimaryColor)
                                }
                            }
                        }
                        
                        item {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                                    .border(1.dp, PrimaryColor.copy(alpha = 0.4f), CircleShape)
                                    .clickable {
                                        showCustomPicker = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Custom Color", tint = PrimaryColor)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                onColorSelected(selectedColor)
                onDismiss()
            }) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
