package com.pocketdev.dropway.data.repository

import com.pocketdev.dropway.data.local.LocalHouseDatabase
import com.pocketdev.dropway.data.local.PreferencesManager
import com.pocketdev.dropway.data.remote.api.ApiEndpoints
import com.pocketdev.dropway.data.remote.api.DropwayApiClient
import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.User
import com.pocketdev.dropway.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Implementation of AuthRepository managing cloud authentication and local user profile persistence.
 */
class AuthRepositoryImpl(
    private val localDb: LocalHouseDatabase,
    private val prefs: PreferencesManager,
    private val apiClient: DropwayApiClient
) : AuthRepository {

    override suspend fun login(email: String, password: String): AppResult<User> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            val response = apiClient.post(ApiEndpoints.AUTH_LOGIN, payload)
            if (response.isSuccessful) {
                val data = (response as com.pocketdev.dropway.data.remote.api.ApiResponse.Success).data
                val token = data.optString("token", "")
                val userObj = data.optJSONObject("user") ?: data

                val user = User(
                    id = userObj.optString("id", prefs.userId),
                    email = userObj.optString("email", email),
                    displayName = userObj.optString("displayName", prefs.driverName),
                    phoneNumber = userObj.optString("phoneNumber").takeIf { it.isNotBlank() && it != "null" },
                    role = userObj.optString("role", "driver"),
                    planType = userObj.optString("planType", prefs.subscriptionPlan),
                    authToken = token,
                    lastSyncedAt = System.currentTimeMillis()
                )

                localDb.saveUserProfile(user)
                prefs.userId = user.id
                prefs.driverName = user.displayName

                AppResult.Success(user)
            } else {
                val err = response as com.pocketdev.dropway.data.remote.api.ApiResponse.Error
                AppResult.Error(message = err.message, code = err.code)
            }
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to authenticate", cause = e)
        }
    }

    override suspend fun register(email: String, password: String, displayName: String): AppResult<User> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("displayName", displayName)
            }
            val response = apiClient.post(ApiEndpoints.AUTH_REGISTER, payload)
            if (response.isSuccessful) {
                val data = (response as com.pocketdev.dropway.data.remote.api.ApiResponse.Success).data
                val token = data.optString("token", "")
                val userObj = data.optJSONObject("user") ?: data

                val user = User(
                    id = userObj.optString("id", prefs.userId),
                    email = userObj.optString("email", email),
                    displayName = userObj.optString("displayName", displayName),
                    role = userObj.optString("role", "driver"),
                    planType = "free",
                    authToken = token,
                    lastSyncedAt = System.currentTimeMillis()
                )

                localDb.saveUserProfile(user)
                prefs.userId = user.id
                prefs.driverName = user.displayName

                AppResult.Success(user)
            } else {
                val err = response as com.pocketdev.dropway.data.remote.api.ApiResponse.Error
                AppResult.Error(message = err.message, code = err.code)
            }
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to register", cause = e)
        }
    }

    override suspend fun getCurrentUser(): AppResult<User?> = withContext(Dispatchers.IO) {
        try {
            val user = localDb.getUserProfile() ?: User(
                id = prefs.userId,
                email = "",
                displayName = prefs.driverName,
                role = "driver",
                planType = prefs.subscriptionPlan
            )
            AppResult.Success(user)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to fetch user profile", cause = e)
        }
    }

    override suspend fun logout(): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            localDb.clearUserProfile()
            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to logout", cause = e)
        }
    }

    override suspend fun updateProfile(displayName: String, phoneNumber: String?): AppResult<User> = withContext(Dispatchers.IO) {
        try {
            val current = localDb.getUserProfile() ?: User(
                id = prefs.userId,
                email = "",
                displayName = prefs.driverName
            )
            val updated = current.copy(displayName = displayName, phoneNumber = phoneNumber)
            localDb.saveUserProfile(updated)
            prefs.driverName = displayName
            AppResult.Success(updated)
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to update profile", cause = e)
        }
    }

    override suspend fun refreshSession(): AppResult<User> = withContext(Dispatchers.IO) {
        try {
            val current = localDb.getUserProfile()
            if (current != null && current.authToken.isNotBlank()) {
                val response = apiClient.get(ApiEndpoints.AUTH_PROFILE)
                if (response.isSuccessful) {
                    val data = (response as com.pocketdev.dropway.data.remote.api.ApiResponse.Success).data
                    val updated = current.copy(
                        displayName = data.optString("displayName", current.displayName),
                        role = data.optString("role", current.role),
                        planType = data.optJSONObject("subscription")?.optString("planId", current.planType) ?: current.planType,
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    localDb.saveUserProfile(updated)
                    return@withContext AppResult.Success(updated)
                }
            }
            AppResult.Success(current ?: User(id = prefs.userId, email = "", displayName = prefs.driverName))
        } catch (e: Exception) {
            AppResult.Error(message = e.localizedMessage ?: "Failed to refresh session", cause = e)
        }
    }
}
