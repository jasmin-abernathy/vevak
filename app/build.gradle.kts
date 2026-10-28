plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val uploadStoreFile = providers.environmentVariable("VEVAK_UPLOAD_STORE_FILE")
val uploadStorePassword = providers.environmentVariable("VEVAK_UPLOAD_STORE_PASSWORD")
val uploadKeyAlias = providers.environmentVariable("VEVAK_UPLOAD_KEY_ALIAS")
val uploadKeyPassword = providers.environmentVariable("VEVAK_UPLOAD_KEY_PASSWORD")
val hasUploadSigning = listOf(
    uploadStoreFile,
    uploadStorePassword,
    uploadKeyAlias,
    uploadKeyPassword
).all { it.isPresent }

// Persistent *testing-only* signing key, independent of the Play upload key and official F-Droid.
// Never commit the keystore; trusted manual CI runs restore it from GitHub Actions secrets.
val betaStoreFile = providers.environmentVariable("VEVAK_BETA_STORE_FILE")
val betaStorePassword = providers.environmentVariable("VEVAK_BETA_STORE_PASSWORD")
val betaKeyAlias = providers.environmentVariable("VEVAK_BETA_KEY_ALIAS")
val betaKeyPassword = providers.environmentVariable("VEVAK_BETA_KEY_PASSWORD")
val hasBetaSigning = listOf(
    betaStoreFile, betaStorePassword, betaKeyAlias, betaKeyPassword
).all { it.isPresent && it.get().isNotBlank() }

// CI supplies the exact checked-out commit. Local builds are explicitly identified as local.
val sourceRevision = providers.environmentVariable("VEVAK_SOURCE_REVISION")
    .orElse(providers.environmentVariable("GITHUB_SHA"))
    .orElse("local")
    .map {
    if (it.matches(Regex("[0-9a-fA-F]{40}"))) it.take(12) else "local"
}

android {
    namespace = "com.vevak.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vevak.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "0.3.15"
        buildConfigField("String", "SOURCE_REVISION", "\"${sourceRevision.get()}\"")

        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "services"
    productFlavors {
        create("foss") {
            dimension = "services"
            buildConfigField("String", "LOCATION_BACKEND", "\"Android LocationManager\"")
            buildConfigField("Boolean", "USES_GOOGLE_PLAY_SERVICES", "false")
        }
        create("diagnostic") {
            // Location-only sideload build; RECEIVE_SMS/SEND_SMS are removed in its
            // source-set manifest, and only the dedicated diagnostics activity is exported.
            dimension = "services"
            applicationId = "com.vevak.app.diagnostic"
            buildConfigField("String", "LOCATION_BACKEND", "\"Android LocationManager\"")
            buildConfigField("Boolean", "USES_GOOGLE_PLAY_SERVICES", "false")
        }
        create("play") {
            dimension = "services"
            applicationId = "vevak.lepotager.org"
            buildConfigField("String", "LOCATION_BACKEND", "\"Google Fused Location Provider\"")
            buildConfigField("Boolean", "USES_GOOGLE_PLAY_SERVICES", "true")
        }
    }

    signingConfigs {
        if (hasUploadSigning) {
            create("upload") {
                storeFile = file(uploadStoreFile.get())
                storePassword = uploadStorePassword.get()
                keyAlias = uploadKeyAlias.get()
                keyPassword = uploadKeyPassword.get()
            }
        }
        if (hasBetaSigning) {
            create("betaTester") {
                storeFile = file(betaStoreFile.get())
                storePassword = betaStorePassword.get()
                keyAlias = betaKeyAlias.get()
                keyPassword = betaKeyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasUploadSigning) {
                signingConfig = signingConfigs.getByName("upload")
            }
        }
        debug {
            // Ephemeral Android debug key, only for developer/CI compilation.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("beta") {
            // Production safety rules (DEBUG=false), but a distinct install identity.
            // Keeping .beta separate prevents us overwriting old debug installations with
            // incompatible signing certificates and keeps the Play/F-Droid IDs unchanged.
            initWith(getByName("release"))
            applicationIdSuffix = ".beta"
            versionNameSuffix = "-beta"
            isDebuggable = false
            matchingFallbacks += listOf("release")
            // Never inherit the Play upload signature through initWith(release).
            signingConfig = null
            if (hasBetaSigning) {
                signingConfig = signingConfigs.getByName("betaTester")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging.resources.excludes += setOf(
        "/META-INF/{AL2.0,LGPL2.1}",
        "/META-INF/DEPENDENCIES"
    )

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    "playImplementation"("com.google.android.gms:play-services-location:21.3.0")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
