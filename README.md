# Ritual (native Android)

Monochrome, offline habit tracker. Kotlin · Jetpack Compose · Room · DataStore.
Application ID: `app.ritualhabits`. Design and rules: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Modules
- `core/`: pure Kotlin business rules (habit day, eligibility, streaks, focus timer, validation). No Android.
- `app/`: Android app (Room, DataStore, repositories, UI).

## Build and test
Open this folder in Android Studio (File → Open → `RitualNative`). Or, from a terminal, using
Android Studio's bundled JDK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew test             # all unit tests (core + app)
./gradlew :app:assembleDebug
```

The Room schema is exported to `app/schemas/` and must be committed with every schema change.
