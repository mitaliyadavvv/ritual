import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Cloud backup config, read from app/google-services.json (git-ignored; downloaded from the Firebase
 * console). These are public client identifiers, not secrets, but they stay out of the repository.
 * Without the file the app builds and runs exactly as before, with "Backup & sync" unavailable.
 * The Google Services Gradle plugin isn't used, so a missing file never breaks the build.
 */
data class FirebaseClientConfig(val projectId: String, val appId: String, val apiKey: String, val webClientId: String)

fun readFirebaseConfig(packageName: String): FirebaseClientConfig? {
    val file = file("google-services.json").takeIf { it.exists() } ?: return null
    val root = JsonSlurper().parse(file) as Map<*, *>
    val project = root["project_info"] as Map<*, *>
    val client = (root["client"] as List<*>).map { it as Map<*, *> }.firstOrNull {
        val info = (it["client_info"] as Map<*, *>)["android_client_info"] as Map<*, *>
        info["package_name"] == packageName
    } ?: error("app/google-services.json has no Android app for $packageName")
    val oauthClients = buildList {
        addAll((client["oauth_client"] as? List<*>).orEmpty())
        val services = client["services"] as? Map<*, *>
        val invite = services?.get("appinvite_service") as? Map<*, *>
        addAll((invite?.get("other_platform_oauth_client") as? List<*>).orEmpty())
    }.map { it as Map<*, *> }
    // client_type 3 = the "Web client" that Google issues ID tokens for.
    val webClientId = oauthClients.firstOrNull { it["client_type"].toString() == "3" }?.get("client_id")?.toString()
        ?: error("app/google-services.json has no Web OAuth client. Enable Google sign-in in Firebase Authentication, then download the file again.")
    return FirebaseClientConfig(
        projectId = project["project_id"].toString(),
        appId = (client["client_info"] as Map<*, *>)["mobilesdk_app_id"].toString(),
        apiKey = ((client["api_key"] as List<*>).first() as Map<*, *>)["current_key"].toString(),
        webClientId = webClientId,
    )
}

// Local end-to-end testing only: -Pritual.firebaseEmulatorHost=10.0.2.2 talks to the Firebase Local
// Emulator Suite instead of a real project; -Pritual.appIdSuffix=.e2e installs it beside the real app.
val emulatorHost = (findProperty("ritual.firebaseEmulatorHost") as String?).orEmpty()
val appIdSuffix = (findProperty("ritual.appIdSuffix") as String?).orEmpty()
val firebaseConfig = if (emulatorHost.isNotEmpty()) {
    FirebaseClientConfig("demo-ritual", "1:000000000000:android:0000000000000000", "demo-api-key", "")
} else {
    readFirebaseConfig("app.ritualhabits")
}
fun quoted(value: String?) = "\"" + value.orEmpty() + "\""

android {
    namespace = "app.ritualhabits"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.ritualhabits"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "FIREBASE_PROJECT_ID", quoted(firebaseConfig?.projectId))
        buildConfigField("String", "FIREBASE_APP_ID", quoted(firebaseConfig?.appId))
        buildConfigField("String", "FIREBASE_API_KEY", quoted(firebaseConfig?.apiKey))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", quoted(firebaseConfig?.webClientId))
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", quoted(emulatorHost))
    }

    buildTypes {
        debug {
            if (appIdSuffix.isNotEmpty()) applicationIdSuffix = appIdSuffix
        }
        release {
            // R8 + signing are configured in Stage 6.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Exported Room schemas as debug-only assets, so MigrationTestHelper (Robolectric) can build old
    // schema versions. Never part of release builds.
    sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas")
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Compose UI tests need the debug-only ui-test-manifest host activity; the migration test
            // needs the debug-only schema assets.
            if (test.name == "testReleaseUnitTest") {
                test.exclude("**/NavigationUiTest*", "**/TodayJourneyTest*", "**/ManageJourneyTest*", "**/ReminderMigrationTest*", "**/SyncMigrationTest*", "**/OnboardingUiTest*")
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Optional Google sign-in + cloud backup (inactive until app/google-services.json is added).
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
