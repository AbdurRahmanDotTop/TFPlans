package com.techilyfly.tfplans.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techilyfly.tfplans.ui.theme.PrimaryColor
import com.techilyfly.tfplans.ui.theme.SecondaryColor
import com.techilyfly.tfplans.ui.theme.SurfaceColor

@Composable
fun AppBottomBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Column {
        AdBanner()
        
        Surface(
            color = SurfaceColor.copy(alpha = 0.95f),
            shadowElevation = 12.dp,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    val tabs = listOf(
                        Triple("Notes", Icons.Filled.Description, "Notes"),
                        Triple("Reminders", Icons.Filled.Notifications, "Reminders"),
                        Triple("Archive", Icons.Filled.Archive, "Archive"),
                        Triple("Settings", Icons.Filled.Settings, "Settings")
                    )

                    tabs.forEach { (tabName, icon, label) ->
                        val isSelected = currentTab == tabName
                        val activeColor = if (isSelected) PrimaryColor else SecondaryColor.copy(alpha = 0.7f)

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onTabSelected(tabName) }
                                .padding(vertical = 4.dp)
                                .testTag("nav_tab_$tabName")
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = activeColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = activeColor
                            )
                        }
                    }
                }
            }
        }
    }
}
