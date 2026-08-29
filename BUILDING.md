# Building VeVak

VeVak is a standard Android Studio / Gradle project using Kotlin, Jetpack Compose and two product flavors (`foss` and `play`).

## Supported toolchain

The project intentionally stays on a conservative, compatible toolchain:

- JDK 17
- Android Gradle Plugin 8.10.1
- Gradle 8.11.1 via the checked-in Gradle Wrapper
- Kotlin 2.2.21
- compileSdk / targetSdk 36
- minSdk 26

AGP 8.10 officially requires Gradle 8.11.1 and JDK 17, and supports API 36. Kotlin 2.2 is compatible with AGP 8.10.

## Open in Android Studio

1. Clone the repository.
2. In Android Studio, choose **Open** and select the repository root (the directory containing `settings.gradle.kts`).
3. Let Android Studio use its bundled JDK 17 / `GRADLE_LOCAL_JAVA_HOME`, or explicitly select JDK 17 for Gradle.
4. Install Android SDK Platform 36 when prompted.
5. Wait for Gradle Sync to finish.
6. Choose a build variant:
   - `fossDebug` for the canonical FOSS build;
   - `playDebug` for the optional Google Play Services location build.

Do not create or commit a hand-written `local.properties`. Android Studio creates it locally with the SDK path and `.gitignore` excludes it.

## Command-line verification

macOS / Linux:

```bash
./gradlew testFossDebugUnitTest assembleFossDebug lintFossDebug
./gradlew testPlayDebugUnitTest assemblePlayDebug lintPlayDebug
```

Windows PowerShell / Command Prompt:

```bat
gradlew.bat testFossDebugUnitTest assembleFossDebug lintFossDebug
gradlew.bat testPlayDebugUnitTest assemblePlayDebug lintPlayDebug
```

## Validated tester APK from GitHub Actions

The `Android CI` workflow runs the static privacy/ecodesign checks, unit tests, builds and lint for both flavors.

For successful runs on `main` (and manual non-PR runs), the workflow then publishes:

```text
vevak-foss-debug-<commit SHA>
└── app-foss-debug.apk
```

as a GitHub Actions artifact with a short retention period. This artifact is deliberately published **only after all FOSS and Play verification steps have succeeded**, so the APK used for a real-device regression test can be tied to an exact validated commit.

For the 0.3.1 resilience beta, verify the app reports version `0.3.1` / versionCode `4` before using test results to close the location-off regression.

An Actions artifact is a test build, not a signed production release. Do not present it as the future stable/F-Droid release.

## Gradle Wrapper

The following wrapper files are source-controlled and must stay together:

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

Do not download an arbitrary `gradle-wrapper.jar` from a third-party repository. Regenerate the wrapper with the trusted Gradle distribution when upgrading Gradle.

## Local files that must not be committed

Examples include:

- `.idea/`
- `.gradle/`
- `.kotlin/`
- `local.properties`
- module/root `build/` directories
- signing keystores and `keystore.properties`

## Release note

A successful desktop/CI build does not prove that SMS reception/reply, trusted-Wi-Fi continuity, remembered-location fallback, background execution or dual-SIM behaviour works on a real device. These paths still require physical-device tests.
