package com.app.sdk.kasookoo.telemetry

import okhttp3.Interceptor
import okhttp3.Response

class TelemetryInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // Generate a new request ID for every request
        builder.addHeader("X-Request-ID", TelemetryContext.getNewRequestId())

        // Add call trace ID if it exists
        TelemetryContext.callTraceId?.let {
            builder.addHeader("X-Call-Trace-ID", it)
        }

        // Add user ID if it exists
        TelemetryContext.userId?.let {
            builder.addHeader("X-User-ID", it)
        }

        // Add feature type if it exists
        TelemetryContext.featureType?.let {
            builder.addHeader("X-Feature-Type", it)
        }

        return chain.proceed(builder.build())
    }
}
