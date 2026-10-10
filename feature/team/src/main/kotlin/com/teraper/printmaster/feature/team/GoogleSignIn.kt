package com.teraper.printmaster.feature.team

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

/**
 * "Sign in with Google": shows Google's account sheet and hands back the ID token
 * (null when cancelled, failed, or sign-in isn't set up in this build).
 */
@Composable
internal fun rememberGoogleSignIn(clientId: String?, onResult: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return {
        if (clientId == null) {
            onResult(null)
        } else {
            scope.launch {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
                    .build()
                val token = try {
                    val credential = CredentialManager.create(context).getCredential(context, request).credential
                    if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        GoogleIdTokenCredential.createFrom(credential.data).idToken
                    } else {
                        null
                    }
                } catch (e: GetCredentialException) {
                    null
                }
                onResult(token)
            }
        }
    }
}
