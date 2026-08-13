package com.aura.app.assistant

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

class GoogleSignInClient(
    context: Context,
    private val serverClientId: String
) {
    private val credentialManager = CredentialManager.create(context)

    suspend fun signIn(activityContext: Context, nonce: String): String {
        if (serverClientId.isBlank()) {
            throw IllegalStateException("Google sign-in is not configured for this build")
        }
        require(nonce.isNotBlank()) { "Google sign-in challenge is missing" }
        val option = GetSignInWithGoogleOption.Builder(serverClientId)
            .setNonce(nonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val credential = try {
            credentialManager.getCredential(activityContext, request).credential
        } catch (error: GetCredentialCancellationException) {
            throw IllegalStateException("Google sign-in was cancelled", error)
        } catch (error: GetCredentialException) {
            throw IllegalStateException("Google sign-in could not be completed", error)
        }
        if (credential !is CustomCredential ||
            credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            throw IllegalStateException("Google returned an unsupported credential")
        }
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (error: GoogleIdTokenParsingException) {
            throw IllegalStateException("Google returned an invalid credential", error)
        }
    }
}
