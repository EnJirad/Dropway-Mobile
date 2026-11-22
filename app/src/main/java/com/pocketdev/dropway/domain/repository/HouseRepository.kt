package com.pocketdev.dropway.domain.repository

import com.pocketdev.dropway.domain.model.AppResult
import com.pocketdev.dropway.domain.model.House

/**
 * Repository interface defining operations on houses / addresses.
 * Provides clean decoupling between Domain business logic and Local/Remote data sources.
 */
interface HouseRepository {
    suspend fun getHouses(folderId: String? = null, query: String? = null): AppResult<List<House>>
    suspend fun getHouseById(id: String): AppResult<House?>
    suspend fun saveHouse(house: House): AppResult<House>
    suspend fun deleteHouse(id: String): AppResult<Unit>
    suspend fun getHouseCount(): Int
    suspend fun searchHouses(query: String): AppResult<List<House>>
    suspend fun seedFromAssetsIfEmpty(): AppResult<Int>
}
