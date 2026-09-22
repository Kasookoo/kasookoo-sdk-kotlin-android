package com.app.sdk.kasookoo.helper

import android.content.Context
import com.app.sdk.data.TokenModel
import com.app.sdk.flashcall.FlashCallRepository
import com.app.sdk.kasookoo.telemetry.KasookooTelemetry
import com.app.sdk.kasookoo.telemetry.TelemetryContext
import io.opentelemetry.api.trace.Span

object KasookooSdkHelper {
    private var isInitialized = false
    private var repository: FlashCallRepository? = null
    private var config: KasookooConfig? = null

    fun initialize(context: Context, sdkConfig: KasookooConfig) {
        config = sdkConfig
        if (isInitialized) return
        
        repository = FlashCallRepository(context.applicationContext)
        
        // Initialize Telemetry
        KasookooTelemetry.init(
            context = context.applicationContext,
            isDebug = sdkConfig.isDebug,
            enabled = sdkConfig.telemetryEnabled
        )
        
        isInitialized = true
        
        if (sdkConfig.loggingEnabled) {
            KasookooTelemetry.logEvent("SDK_INITIALIZED", "INFO", mapOf("user_id" to (sdkConfig.userId ?: "anonymous")))
        }
    }

    /**
     * Flash Call Feature
     */
    fun startFlashCallFeature(userId: String, onSuccess: (TokenModel?) -> Unit, onError: (String) -> Unit) {
        if (!isInitialized) {
            onError("SDK not initialized")
            return
        }

        TelemetryContext.startFeature("flash_call", userId)
        
        val span = KasookooTelemetry.startSpan("flash_call.create")
        
        repository?.createClientSession(
            key = config?.publishableKey ?: "",
            onSuccess = { tokenModel ->
                KasookooTelemetry.endSpan(span, true)
                KasookooTelemetry.logEvent("FLASH_CALL_SESSION_CREATED", attributes = mapOf("session_id" to (tokenModel?.session_id ?: "null")))
                onSuccess(tokenModel)
            },
            onError = { error ->
                KasookooTelemetry.endSpan(span, false, error)
                KasookooTelemetry.logEvent("FLASH_CALL_FAILED", "ERROR", mapOf("error" to error))
                onError(error)
                TelemetryContext.clear()
            },
            onTime = { }
        )
    }

    fun triggerFlashCall(token: String, phone: String, devId: String, info: Map<String, Any>, onSuccess: (TokenModel?) -> Unit, onError: (String) -> Unit) {
        val span = KasookooTelemetry.startSpan("flash_call.trigger_outbound")
        
        repository?.triggerOutbound(
            token = token,
            phone = phone,
            devId = devId,
            info = info,
            onSuccess = { tokenModel ->
                KasookooTelemetry.endSpan(span, true)
                KasookooTelemetry.logEvent("FLASH_CALL_TRIGGERED")
                onSuccess(tokenModel)
            },
            onError = { error ->
                KasookooTelemetry.endSpan(span, false, error)
                KasookooTelemetry.logEvent("FLASH_CALL_TRIGGER_FAILED", "ERROR", mapOf("error" to error))
                onError(error)
            },
            onTime = { }
        )
    }

    fun verifyFlashCall(token: String, sessionId: String, devId: String, onSuccess: (TokenModel?) -> Unit, onError: (String) -> Unit) {
        val span = KasookooTelemetry.startSpan("flash_call.verify")
        
        repository?.verify(
            token = token,
            sid = sessionId,
            devId = devId,
            onSuccess = { tokenModel ->
                KasookooTelemetry.endSpan(span, true)
                KasookooTelemetry.logEvent("FLASH_CALL_VERIFIED")
                onSuccess(tokenModel)
                TelemetryContext.clear()
            },
            onError = { error ->
                KasookooTelemetry.endSpan(span, false, error)
                KasookooTelemetry.logEvent("FLASH_CALL_VERIFY_FAILED", "ERROR", mapOf("error" to error))
                onError(error)
                TelemetryContext.clear()
            },
            onTime = { }
        )
    }

    /**
     * K Chat Feature
     */
    fun startKChatFeature(userId: String) {
        TelemetryContext.startFeature("k_chat", userId)
        val span = KasookooTelemetry.startSpan("session.mint")
        KasookooTelemetry.logEvent("KCHAT_STARTED")
        // Implementation for session minting would go here
        KasookooTelemetry.endSpan(span, true)
    }
    
    fun sendChatMessage(message: String, callback: (Boolean) -> Unit) {
        val span = KasookooTelemetry.startSpan("messaging.send")
        // Messaging logic
        KasookooTelemetry.logEvent("MESSAGE_SENT")
        KasookooTelemetry.endSpan(span, true)
        callback(true)
    }

    fun startWebRTCCall(roomId: String) {
        val span = KasookooTelemetry.startSpan("webrtc.create_session")
        KasookooTelemetry.logEvent("CALL_STARTED", attributes = mapOf("room_id" to roomId))
        // WebRTC logic
        KasookooTelemetry.endSpan(span, true)
    }

    fun joinWebRTCCall(roomId: String) {
        val span = KasookooTelemetry.startSpan("webrtc.join")
        KasookooTelemetry.logEvent("CALL_JOINED", attributes = mapOf("room_id" to roomId))
        KasookooTelemetry.endSpan(span, true)
    }

    fun endWebRTCCall() {
        val span = KasookooTelemetry.startSpan("webrtc.hangup")
        KasookooTelemetry.logEvent("CALL_ENDED")
        KasookooTelemetry.endSpan(span, true)
        TelemetryContext.clear()
    }
    
    fun stopKChatFeature() {
        KasookooTelemetry.logEvent("KCHAT_STOPPED")
        TelemetryContext.clear()
    }
}
