package com.goorder.ui.screens.login

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goorder.data.local.SessionManager
import com.goorder.data.remote.AuthRemoteDataSource
import com.goorder.data.remote.SocketManager
import com.goorder.data.repository.AuthRepositoryImpl
import com.goorder.domain.model.PrintRequest
import com.goorder.domain.model.User
import com.goorder.domain.usecase.CheckSessionUseCase
import com.goorder.domain.usecase.LoginUseCase
import com.goorder.domain.usecase.LogoutUseCase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data object Unauthenticated : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    
    // Manual DI - In a real app, use Hilt or Koin
    private val remoteDataSource = AuthRemoteDataSource(application)
    private val sessionManager = SessionManager(application)
    private val repository = AuthRepositoryImpl(remoteDataSource, sessionManager)
    private val socketManager = SocketManager()
    
    private val loginUseCase = LoginUseCase(repository)
    private val logoutUseCase = LogoutUseCase(repository)
    private val checkSessionUseCase = CheckSessionUseCase(repository)
    
    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set

    var printRequests by mutableStateOf<List<PrintRequest>>(emptyList())
        private set
        
    var isAutoPrintEnabled by mutableStateOf(false)
        private set

    init {
        observeSession()
        syncSession()
    }

    private fun observeSession() {
        viewModelScope.launch {
            checkSessionUseCase.getSavedUser().collectLatest { user ->
                if (user != null) {
                    authState = AuthState.Authenticated(user)
                    // Connect socket when authenticated
                    socketManager.connect(
                        onNotificationReceived = {
                            syncSession()
                        },
                        onPrintQrReceived = { data ->
                            val newRequest = PrintRequest(data = data)
                            printRequests = printRequests + newRequest
                        }
                    )
                    socketManager.join(user.id)
                } else {
                    authState = AuthState.Unauthenticated
                    // Disconnect socket when not authenticated
                    socketManager.disconnect()
                    printRequests = emptyList()
                }
            }
        }
    }

    fun clearPrintRequests() {
        // Hanya padam yang sudah diprint
        printRequests = printRequests.filter { !it.isPrinted }
    }

    fun deletePrintRequest(id: String) {
        printRequests = printRequests.filter { it.id != id }
    }

    fun toggleAutoPrint(enabled: Boolean) {
        isAutoPrintEnabled = enabled
    }

    fun markAsPrinted(requestId: String) {
        printRequests = printRequests.map {
            if (it.id == requestId) it.copy(isPrinted = true) else it
        }
    }

    private fun syncSession() {
        viewModelScope.launch {
            checkSessionUseCase.syncUser()
                .onFailure {
                    // Optional: handle background sync failure
                }
        }
    }

    fun login(email: String, pass: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            loginUseCase(email, pass)
                .onSuccess {
                    onResult(null)
                }
                .onFailure { error ->
                    onResult(error.message ?: "Login gagal")
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
        }
    }
}
