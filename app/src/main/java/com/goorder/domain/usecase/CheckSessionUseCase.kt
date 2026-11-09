package com.goorder.domain.usecase

import com.goorder.domain.model.User
import com.goorder.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

class CheckSessionUseCase(private val repository: AuthRepository) {
    fun getSavedUser(): Flow<User?> = repository.getSavedUser()
    
    suspend fun syncUser(): Result<User> {
        return repository.getCurrentUser().onSuccess { user ->
            repository.saveUser(user)
        }
    }
}
