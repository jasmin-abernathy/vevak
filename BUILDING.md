# Building VeVak

VeVak is a standard Android Studio / Gradle project using Kotlin, Jetpack Compose and two product flavors (`foss` and `play`).

## Supported toolchain

The project intentionally stays on a conservative, compatible toolchain:

- JDK 17
- Android Gradle Plugin 8.13.2
- Gradle 8.14.5 via the checked-in Gradle Wrapper
- Kotlin 2.2.21
- compileSdk / targetSdk 36
- minSdk 26

The versions above reflect the committed `build.gradle.kts` and `gradle/wrapper/gradle-wrapper.properties`. Verify compatibility and CI before future toolchain upgrades.

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

## Validated APKs from GitHub Actions

The regular Android CI job produces **`fossDebug` for developer checks only**. Its signing key is specific to each GitHub runner, so **it is not an upgradable public beta**.

For repeatable testing, there is a separate **`fossBeta`** build type with the package `com.vevak.app.beta`, signed with a persistent **tester-only** key injected using GitHub Actions encrypted secrets. It is independent of the former `com.vevak.app.debug`, the F-Droid `com.vevak.app` ID and the Play `vevak.lepotager.org` ID.

To set up the key (one time) and trigger a signed APK build, follow **[docs/BETA-SIGNING.md](docs/BETA-SIGNING.md)**.

This workflow never signs an unreviewed PR automatically, requires an explicit manual dispatch from the repository owner, checks the pinned certificate fingerprint and exports the APK plus SHA-256 checksum. A successful build is not a proof of working SMS on all phones, and signing does not bypass Google Play Protect.

## Google Play bundle

The Play release can be signed without placing secrets in the repository. Set
`VEVAK_UPLOAD_STORE_FILE`, `VEVAK_UPLOAD_STORE_PASSWORD`, `VEVAK_UPLOAD_KEY_ALIAS` and
`VEVAK_UPLOAD_KEY_PASSWORD`, then run `./gradlew clean bundlePlayRelease`. See
[`docs/google-play-release-0.3.13.md`](docs/google-play-release-0.3.13.md) for the permission and
Play Console hand-off checklist.

An Actions artifact is a test build, not a signed production release. Do not present it as the future stable/F-Droid release.

## F-Droid release preflight

The `fossRelease` variant is the **only** candidate for the official F-Droid repository; the `play` variant has Google Play Services and a separate application ID. Build the unsigned release without signing secrets:

```bash
./gradlew --no-daemon testFossDebugUnitTest lintFossDebug assembleFossRelease
```

The `F-Droid preflight (FOSS release)` GitHub Actions workflow repeats these checks for incoming packaging PRs. See [`docs/FDROID.md`](docs/FDROID.md) for release tagging, fdroiddata validation, assets, and submission. An unsigned release build is a technical preflight, **not** proof of F-Droid acceptance or SMS functionality on a physical phone.

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

A successful desktop/CI build does not prove that SMS reception/reply, trusted-Wi-Fi continuity, remembered-location fallback, one-slot background refresh, boot re-scheduling, pinned-shortcut cancellation or dual-SIM behaviour works on a real device. These paths still require physical-device tests before a stable public release.
