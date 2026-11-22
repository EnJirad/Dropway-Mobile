package com.pocketdev.dropway.data.remote.api

/**
 * Universal Network Response envelope wrapping HTTP responses.
 */
sealed class ApiResponse<out T> {
    data class Success<out T>(
        val data: T,
        val statusCode: Int = 200,
        val timestamp: Long = System.currentTimeMillis()
    ) : ApiResponse<T>()

    data class Error(
        val code: String,
        val message: String,
        val statusCode: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    ) : ApiResponse<Nothing>()

    val isSuccessful: Boolean get() = this is Success
}
