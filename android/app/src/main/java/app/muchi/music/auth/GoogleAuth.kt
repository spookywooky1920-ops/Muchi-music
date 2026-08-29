package app.muchi.music.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import app.muchi.music.BuildConfig
import app.muchi.music.data.GoogleUser
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleAuth {
    suspend fun signIn(ctx: Context): GoogleUser {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (clientId.isBlank()) {
            throw IllegalStateException(
                "Add a Google Cloud OAuth Web client ID as GitHub secret GOOGLE_WEB_CLIENT_ID, then rebuild."
            )
        }
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()
        val cm = CredentialManager.create(ctx)
        try {
            val result = cm.getCredential(ctx, request)
            val cred = result.credential
            if (cred is CustomCredential &&
                cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val id = GoogleIdTokenCredential.createFrom(cred.data)
                return GoogleUser(
                    id = id.id,
                    name = id.displayName ?: "Google user",
                    email = id.id,
                    photo = id.profilePictureUri?.toString().orEmpty(),
                )
            }
            throw IllegalStateException("Google did not return an ID token")
        } catch (e: GetCredentialException) {
            throw IllegalStateException(e.message ?: "Google sign-in cancelled")
        }
    }
}
