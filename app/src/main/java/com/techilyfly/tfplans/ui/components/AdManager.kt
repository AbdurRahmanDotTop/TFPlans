package com.techilyfly.tfplans.ui.components

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions

object AdManager {
    private const val TAG = "AdManager"
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-4936596132232039/3809097077"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-4936596132232039/3813654868"
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-4936596132232039/4586992122"
    
    private var interstitialAd: InterstitialAd? = null
    private var activityCount = 0
    private var isAdLoading = false
    private var threshold = (10..20).random()

    fun loadAd(context: Context) {
        if (interstitialAd != null || isAdLoading) return
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    Log.d(TAG, "Interstitial Ad loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    Log.e(TAG, "Interstitial Ad failed to load: ${error.message}")
                }
            }
        )
    }

    fun incrementActivity(context: Context) {
        activityCount++
        Log.d(TAG, "Activity Count incremented: $activityCount, Threshold: $threshold")
        
        // Always try to load the ad if it is null so it's ready when we hit the threshold
        if (interstitialAd == null) {
            loadAd(context)
        }
        
        if (activityCount >= threshold) {
            showAd(context)
        }
    }

    private fun showAd(context: Context) {
        val activity = context as? Activity
        if (activity != null && interstitialAd != null) {
            Log.d(TAG, "Showing Interstitial Ad")
            interstitialAd?.show(activity)
            interstitialAd = null // Clear after show
            
            // Generate a new random threshold and reset count after showing
            threshold = (10..20).random()
            activityCount = 0
            Log.d(TAG, "New Threshold set to: $threshold")
            
            loadAd(context) // Pre-load the next one
        } else {
            Log.d(TAG, "Ad was not shown. Activity: $activity, Ad Ready: ${interstitialAd != null}")
            loadAd(context)
        }
    }

    private var nativeAd: NativeAd? = null
    private var isNativeAdLoading = false

    fun loadNativeAd(context: Context) {
        if (nativeAd != null || isNativeAdLoading) return
        isNativeAdLoading = true

        val adLoader = AdLoader.Builder(context.applicationContext, NATIVE_AD_UNIT_ID)
            .forNativeAd { ad: NativeAd ->
                nativeAd?.destroy()
                nativeAd = ad
                isNativeAdLoading = false
                Log.d(TAG, "Native Ad loaded successfully")
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isNativeAdLoading = false
                    Log.e(TAG, "Native Ad failed to load: ${error.message}")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    fun getNativeAdAndLoadNext(context: Context): NativeAd? {
        val ad = nativeAd
        nativeAd = null
        loadNativeAd(context)
        return ad
    }
}
