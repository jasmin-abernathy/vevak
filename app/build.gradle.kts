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

android {
    namespace = "com.vevak.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vevak.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 17
        versionName = "0.3.14"

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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
