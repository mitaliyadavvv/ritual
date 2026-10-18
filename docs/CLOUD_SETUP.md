# Turning on Google sign-in and cloud backup

The code is in place. Until these steps are done, Ritual builds and runs exactly as before, and the
Account section in Settings stays hidden. Everything below happens in your Google/Firebase account.
No passwords, keys or tokens ever need to be pasted anywhere except the Firebase console.

## 1. Create the Firebase project (about 5 minutes)
1. Open https://console.firebase.google.com → **Add project** → name it (e.g. `ritual`).
   Google Analytics isn't needed; you can turn it off.
2. In the project: **Build → Authentication → Get started → Sign-in method → Google → Enable**.
   Choose a project support email and **Save**.
3. **Build → Firestore Database → Create database** → *Start in production mode* → choose a location
   near you (this can't be changed later).
4. **Firestore Database → Rules**: replace the contents with the file `firestore.rules` from this
   repository, then **Publish**.

## 2. Register the Android app
1. **Project settings (gear) → General → Your apps → Add app → Android**.
2. Android package name: `app.ritualhabits`.
3. Debug signing certificate SHA-1 (this Mac's debug key, used by Android Studio builds):
   `FE:B2:27:EB:3B:D8:7A:CE:7E:88:11:E3:B0:04:2F:5F:F8:DF:A4:49`
   Also add the SHA-256 under *Project settings → Your apps → Add fingerprint*:
   `A5:9B:CD:69:3D:5F:2B:51:B4:77:51:6D:9A:EF:5C:7B:7B:34:80:02:B4:B5:08:2C:AF:EB:3B:E1:92:9E:66:39`
4. Download **google-services.json** and save it as `app/google-services.json` in this project.
   It's already git-ignored, so it won't be committed. Do this *after* step 1.2 (enabling Google
   sign-in), so the file contains the Web OAuth client the app needs. The build says so if it doesn't.
5. Rebuild and run. Settings now shows **Account → Continue with Google**.

## 3. Before a Play Store release
- When a release signing key exists, add its SHA-1 and SHA-256 too (step 2.3). With Play App Signing,
  also add the **app signing key** fingerprints from Play Console → *Setup → App signing*.
- **Google Cloud console → APIs & Services → OAuth consent screen**: set the app name, support email
  and, if publishing broadly, a privacy policy link. Only basic scopes (email, profile) are used, so no
  Google verification review is required.
- Optional hardening: **Firebase → App Check** with Play Integrity.

## 4. Check it works
1. On a phone or emulator with a Google account, open Settings → **Continue with Google**.
2. You should see your name and email, then "Backed up just now".
3. In the Firebase console → Firestore → `users/<id>/habits` you'll see your habits.

## Local testing without a real project
`tools/firestore-rules-test` checks the access rules against the Firestore emulator
(`npm install && npm test`, needs Java 11+). For an end-to-end run against the Auth + Firestore
emulators, build with
`./gradlew :app:assembleDebug -Pritual.firebaseEmulatorHost=10.0.2.2 -Pritual.appIdSuffix=.e2e`
(installs as a separate app, `app.ritualhabits.e2e`) and start
`firebase emulators:start --only auth,firestore --project demo-ritual`. In that build the sign-in
button uses a test account named in the app's `files/e2e_account`.
