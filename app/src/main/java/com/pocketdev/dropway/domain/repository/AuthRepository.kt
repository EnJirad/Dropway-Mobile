package com.pocketdev.dropway.domain.repository

import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.User

/**
 * Repository interface defining authentication, session, and user profile operations.
 */
interface AuthRepository {
    suspend fun login(email: String, password: String): AppResult<User>
    suspend fun register(email: String, password: String, displayName: String): AppResult<User>
    suspend fun getCurrentUser(): AppResult<User?>
    suspend fun logout(): AppResult<Unit>
    suspend fun updateProfile(displayName: String, phoneNumber: String? = null): AppResult<User>
    suspend fun refreshSession(): AppResult<User>
}
