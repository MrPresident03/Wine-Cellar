package com.example.data

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Real sign-in with Firebase Authentication (email + password). */
class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    val authState: Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val state = if (user == null) {
                AuthState.SignedOut
            } else {
                AuthState.SignedIn(user.uid, user.email ?: "")
            }
            trySend(state)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    suspend fun resetPassword(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    fun signOut() {
        auth.signOut()
    }

    companion object {
        fun friendlyError(e: Throwable): String = when (e) {
            is FirebaseAuthWeakPasswordException -> "That password is too weak. Use at least 6 characters."
            is FirebaseAuthUserCollisionException -> "An account with that email already exists. Use Sign in instead."
            is FirebaseAuthInvalidUserException -> "No account found for that email. Use Create account first."
            is FirebaseAuthInvalidCredentialsException -> "Email or password is incorrect."
            is FirebaseNetworkException -> "No internet connection. Try again when you're online."
            is FirebaseAuthException -> when (e.errorCode) {
                "ERROR_OPERATION_NOT_ALLOWED" ->
                    "Email sign-in isn't switched on yet. In the Firebase console go to Authentication → Sign-in method and enable Email/Password."
                else -> e.localizedMessage ?: "Sign-in failed."
            }
            else -> e.localizedMessage ?: "Something went wrong."
        }
    }
}
