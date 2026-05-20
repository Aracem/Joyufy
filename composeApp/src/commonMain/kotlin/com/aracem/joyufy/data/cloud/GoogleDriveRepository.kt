package com.aracem.joyufy.data.cloud

import kotlinx.coroutines.flow.StateFlow

interface GoogleDriveRepository {
    val authState: StateFlow<AuthState>
    suspend fun signIn()
    suspend fun signOut()
    suspend fun upload(json: String): Result<Unit>
    suspend fun download(): Result<String>
}

sealed interface AuthState {
    data object Unauthenticated : AuthState
    data object Authenticating : AuthState
    data class Authenticated(val email: String) : AuthState
}
