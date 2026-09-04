package com.margelo.nitro.obitrain.reactnativegoogleauth

import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.facebook.proguard.annotations.DoNotStrip
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import java.util.concurrent.Executors

/** Nitro bridge over the Credential Manager API. */
@DoNotStrip
class ReactNativeGoogleAuth : HybridReactNativeGoogleAuthSpec() {
  private var webClientId: String? = null
  private val executor = Executors.newSingleThreadExecutor()

  override fun configure(webClientId: String, iosClientId: String?) {
    this.webClientId = webClientId
  }

  override fun signIn(): Promise<GoogleSignInResult> {
    val promise = Promise<GoogleSignInResult>()
    val clientId = webClientId
    if (clientId == null) {
      promise.resolve(errorResult(GoogleAuthErrorCode.ERROR, "configure() was not called"))
      return promise
    }
    val activity = NitroModules.applicationContext?.currentActivity
    if (activity == null) {
      promise.resolve(errorResult(GoogleAuthErrorCode.ERROR, "No foreground activity"))
      return promise
    }

    val option = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(false)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(false)
      .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    CredentialManager.create(activity).getCredentialAsync(
      activity, request, null, executor,
      object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
        override fun onResult(result: GetCredentialResponse) {
          promise.resolve(toResult(result))
        }

        override fun onError(e: GetCredentialException) {
          val code = when (e) {
            is GetCredentialCancellationException -> GoogleAuthErrorCode.CANCELLED
            is NoCredentialException -> GoogleAuthErrorCode.NOCREDENTIAL
            is GetCredentialProviderConfigurationException ->
              GoogleAuthErrorCode.PLAYSERVICESNOTAVAILABLE
            else -> GoogleAuthErrorCode.ERROR
          }
          promise.resolve(errorResult(code, e.message ?: e.type))
        }
      }
    )
    return promise
  }

  override fun signOut(): Promise<Unit> {
    val promise = Promise<Unit>()
    val context = NitroModules.applicationContext
    if (context == null) {
      promise.resolve(Unit)
      return promise
    }
    CredentialManager.create(context).clearCredentialStateAsync(
      ClearCredentialStateRequest(), null, executor,
      object : CredentialManagerCallback<Void?, ClearCredentialException> {
        override fun onResult(result: Void?) = promise.resolve(Unit)

        // best-effort: nothing actionable for the caller
        override fun onError(e: ClearCredentialException) = promise.resolve(Unit)
      }
    )
    return promise
  }

  private fun toResult(response: GetCredentialResponse): GoogleSignInResult {
    val credential = response.credential
    if (credential !is CustomCredential ||
      credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
      return errorResult(GoogleAuthErrorCode.ERROR, "Unexpected credential type: ${credential.type}")
    }
    val google = GoogleIdTokenCredential.createFrom(credential.data)
    return GoogleSignInResult(
      user = GoogleAuthUser(
        idToken = google.idToken,
        email = google.id,
        name = google.displayName,
        photoUrl = google.profilePictureUri?.toString()
      ),
      errorCode = null,
      errorMessage = null
    )
  }

  private fun errorResult(code: GoogleAuthErrorCode, message: String) =
    GoogleSignInResult(user = null, errorCode = code, errorMessage = message)
}
