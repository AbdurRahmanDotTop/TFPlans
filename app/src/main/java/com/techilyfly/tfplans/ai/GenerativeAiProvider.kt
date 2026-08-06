package com.techilyfly.tfplans.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.techilyfly.tfplans.BuildConfig

object GenerativeAiProvider {
    
    // Lazy initialization of the GenerativeModel using the free Google AI Studio SDK.
    // This requires GEMINI_API_KEY to be set in local.properties or .env.
    val model: GenerativeModel by lazy {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        GenerativeModel(
            modelName = "gemini-3.5-flash",
            apiKey = apiKey
        )
    }
}
