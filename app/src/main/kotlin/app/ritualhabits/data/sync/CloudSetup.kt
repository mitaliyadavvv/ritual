package app.ritualhabits.data.sync

import android.content.Context
import android.util.Log
import app.ritualhabits.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

/** Firebase services for backup, or null when this build has no Firebase config (feature hidden). */
class CloudServices(val auth: AuthGateway, val store: CloudStore, val credentials: GoogleCredentialSource)

object CloudSetup {
    val isConfigured: Boolean
        get() = BuildConfig.FIREBASE_PROJECT_ID.isNotEmpty() &&
            (BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotEmpty() || BuildConfig.FIREBASE_EMULATOR_HOST.isNotEmpty())

    fun create(context: Context): CloudServices? {
        if (!isConfigured) return null
        return try {
            val options = FirebaseOptions.Builder()
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .build()
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
                ?: FirebaseApp.initializeApp(context, options, APP_NAME)
            val auth = FirebaseAuth.getInstance(app)
            val firestore = FirebaseFirestore.getInstance(app)
            val emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST
            if (emulatorHost.isNotEmpty()) {
                auth.useEmulator(emulatorHost, 9099)
                firestore.useEmulator(emulatorHost, 8180)
            }
            // The Room database is the offline copy; Firestore keeps no second cache on disk.
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            CloudServices(
                auth = FirebaseAuthGateway(auth, context.applicationContext),
                store = FirestoreCloudStore(firestore),
                credentials = if (emulatorHost.isNotEmpty()) EmulatorGoogleSource() else CredentialManagerGoogleSource(BuildConfig.GOOGLE_WEB_CLIENT_ID),
            )
        } catch (e: Exception) {
            Log.w("Ritual", "Cloud backup unavailable", e)
            null
        }
    }

    private const val APP_NAME = "ritual"
}
