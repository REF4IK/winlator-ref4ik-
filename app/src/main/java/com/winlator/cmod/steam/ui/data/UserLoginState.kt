package com.winlator.cmod.steam.ui.data
import com.winlator.cmod.steam.enums.LoginResult
import com.winlator.cmod.steam.enums.LoginScreen

data class UserLoginState(
    val username: String = "",
    val password: String = "",
    val twoFactorCode: String = "",
    val rememberSession: Boolean = true,
    val isLoggingIn: Boolean = false,
    val isSteamConnected: Boolean = false,
    val loginResult: LoginResult = LoginResult.Failed,
    val loginScreen: LoginScreen = LoginScreen.CREDENTIAL,
    val qrCode: String? = null,
    val isQrFailed: Boolean = false,
    val previousCodeIncorrect: Boolean = false,
    val email: String? = null,
    val lastTwoFactorMethod: String? = null
)
