package com.techilyfly.tfplans.ui.components

import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.techilyfly.tfplans.ui.theme.PrimaryColor
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun NativeAdCard(nativeAd: NativeAd, modifier: Modifier = Modifier) {
    val primaryColorArgb = PrimaryColor.toArgb()
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDark) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    val bodyTextColor = if (isDark) android.graphics.Color.LTGRAY else android.graphics.Color.DKGRAY

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PrimaryColor.copy(alpha = 0.15f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            factory = { context ->
                val adView = NativeAdView(context)
                adView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

                val container = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Ad Badge & Headline Row
                val headerRow = android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                val density = context.resources.displayMetrics.density
                val dp12 = (12 * density).toInt()
                val dp4 = (4 * density).toInt()
                val dp16 = (16 * density).toInt()
                val dp24 = (24 * density).toInt()
                val mediaHeight = (220 * density).toInt()

                val adBadge = TextView(context).apply {
                    text = context.getString(com.techilyfly.tfplans.R.string.ad_badge)
                    textSize = 10f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.parseColor("#fbc02d")) // Yellow
                    setPadding(dp12, dp4, dp12, dp4)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginEnd = dp16
                    }
                    val shape = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 8f
                        setColor(android.graphics.Color.parseColor("#fbc02d"))
                    }
                    background = shape
                }

                val headlineView = TextView(context).apply {
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(textColor)
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                
                headerRow.addView(adBadge)
                headerRow.addView(headlineView)
                adView.headlineView = headlineView

                val mediaView = MediaView(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        mediaHeight
                    ).apply {
                        topMargin = dp24
                        bottomMargin = dp16
                    }
                }
                adView.mediaView = mediaView

                val bodyView = TextView(context).apply {
                    textSize = 14f
                    setTextColor(bodyTextColor)
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                adView.bodyView = bodyView

                val callToActionView = Button(context).apply {
                    textSize = 14f
                    setTextColor(android.graphics.Color.WHITE)
                    setAllCaps(false)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp24
                    }
                    val shape = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = 24f
                        setColor(primaryColorArgb)
                    }
                    background = shape
                }
                adView.callToActionView = callToActionView

                container.addView(headerRow)
                container.addView(mediaView)
                container.addView(bodyView)
                container.addView(callToActionView)

                adView.addView(container)
                adView
            },
            update = { adView ->
                (adView.headlineView as TextView).apply {
                    text = nativeAd.headline
                    setTextColor(textColor)
                }

                if (nativeAd.body == null) {
                    adView.bodyView?.visibility = android.view.View.GONE
                } else {
                    adView.bodyView?.visibility = android.view.View.VISIBLE
                    (adView.bodyView as TextView).apply {
                        text = nativeAd.body
                        setTextColor(bodyTextColor)
                    }
                }

                if (nativeAd.callToAction == null) {
                    adView.callToActionView?.visibility = android.view.View.GONE
                } else {
                    adView.callToActionView?.visibility = android.view.View.VISIBLE
                    (adView.callToActionView as Button).apply {
                        text = nativeAd.callToAction
                        // Re-apply background color in case theme changed
                        val shape = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                            cornerRadius = 24f
                            setColor(primaryColorArgb)
                        }
                        background = shape
                    }
                }

                adView.setNativeAd(nativeAd)
            }
        )
    }
}
