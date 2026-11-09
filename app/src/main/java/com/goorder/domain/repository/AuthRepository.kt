package com.goorder.domain.repository

import com.goorder.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    suspend fun login(email: String, pass: String): Result<User>
    suspend fun logout(): Result<Unit>
    suspend fun getCurrentUser(): Result<User>
    fun getSavedUser(): Flow<User?>
    suspend fun saveUser(user: User)
    suspend fun clearSession()
    
    // Update User Profile
    suspend fun updateFname(fname: String): Result<User>
    suspend fun updateAddress(address: String): Result<User>
    suspend fun updateBio(bio: String): Result<User>
    suspend fun updateLanguage(language: Int): Result<User>
    suspend fun updateAvatar(avatarUuid: String): Result<User>
    suspend fun uploadTempFile(bytes: ByteArray, fileName: String): Result<String>
}
