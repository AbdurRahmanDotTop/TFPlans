package com.techilyfly.tfplans.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush as ComposeBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techilyfly.tfplans.ui.theme.*
import com.techilyfly.tfplans.ui.components.AdBanner
import com.techilyfly.tfplans.ui.components.AdManager
import com.techilyfly.tfplans.BuildConfig
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToTab: (String) -> Unit,
    onLogout: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val defaultView by viewModel.defaultView.collectAsState()
    val context = LocalContext.current
    val userEmail = viewModel.getUserEmail()

    var browserUrl by remember { mutableStateOf<String?>(null) }

    if (browserUrl != null) {
        InAppBrowserScreen(
            initialUrl = browserUrl!!,
            onDismiss = { browserUrl = null }
        )
    }

    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    val photoUrl = auth.currentUser?.photoUrl
    val userInitials = if (userEmail.isNotBlank()) userEmail.take(2).uppercase() else "U"




    Scaffold(
        topBar = {
            Surface(
                color = SurfaceColor.copy(alpha = 0.95f),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                AdManager.incrementActivity(context)
                                onNavigateBack()
                            },
                            modifier = Modifier.size(40.dp).testTag("settings_back_btn")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = PrimaryColor
                            )
                        }

                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = PrimaryColor
                        )
                    }

                    // User Profile Avatar Pill
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                ComposeBrush.linearGradient(
                                    listOf(PrimaryColor, TertiaryColor)
                                )
                            )
                            .clickable { onNavigateToProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            coil.compose.AsyncImage(
                                model = photoUrl,
                                contentDescription = "Profile Photo",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = userInitials,
                                color = BackgroundColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            com.techilyfly.tfplans.ui.components.AppBottomBar(
                currentTab = "Settings",
                onTabSelected = onNavigateToTab
            )
        },
        containerColor = BackgroundColor
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ================= HERO BANNER CARD =================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = ComposeBrush.linearGradient(
                                colors = listOf(PrimaryColor, Color(0xFF8B5CF6), TertiaryColor)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Preferences",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tailor TF Plans to perfectly match your workflow and style.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }

                        // Decorative settings gear icon
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.18f),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ================= APPEARANCE SECTION =================
            SettingsSectionCard(
                title = "Appearance",
                icon = Icons.Filled.Palette
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "THEME",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = SecondaryColor
                    )

                    // 3-way Segmented Theme Switcher
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PrimaryColor.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ThemeOptionButton(
                                label = "Light",
                                icon = Icons.Filled.LightMode,
                                isSelected = themeMode == "light",
                                onClick = { viewModel.setThemeMode("light") },
                                modifier = Modifier.weight(1f).testTag("theme_light_btn")
                            )

                            ThemeOptionButton(
                                label = "Dark",
                                icon = Icons.Filled.DarkMode,
                                isSelected = themeMode == "dark",
                                onClick = { viewModel.setThemeMode("dark") },
                                modifier = Modifier.weight(1f).testTag("theme_dark_btn")
                            )

                            ThemeOptionButton(
                                label = "System",
                                icon = Icons.Filled.BrightnessAuto,
                                isSelected = themeMode == "system",
                                onClick = { viewModel.setThemeMode("system") },
                                modifier = Modifier.weight(1f).testTag("theme_system_btn")
                            )
                        }
                    }
                }
            }

            // ================= GENERAL SECTION =================
            SettingsSectionCard(
                title = "General",
                icon = Icons.Filled.Tune
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val currentSp = FontSizeManager.parseSp(fontSize)

                    // Font Size Header & Reset
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Font Size",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Adjust text size (${currentSp.toInt()} sp)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = { viewModel.setFontSize("16") },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("reset_font_size_btn")
                            ) {
                                Icon(
                                    Icons.Filled.RestartAlt,
                                    contentDescription = "Reset Font Size",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Reset", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Font Size Controls (Stepper - Slider - Stepper +)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        val newSp = (currentSp - 2f).coerceAtLeast(FontSizeManager.MIN_SP)
                                        viewModel.setFontSize(newSp.toInt().toString())
                                    },
                                    modifier = Modifier.size(36.dp).testTag("font_decrease_btn")
                                ) {
                                    Icon(
                                        Icons.Filled.Remove,
                                        contentDescription = "Decrease Font Size",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Slider(
                                    value = currentSp,
                                    onValueChange = { newSp ->
                                        viewModel.setFontSize(newSp.toInt().toString())
                                    },
                                    valueRange = FontSizeManager.MIN_SP..FontSizeManager.MAX_SP,
                                    steps = 5,
                                    modifier = Modifier.weight(1f).testTag("font_size_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    )
                                )

                                IconButton(
                                    onClick = {
                                        val newSp = (currentSp + 2f).coerceAtMost(FontSizeManager.MAX_SP)
                                        viewModel.setFontSize(newSp.toInt().toString())
                                    },
                                    modifier = Modifier.size(36.dp).testTag("font_increase_btn")
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Increase Font Size",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Live Text Size Preview Box
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Sample Note Title",
                                        fontSize = FontSizeManager.getCardTitleSp(fontSize),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "This is a preview of how your note content text will look at ${currentSp.toInt()} sp font size.",
                                        fontSize = FontSizeManager.getPreviewSp(fontSize),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))

                    // Default View Setting
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Default View",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Choose default note layout mode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (defaultView == "grid") MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setDefaultView("grid") }
                                        .testTag("view_grid_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.GridView,
                                            contentDescription = "Grid View",
                                            tint = if (defaultView == "grid") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Grid View",
                                            fontSize = 13.sp,
                                            fontWeight = if (defaultView == "grid") FontWeight.Bold else FontWeight.Medium,
                                            color = if (defaultView == "grid") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (defaultView == "list") MaterialTheme.colorScheme.primary else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { viewModel.setDefaultView("list") }
                                        .testTag("view_list_btn")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ViewList,
                                            contentDescription = "List View",
                                            tint = if (defaultView == "list") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "List View",
                                            fontSize = 13.sp,
                                            fontWeight = if (defaultView == "list") FontWeight.Bold else FontWeight.Medium,
                                            color = if (defaultView == "list") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            Spacer(modifier = Modifier.height(24.dp))
        }
    }


}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            content()
        }
    }
}

@Composable
private fun ThemeOptionButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
