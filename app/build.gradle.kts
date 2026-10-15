plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries)
}

// Numéro de build croissant fourni par la CI (BUILD_NUMBER = github.run_number) : il devient le versionCode et le
// dernier segment du versionName (modèle lieluinit).
val buildNumber = (System.getenv("BUILD_NUMBER") ?: providers.gradleProperty("buildNumber").orNull)?.toIntOrNull() ?: 1
val appVersionBase = providers.gradleProperty("appVersionBase").get()

android {
    namespace = "com.ruizlenato.karabau"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // Par défaut, le keystore versionné `app/debug.keystore` (mots de passe publics `android`) signe debug ET release :
    // signature stable, donc mise à jour par-dessus possible, sans secret. Pour une vraie clé de publication, définir
    // KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS et KEY_PASSWORD (secrets CI). Changer de clé impose de désinstaller.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            val keystoreFile = System.getenv("KEYSTORE_FILE")?.takeIf { it.isNotBlank() }
            if (keystoreFile != null) {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            } else {
                storeFile = file("debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.ruizlenato.karabau"
        minSdk = 31
        targetSdk = 36
        versionCode = buildNumber
        versionName = "$appVersionBase.$buildNumber"

        // L'app se met à jour depuis les releases de son propre dépôt (GITHUB_REPOSITORY en CI).
        val githubRepository = System.getenv("GITHUB_REPOSITORY") ?: providers.gradleProperty("appGithubRepository").get()
        buildConfigField("String", "GITHUB_REPOSITORY", "\"$githubRepository\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "LieluKeep Debug")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Collections
    implementation(libs.kotlinx.collections.immutable)

    // Images
    implementation(libs.coil.compose)

    // Mise à jour automatique (releases GitHub)
    implementation(libs.lielugit.updater)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
