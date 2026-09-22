package com.app.sdk.kasookoo.helper

data class KasookooConfig(
    val baseUrl: String = "https://sdk.kasookoo.ai",
    val publishableKey: String,
    val userId: String? = null,
    val featureType: String = "general",
    val telemetryEnabled: Boolean = true,
    val isDebug: Boolean = true,
    val loggingEnabled: Boolean = true
)
