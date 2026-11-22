package com.pocketdev.dropway.data.remote.api

import java.net.HttpURLConnection
import java.util.UUID

/**
 * Interceptor responsible for injecting security headers, platform telemetry, and idempotency keys.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
    private val appVersion: String = "2.0.0"
) {
    fun applyHeaders(connection: HttpURLConnection, idempotencyKey: String? = null) {
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("X-Client-Platform", "Android")
        connection.setRequestProperty("X-Client-Version", appVersion)

        val token = tokenProvider()
        if (!token.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $token")
        }

        if (!idempotencyKey.isNullOrBlank()) {
            connection.setRequestProperty("Idempotency-Key", idempotencyKey)
        } else if (connection.requestMethod in listOf("POST", "PUT", "DELETE")) {
            connection.setRequestProperty("Idempotency-Key", UUID.randomUUID().toString())
        }
    }
}
