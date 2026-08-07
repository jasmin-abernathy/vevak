VeVak Gradle project structure fix
==================================

Purpose
-------
The current repository contains app/build.gradle.kts but is missing the
root Gradle project files and Gradle Wrapper.

Versions used
-------------
Android Gradle Plugin: 8.10.1
Gradle:                8.11.1
Kotlin:                2.2.21
Java/JDK:              17 or newer (JDK 17 recommended)

Why these versions?
-------------------
- VeVak currently uses compileSdk/targetSdk 36.
- AGP 8.10 supports API 36 and requires Gradle 8.11.1 and JDK 17.
- Kotlin 2.2.21 supports this Gradle/AGP range.
- app/build.gradle.kts already uses org.jetbrains.kotlin.plugin.compose,
  so Kotlin 2.x is required.

How to apply
------------
1. Copy/extract these files at the ROOT of the VeVak repository.
   Do not put them inside app/.

2. You should then have:
   vevak/
     app/
     gradle/
       wrapper/
         gradle-wrapper.properties
     build.gradle.kts
     settings.gradle.kts
     gradle.properties
     bootstrap-gradle-wrapper.ps1

3. Open PowerShell/Android Studio Terminal in the repository root and run:

   powershell -ExecutionPolicy Bypass -File .\bootstrap-gradle-wrapper.ps1

   The script downloads Gradle 8.11.1 from services.gradle.org,
   verifies its official SHA-256 checksum, generates the official wrapper,
   and verifies gradle-wrapper.jar.

4. After it succeeds, the repository should additionally contain:

   gradlew
   gradlew.bat
   gradle/wrapper/gradle-wrapper.jar

5. The bootstrap script itself does NOT need to be committed.
   You may delete bootstrap-gradle-wrapper.ps1 after generating the wrapper.

6. Commit:
   build.gradle.kts
   settings.gradle.kts
   gradle.properties
   gradlew
   gradlew.bat
   gradle/wrapper/gradle-wrapper.jar
   gradle/wrapper/gradle-wrapper.properties

7. Re-open the repository root in Android Studio and run Gradle Sync.

First build target
------------------
After sync succeeds:

   .\gradlew.bat assembleFossDebug

Expected APK location:
   app\build\outputs\apk\foss\debug\

Do not upgrade AGP, Gradle or Kotlin automatically before the first successful
build. First establish a known-good baseline, then update intentionally.
