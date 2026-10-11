package com.example.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Thrown when google-services.json doesn't contain the Google sign-in (web client) entry yet. */
class GoogleSignInNotConfiguredException : Exception(
    "Google sign-in isn't set up in this build yet. In Firebase → Project settings, add the app's SHA-1 " +
        "fingerprint, download google-services.json again and upload it to the app folder on GitHub."
)

/**
 * Shows the "Sign in with Google" sheet and returns a Google ID token for Firebase.
 *
 * The web client ID comes from google-services.json (the Google Services plugin turns it into the
 * string resource "default_web_client_id"). It's looked up by name so the app still builds, and email
 * sign-in still works, if that file hasn't been updated yet.
 */
object GoogleSignIn {

    fun webClientId(context: Context): String? {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId == 0) return null
        return context.getString(resId).takeIf { it.isNotBlank() }
    }

    /** [activityContext] must be an Activity so the Google account picker can be shown. */
    suspend fun getIdToken(activityContext: Context): String {
        val clientId = webClientId(activityContext) ?: throw GoogleSignInNotConfiguredException()
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
        val credential = response.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Google returned an unexpected sign-in type.")
    }
}
