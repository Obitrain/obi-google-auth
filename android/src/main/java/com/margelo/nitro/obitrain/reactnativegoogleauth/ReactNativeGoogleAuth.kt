package com.margelo.nitro.obitrain.reactnativegoogleauth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
import com.facebook.react.bridge.ActivityEventListener
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Nitro bridge: Credential Manager for sign-in, AuthorizationClient for scopes/auth code. */
@DoNotStrip
class ReactNativeGoogleAuth : HybridReactNativeGoogleAuthSpec() {
  private var webClientId: String? = null
  private val executor = Executors.newSingleThreadExecutor()
  private val mainHandler = Handler(Looper.getMainLooper())
  private var pendingAuthorize: ((AuthorizationResult?, GoogleAuthErrorCode?, String?) -> Unit)? =
    null

  private val context: Context?
    get() = NitroModules.applicationContext

  private val activity: Activity?
    get() = NitroModules.applicationContext?.currentActivity

  init {
    // the AuthorizationClient consent screen returns through onActivityResult
    NitroModules.applicationContext?.addActivityEventListener(object : ActivityEventListener {
      override fun onActivityResult(
        activity: Activity, requestCode: Int, resultCode: Int, data: Intent?
      ) {
        if (requestCode != AUTHORIZE_REQUEST_CODE) return
        val done = pendingAuthorize ?: return
        pendingAuthorize = null
        if (resultCode != Activity.RESULT_OK || data == null) {
          done(null, GoogleAuthErrorCode.CANCELLED, "Authorization cancelled")
          return
        }
        try {
          done(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data), null, null)
        } catch (e: ApiException) {
          done(null, GoogleAuthErrorCode.ERROR, e.message ?: "Authorization failed")
        }
      }

      override fun onNewIntent(intent: Intent) = Unit
    })
  }

  override fun configure(webClientId: String, iosClientId: String?) {
    this.webClientId = webClientId
  }

  override fun signIn(options: GoogleSignInOptions?): Promise<GoogleSignInResult> {
    val promise = Promise<GoogleSignInResult>()
    val clientId = webClientId
    if (clientId == null) {
      promise.resolve(errorResult(GoogleAuthErrorCode.ERROR, "configure() was not called"))
      return promise
    }
    val activity = activity
    if (activity == null) {
      promise.resolve(errorResult(GoogleAuthErrorCode.ERROR, "No foreground activity"))
      return promise
    }
    val wantsAuthorization =
      options?.offlineAccess == true || options?.scopes?.isNotEmpty() == true

    credentialSignIn(activity, clientId, filterAuthorized = false) { result ->
      if (result.user == null || !wantsAuthorization) {
        promise.resolve(result)
        return@credentialSignIn
      }
      authorize(activity, clientId, options!!) { auth, code, message ->
        if (auth == null) {
          promise.resolve(errorResult(code ?: GoogleAuthErrorCode.ERROR, message ?: "Authorization failed"))
        } else {
          promise.resolve(
            result.copy(
              serverAuthCode = auth.serverAuthCode,
              grantedScopes = auth.grantedScopes.toTypedArray()
            )
          )
        }
      }
    }
    return promise
  }

  override fun signInSilently(): Promise<GoogleSignInResult> {
    val promise = Promise<GoogleSignInResult>()
    val clientId = webClientId
    if (clientId == null) {
      promise.resolve(errorResult(GoogleAuthErrorCode.ERROR, "configure() was not called"))
      return promise
    }
    val ctx = activity ?: context
    if (ctx == null) {
      promise.resolve(errorResult(GoogleAuthErrorCode.ERROR, "No context"))
      return promise
    }
    credentialSignIn(ctx, clientId, filterAuthorized = true) { promise.resolve(it) }
    return promise
  }

  override fun signOut(): Promise<Unit> {
    val promise = Promise<Unit>()
    val ctx = context
    if (ctx == null) {
      promise.resolve(Unit)
      return promise
    }
    clearCredentialState(ctx) { promise.resolve(Unit) }
    return promise
  }

  override fun revokeAccess(): Promise<Unit> {
    val promise = Promise<Unit>()
    val ctx = context
    if (ctx == null) {
      promise.resolve(Unit)
      return promise
    }
    val finish = { clearCredentialState(ctx) { promise.resolve(Unit) } }
    val activity = activity
    val clientId = webClientId
    if (activity == null || clientId == null) {
      finish()
      return promise
    }
    // best-effort: revoke the silently-obtainable access token, then clear state
    val request = AuthorizationRequest.builder()
      .setRequestedScopes(listOf(Scope("email")))
      .build()
    Identity.getAuthorizationClient(activity).authorize(request)
      .addOnSuccessListener(executor) { result ->
        val token = if (result.hasResolution()) null else result.accessToken
        if (token != null) revokeToken(token)
        finish()
      }
      .addOnFailureListener { finish() }
    return promise
  }

  override fun checkPlayServices(showDialog: Boolean): Promise<Boolean> {
    val promise = Promise<Boolean>()
    val ctx = context
    if (ctx == null) {
      promise.resolve(false)
      return promise
    }
    val availability = GoogleApiAvailability.getInstance()
    if (availability.isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS) {
      promise.resolve(true)
      return promise
    }
    val activity = activity
    if (!showDialog || activity == null) {
      promise.resolve(false)
      return promise
    }
    mainHandler.post {
      availability.makeGooglePlayServicesAvailable(activity)
        .addOnCompleteListener { task -> promise.resolve(task.isSuccessful) }
    }
    return promise
  }

  private fun credentialSignIn(
    ctx: Context,
    clientId: String,
    filterAuthorized: Boolean,
    done: (GoogleSignInResult) -> Unit
  ) {
    val option = GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(filterAuthorized)
      .setServerClientId(clientId)
      .setAutoSelectEnabled(filterAuthorized)
      .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    CredentialManager.create(ctx).getCredentialAsync(
      ctx, request, null, executor,
      object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
        override fun onResult(result: GetCredentialResponse) = done(toResult(result))

        override fun onError(e: GetCredentialException) {
          val code = when (e) {
            is GetCredentialCancellationException -> GoogleAuthErrorCode.CANCELLED
            is NoCredentialException -> GoogleAuthErrorCode.NOCREDENTIAL
            is GetCredentialProviderConfigurationException ->
              GoogleAuthErrorCode.PLAYSERVICESNOTAVAILABLE
            else -> GoogleAuthErrorCode.ERROR
          }
          done(errorResult(code, e.message ?: e.type))
        }
      }
    )
  }

  private fun authorize(
    activity: Activity,
    clientId: String,
    options: GoogleSignInOptions,
    done: (AuthorizationResult?, GoogleAuthErrorCode?, String?) -> Unit
  ) {
    val scopeNames = options.scopes?.toList().orEmpty().ifEmpty { listOf("openid", "email", "profile") }
    val builder = AuthorizationRequest.builder().setRequestedScopes(scopeNames.map { Scope(it) })
    if (options.offlineAccess == true) builder.requestOfflineAccess(clientId)
    Identity.getAuthorizationClient(activity).authorize(builder.build())
      .addOnSuccessListener(executor) { result ->
        if (!result.hasResolution()) {
          done(result, null, null)
          return@addOnSuccessListener
        }
        if (pendingAuthorize != null) {
          done(null, GoogleAuthErrorCode.INPROGRESS, "An authorization is already running")
          return@addOnSuccessListener
        }
        pendingAuthorize = done
        mainHandler.post {
          try {
            activity.startIntentSenderForResult(
              result.pendingIntent!!.intentSender, AUTHORIZE_REQUEST_CODE, null, 0, 0, 0
            )
          } catch (e: Exception) {
            pendingAuthorize = null
            done(null, GoogleAuthErrorCode.ERROR, e.message ?: "Could not launch authorization")
          }
        }
      }
      .addOnFailureListener { e -> done(null, GoogleAuthErrorCode.ERROR, e.message ?: "Authorization failed") }
  }

  private fun clearCredentialState(ctx: Context, done: () -> Unit) {
    CredentialManager.create(ctx).clearCredentialStateAsync(
      ClearCredentialStateRequest(), null, executor,
      object : CredentialManagerCallback<Void?, ClearCredentialException> {
        override fun onResult(result: Void?) = done()

        // best-effort: nothing actionable for the caller
        override fun onError(e: ClearCredentialException) = done()
      }
    )
  }

  /** Blocking; call from the executor thread only. */
  private fun revokeToken(token: String) {
    try {
      val conn = URL("https://oauth2.googleapis.com/revoke").openConnection() as HttpURLConnection
      conn.requestMethod = "POST"
      conn.doOutput = true
      conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      conn.outputStream.use { it.write("token=$token".toByteArray()) }
      conn.responseCode
      conn.disconnect()
    } catch (_: Exception) {
      // best-effort
    }
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
        givenName = google.givenName,
        familyName = google.familyName,
        photoUrl = google.profilePictureUri?.toString()
      ),
      serverAuthCode = null,
      grantedScopes = null,
      errorCode = null,
      errorMessage = null
    )
  }

  private fun errorResult(code: GoogleAuthErrorCode, message: String) =
    GoogleSignInResult(
      user = null,
      serverAuthCode = null,
      grantedScopes = null,
      errorCode = code,
      errorMessage = message
    )

  companion object {
    private const val AUTHORIZE_REQUEST_CODE = 91427
  }
}
