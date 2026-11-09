package com.goorder.data.repository

import com.goorder.data.local.SessionManager
import com.goorder.data.remote.AuthRemoteDataSource
import com.goorder.data.remote.dto.toDomain
import com.goorder.domain.model.User
import com.goorder.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class AuthRepositoryImpl(
    private val remoteDataSource: AuthRemoteDataSource,
    private val sessionManager: SessionManager
) : AuthRepository {

    override suspend fun login(email: String, pass: String): Result<User> {
        return remoteDataSource.login(email, pass).map { it.toDomain() }
    }

    override suspend fun logout(): Result<Unit> {
        return remoteDataSource.logout().map { }
    }

    override suspend fun getCurrentUser(): Result<User> {
        return remoteDataSource.getCurrentUser().map { it.toDomain() }
    }

    override fun getSavedUser(): Flow<User?> {
        return sessionManager.userData
    }

    override suspend fun saveUser(user: User) {
        sessionManager.saveUser(user)
    }

    override suspend fun clearSession() {
        sessionManager.clearSession()
    }

    override suspend fun updateFname(fname: String): Result<User> {
        return remoteDataSource.updateFname(fname).map { it.toDomain() }
    }

    override suspend fun updateAddress(address: String): Result<User> {
        return remoteDataSource.updateAddress(address).map { it.toDomain() }
    }

    override suspend fun updateBio(bio: String): Result<User> {
        return remoteDataSource.updateBio(bio).map { it.toDomain() }
    }

    override suspend fun updateLanguage(language: Int): Result<User> {
        return remoteDataSource.updateLanguage(language).map { it.toDomain() }
    }

    override suspend fun updateAvatar(avatarUuid: String): Result<User> {
        return remoteDataSource.updateAvatar(avatarUuid).map { it.toDomain() }
    }

    override suspend fun uploadTempFile(bytes: ByteArray, fileName: String): Result<String> {
        return remoteDataSource.uploadTempFile(bytes, fileName)
    }
}
