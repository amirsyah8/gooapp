package com.goorder.ui.screens.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goorder.data.local.SessionManager
import com.goorder.data.remote.AuthRemoteDataSource
import com.goorder.data.repository.AuthRepositoryImpl
import com.goorder.domain.usecase.*
import kotlinx.coroutines.launch

class UserSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepositoryImpl(
        AuthRemoteDataSource(application),
        SessionManager(application)
    )

    private val updateFnameUseCase = UpdateFnameUseCase(repository)
    private val updateAddressUseCase = UpdateAddressUseCase(repository)
    private val updateBioUseCase = UpdateBioUseCase(repository)
    private val updateLanguageUseCase = UpdateLanguageUseCase(repository)
    private val updateAvatarUseCase = UpdateAvatarUseCase(repository)
    private val uploadFileUseCase = UploadFileUseCase(repository)

    var isLoading by mutableStateOf(false)
        private set
    
    var error by mutableStateOf<String?>(null)
        private set

    fun updateFname(fname: String) = performUpdate { updateFnameUseCase(fname) }
    fun updateAddress(address: String) = performUpdate { updateAddressUseCase(address) }
    fun updateBio(bio: String) = performUpdate { updateBioUseCase(bio) }
    fun updateLanguage(language: Int) = performUpdate { updateLanguageUseCase(language) }
    
    fun uploadAvatar(bytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            isLoading = true
            error = null
            uploadFileUseCase(bytes, fileName)
                .onSuccess { uuid ->
                    updateAvatarUseCase(uuid)
                        .onSuccess { isLoading = false }
                        .onFailure { handleError(it) }
                }
                .onFailure { handleError(it) }
        }
    }

    private fun performUpdate(action: suspend () -> Result<Any>) {
        viewModelScope.launch {
            isLoading = true
            error = null
            action().onSuccess {
                isLoading = false
            }.onFailure {
                handleError(it)
            }
        }
    }

    private fun handleError(e: Throwable) {
        error = e.message ?: "Operation failed"
        isLoading = false
    }
}
