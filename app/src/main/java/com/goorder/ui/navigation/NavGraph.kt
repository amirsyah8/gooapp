package com.goorder.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.goorder.ui.screens.login.AuthState
import com.goorder.ui.screens.login.AuthViewModel
import com.goorder.ui.screens.login.LoginScreen
import com.goorder.ui.screens.printer.PrinterScreen
import com.goorder.ui.screens.user.UserScreen

@Composable
fun NavGraph(
    authViewModel: AuthViewModel = viewModel()
) {
    val authState = authViewModel.authState

    LaunchedEffect(authState) {
        Log.d("NavGraph", "Current AuthState: $authState")
    }

    when (authState) {
        is AuthState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is AuthState.Authenticated -> {
            if (authState.user.role?.option == "printer") {
                PrinterScreen(
                    user = authState.user,
                    authViewModel = authViewModel,
                    onLogout = { authViewModel.logout() }
                )
            } else {
                UserScreen(
                    userName = authState.user.name ?: "User",
                    onLogout = { authViewModel.logout() }
                )
            }
        }
        is AuthState.Unauthenticated -> {
            LoginScreen(
                authViewModel = authViewModel
            )
        }
    }
}
