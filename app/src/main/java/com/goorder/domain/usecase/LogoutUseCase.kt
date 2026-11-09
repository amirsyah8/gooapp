package com.goorder.domain.usecase

import com.goorder.domain.repository.AuthRepository

class LogoutUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(): Result<Unit> {
        return repository.logout().also {
            repository.clearSession()
        }
    }
}
