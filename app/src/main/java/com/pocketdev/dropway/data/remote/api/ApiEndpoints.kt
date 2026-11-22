package com.pocketdev.dropway.data.remote.api

/**
 * REST API Endpoint paths and URL configurations.
 */
object ApiEndpoints {
    // Production Cloud URL and Local Emulator Fallback
    const val DEFAULT_BASE_URL = "https://dropway.app/api"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:3000/api"

    // Authentication Endpoints
    const val AUTH_REGISTER = "/auth/register"
    const val AUTH_LOGIN = "/auth/login"
    const val AUTH_PROFILE = "/auth/profile"
    const val AUTH_REFRESH = "/auth/refresh"

    // Folder Management
    const val FOLDERS = "/folders"
    fun folderDetail(folderId: String) = "/folders/$folderId"
    fun folderHouses(folderId: String) = "/folders/$folderId/houses"

    // Houses & Addresses
    const val HOUSES = "/houses"
    fun houseDetail(houseId: String) = "/houses/$houseId"
    const val HOUSES_BATCH = "/houses/batch"

    // Navigation Queue
    const val NAVIGATION = "/navigation"
    fun navigationDetail(navId: String) = "/navigation/$navId"
    const val NAVIGATION_CLEAR = "/navigation/clear"

    // Synchronization Engine
    const val SYNC_PUSH = "/sync/push"
    const val SYNC_PULL = "/sync/pull"

    // QR Sharing & Delegation
    const val SHARING_CREATE_TOKEN = "/sharing/create-token"
    const val SHARING_REDEEM_TOKEN = "/sharing/redeem-token"
    const val SHARING_REVOKE = "/sharing/revoke"

    // AI Vision
    const val AI_LABEL = "/ai/label"
    const val AI_MODELS = "/ai/models"
}
