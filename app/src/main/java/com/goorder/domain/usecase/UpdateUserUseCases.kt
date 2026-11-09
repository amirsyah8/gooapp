package com.goorder.domain.usecase

import com.goorder.domain.model.User
import com.goorder.domain.repository.AuthRepository

class UpdateFnameUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(fname: String): Result<User> {
        return repository.updateFname(fname).onSuccess { repository.saveUser(it) }
    }
}

class UpdateAddressUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(address: String): Result<User> {
        return repository.updateAddress(address).onSuccess { repository.saveUser(it) }
    }
}

class UpdateBioUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(bio: String): Result<User> {
        return repository.updateBio(bio).onSuccess { repository.saveUser(it) }
    }
}

class UpdateLanguageUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(language: Int): Result<User> {
        return repository.updateLanguage(language).onSuccess { repository.saveUser(it) }
    }
}

class UpdateAvatarUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(avatarUuid: String): Result<User> {
        return repository.updateAvatar(avatarUuid).onSuccess { repository.saveUser(it) }
    }
}

class UploadFileUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(bytes: ByteArray, fileName: String): Result<String> {
        return repository.uploadTempFile(bytes, fileName)
    }
}
