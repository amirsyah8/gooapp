package com.goorder.ui.screens.login

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goorder.data.local.SessionManager
import com.goorder.data.remote.AuthRemoteDataSource
import com.goorder.data.repository.AuthRepositoryImpl
import com.goorder.domain.model.User
import com.goorder.domain.usecase.CheckSessionUseCase
import com.goorder.domain.usecase.LoginUseCase
import com.goorder.domain.usecase.LogoutUseCase
import com.goorder.service.BluetoothPrinterService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Loading : AuthState()
    data class Authenticated(val user: User) : AuthState()
    data object Unauthenticated : AuthState()
}

/**
 * AuthViewModel — HANYA tanggungjawab pengesahan (authentication).
 *
 * Skop: auth state, login, logout, session management.
 * BUKAN: orders, print queue, socket, MQTT — itu di OrderViewModel & PrintQueueViewModel.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val remoteDataSource = AuthRemoteDataSource(application)
    private val sessionManager = SessionManager(application)
    private val repository = AuthRepositoryImpl(remoteDataSource, sessionManager)

    private val loginUseCase = LoginUseCase(repository)
    private val logoutUseCase = LogoutUseCase(repository)
    private val checkSessionUseCase = CheckSessionUseCase(repository)

    var authState by mutableStateOf<AuthState>(AuthState.Loading)
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

                    // Mulakan foreground service pencetak Bluetooth
                    try {
                        BluetoothPrinterService.startService(getApplication())
                    } catch (e: Exception) {
                        Log.e("AuthViewModel", "Gagal memulakan BluetoothPrinterService", e)
                    }
                } else {
                    authState = AuthState.Unauthenticated

                    // Hentikan servis pencetak bila log keluar
                    BluetoothPrinterService.stopService(getApplication())
                }
            }
        }
    }

    /** Segar semula data pengguna dari server. */
    fun syncSession() {
        viewModelScope.launch {
            checkSessionUseCase.syncUser()
                .onFailure {
                    Log.w("AuthViewModel", "Background sync gagal: ${it.message}")
                }
        }
    }

    fun login(email: String, pass: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            loginUseCase(email, pass)
                .onSuccess { onResult(null) }
                .onFailure { error -> onResult(error.message ?: "Login gagal") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            BluetoothPrinterService.stopService(getApplication())
            logoutUseCase()
        }
    }
}
