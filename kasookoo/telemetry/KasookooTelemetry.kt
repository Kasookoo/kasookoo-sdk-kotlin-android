package com.app.sdk.kasookoo.telemetry

import android.content.Context
import android.util.Log
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context as OTelContext
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.api.common.AttributeKey

object KasookooTelemetry {
    private var openTelemetry: OpenTelemetry = OpenTelemetry.noop()
    private var tracer: Tracer? = null
    private var isEnabled = false

    fun init(context: Context, serviceName: String = "kasookoo-android", isDebug: Boolean = true, enabled: Boolean = true) {
        this.isEnabled = enabled
        if (!enabled) return

        val endpoint = if (isDebug) "https://monitoring-test.kasookoo.ai" else "https://monitoring.kasookoo.ai"

        val resource = Resource.getDefault().toBuilder()
            .put(AttributeKey.stringKey("service.name"), serviceName)
            .build()

        val spanExporter = OtlpHttpSpanExporter.builder()
            .setEndpoint("$endpoint/v1/traces")
            .build()

        val tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
            .setResource(resource)
            .build()

        val logExporter = OtlpHttpLogRecordExporter.builder()
            .setEndpoint("$endpoint/v1/logs")
            .build()

        val loggerProvider = SdkLoggerProvider.builder()
            .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build())
            .setResource(resource)
            .build()

        openTelemetry = OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setLoggerProvider(loggerProvider)
            .build()

        tracer = openTelemetry.getTracer(serviceName)
    }

    fun startSpan(name: String): Span? {
        if (!isEnabled) return null
        val span = tracer?.spanBuilder(name)?.startSpan()
        TelemetryContext.callTraceId?.let { span?.setAttribute("call_trace_id", it) }
        TelemetryContext.userId?.let { span?.setAttribute("user_id", it) }
        TelemetryContext.featureType?.let { span?.setAttribute("feature_type", it) }
        return span
    }

    fun endSpan(span: Span?, success: Boolean = true, error: String? = null) {
        if (span == null) return
        if (success) {
            span.setStatus(StatusCode.OK)
        } else {
            span.setStatus(StatusCode.ERROR, error ?: "Unknown error")
            if (error != null) {
                span.recordException(Exception(error))
            }
        }
        span.end()
    }

    fun logEvent(event: String, level: String = "INFO", attributes: Map<String, String> = emptyMap()) {
        if (!isEnabled) return
        
        // Structured logging through OTel Logs
        val logger = openTelemetry.logsBridge.get("kasookoo-android")
        val logRecord = logger.logRecordBuilder()
            .setBody(event)
            .setAttribute(AttributeKey.stringKey("event"), event)
            .setAttribute(AttributeKey.stringKey("level"), level)
        
        TelemetryContext.callTraceId?.let { logRecord.setAttribute(AttributeKey.stringKey("call_trace_id"), it) }
        TelemetryContext.userId?.let { logRecord.setAttribute(AttributeKey.stringKey("user_id"), it) }
        TelemetryContext.featureType?.let { logRecord.setAttribute(AttributeKey.stringKey("feature_type"), it) }
        
        attributes.forEach { (k, v) ->
            logRecord.setAttribute(AttributeKey.stringKey(k), v)
        }
        
        logRecord.emit()
        
        // Also log to logcat if in debug mode
        if (level == "ERROR") {
            Log.e("KasookooTelemetry", "[$event] $attributes")
        } else {
            Log.i("KasookooTelemetry", "[$event] $attributes")
        }
    }
}
