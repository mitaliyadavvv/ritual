package app.ritualhabits.data.sync

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

/** The signed-in account, as shown in Settings. */
data class AccountUser(val uid: String, val email: String?, val displayName: String?)

enum class SignInFailure { NETWORK, OTHER }

class SignInException(val failure: SignInFailure, cause: Throwable? = null) : Exception(cause)

interface AuthGateway {
    /** The current user; null when signed out. Emits on every change. */
    val user: Flow<AccountUser?>
    /** Exchanges a Google ID token for a session. Throws [SignInException]. */
    suspend fun signInWithGoogle(idToken: String): AccountUser
    suspend fun signOut()
}

/** Firebase Authentication with the Google provider. Only the basic profile (name, email) is used. */
class FirebaseAuthGateway(
    private val auth: FirebaseAuth,
    private val appContext: Context,
) : AuthGateway {

    override val user: Flow<AccountUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.let { u -> AccountUser(u.uid, u.email, u.displayName) }) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signInWithGoogle(idToken: String): AccountUser {
        val result = try {
            auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        } catch (e: FirebaseNetworkException) {
            throw SignInException(SignInFailure.NETWORK, e)
        } catch (e: Exception) {
            throw SignInException(SignInFailure.OTHER, e)
        }
        val u = result.user ?: throw SignInException(SignInFailure.OTHER)
        return AccountUser(u.uid, u.email, u.displayName)
    }

    override suspend fun signOut() {
        auth.signOut()
        // Also forget the chosen Google account, so the next sign-in shows the account picker.
        runCatching { CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest()) }
    }
}
