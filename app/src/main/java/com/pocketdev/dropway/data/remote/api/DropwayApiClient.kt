package com.pocketdev.dropway.data.remote.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud HTTP Client for Dropway REST API.
 * Uses native HttpURLConnection with zero external dependency overhead and handles TLS 1.3 / JSON communication.
 */
class DropwayApiClient(
    private var baseUrl: String = ApiEndpoints.DEFAULT_BASE_URL,
    private val authInterceptor: AuthInterceptor,
    private val connectTimeoutMs: Int = 15000,
    private val readTimeoutMs: Int = 20000
) {

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
    }

    suspend fun get(endpoint: String): ApiResponse<JSONObject> = withContext(Dispatchers.IO) {
        executeRequest("GET", endpoint, null)
    }

    suspend fun post(endpoint: String, payload: JSONObject, idempotencyKey: String? = null): ApiResponse<JSONObject> = withContext(Dispatchers.IO) {
        executeRequest("POST", endpoint, payload.toString(), idempotencyKey)
    }

    suspend fun put(endpoint: String, payload: JSONObject, idempotencyKey: String? = null): ApiResponse<JSONObject> = withContext(Dispatchers.IO) {
        executeRequest("PUT", endpoint, payload.toString(), idempotencyKey)
    }

    suspend fun delete(endpoint: String, idempotencyKey: String? = null): ApiResponse<JSONObject> = withContext(Dispatchers.IO) {
        executeRequest("DELETE", endpoint, null, idempotencyKey)
    }

    private fun executeRequest(
        method: String,
        endpoint: String,
        jsonBody: String?,
        idempotencyKey: String? = null
    ): ApiResponse<JSONObject> {
        var connection: HttpURLConnection? = null
        return try {
            val fullUrl = if (endpoint.startsWith("http")) endpoint else "$baseUrl$endpoint"
            val url = URL(fullUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doInput = true
                authInterceptor.applyHeaders(this, idempotencyKey)
            }

            if (jsonBody != null && (method == "POST" || method == "PUT" || method == "PATCH")) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val statusCode = connection.responseCode
            val isSuccess = statusCode in 200..299
            val inputStream = if (isSuccess) connection.inputStream else connection.errorStream

            val responseBody = if (inputStream != null) {
                BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                    reader.readText()
                }
            } else {
                "{}"
            }

            val jsonResponse = try {
                JSONObject(responseBody)
            } catch (e: Exception) {
                JSONObject().apply { put("raw", responseBody) }
            }

            if (isSuccess) {
                val dataObj = if (jsonResponse.has("data") && !jsonResponse.isNull("data")) {
                    val rawData = jsonResponse.get("data")
                    if (rawData is JSONObject) rawData else jsonResponse
                } else {
                    jsonResponse
                }
                ApiResponse.Success(data = dataObj, statusCode = statusCode)
            } else {
                val errorCode = jsonResponse.optJSONObject("error")?.optString("code") ?: "HTTP_$statusCode"
                val errorMessage = jsonResponse.optJSONObject("error")?.optString("message") 
                    ?: jsonResponse.optString("message", "Network request failed with status $statusCode")
                ApiResponse.Error(code = errorCode, message = errorMessage, statusCode = statusCode)
            }
        } catch (e: Exception) {
            ApiResponse.Error(
                code = "NETWORK_EXCEPTION",
                message = e.localizedMessage ?: "Network connection error",
                statusCode = 0
            )
        } finally {
            connection?.disconnect()
        }
    }
}
