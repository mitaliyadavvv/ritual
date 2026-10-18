package app.ritualhabits.data.sync

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.io.File

/** Outcome of the "Continue with Google" account picker. */
sealed interface GoogleCredentialResult {
    data class Token(val idToken: String) : GoogleCredentialResult
    data object Cancelled : GoogleCredentialResult
    data object NoAccount : GoogleCredentialResult
    data class Failed(val error: Throwable?) : GoogleCredentialResult
}

fun interface GoogleCredentialSource {
    /** Shows the system Google account picker. [activityContext] must be an Activity. */
    suspend fun request(activityContext: Context): GoogleCredentialResult
}

/**
 * Credential Manager "Sign in with Google": asks only for the basic profile (openid, email, name).
 * No Drive, Contacts or other Google scopes are requested.
 */
class CredentialManagerGoogleSource(private val webClientId: String) : GoogleCredentialSource {
    override suspend fun request(activityContext: Context): GoogleCredentialResult {
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleCredentialResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleCredentialResult.Failed(null)
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleCredentialResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleCredentialResult.NoAccount
        } catch (e: GetCredentialException) {
            GoogleCredentialResult.Failed(e)
        } catch (e: Exception) {
            GoogleCredentialResult.Failed(e)
        }
    }
}

/**
 * Local end-to-end testing against the Firebase Auth emulator only (debug builds made with
 * -Pritual.firebaseEmulatorHost). The emulator accepts an unsigned token naming a test account,
 * read from files/e2e_account. Never used with a real Firebase project.
 */
class EmulatorGoogleSource : GoogleCredentialSource {
    override suspend fun request(activityContext: Context): GoogleCredentialResult {
        val name = File(activityContext.filesDir, "e2e_account").takeIf { it.exists() }?.readText()?.trim()
            ?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: return GoogleCredentialResult.Cancelled
        return GoogleCredentialResult.Token(
            """{"sub":"$name-sub","email":"$name@example.com","email_verified":true,"name":"${name.replaceFirstChar(Char::uppercase)} Test"}""",
        )
    }
}
