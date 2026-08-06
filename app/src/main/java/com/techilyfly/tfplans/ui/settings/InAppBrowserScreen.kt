package com.techilyfly.tfplans.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun InAppBrowserScreen(
    initialUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    LaunchedEffect(initialUrl) {
        try {
            val builder = CustomTabsIntent.Builder()
            builder.setShowTitle(true)
            val customTabsIntent = builder.build()
            
            val secureUrl = if (initialUrl.startsWith("http://")) {
                initialUrl.replace("http://", "https://")
            } else {
                initialUrl
            }
            
            customTabsIntent.launchUrl(context, Uri.parse(secureUrl))
        } catch (e: Exception) {
            // Fallback if Custom Tabs isn't available
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(initialUrl))
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Ignore
            }
        }
        
        // Immediately dismiss the state so when the user closes the Custom Tab,
        // they return to the original screen smoothly.
        onDismiss()
    }
}
