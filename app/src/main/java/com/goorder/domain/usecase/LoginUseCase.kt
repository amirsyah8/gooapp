package com.goorder.domain.usecase

import com.goorder.domain.model.User
import com.goorder.domain.repository.AuthRepository

class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, pass: String): Result<User> {
        return repository.login(email, pass).onSuccess { user ->
            repository.saveUser(user)
        }
    }
}
