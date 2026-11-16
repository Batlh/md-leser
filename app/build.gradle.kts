plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.batlh.mdreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.batlh.mdreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    // Im Build auf GitHub wird der eigene Schlüssel aus ci/release.keystore.enc genommen
    // (Secret ANDROID_KEY_PASSWORD). Ohne ihn, etwa bei einem lokalen Build, der Ersatzschlüssel.
    signingConfigs {
        create("fest") {
            storeFile = file(System.getenv("MD_KEYSTORE") ?: "../ci/debug.keystore")
            storePassword = System.getenv("MD_KEYSTORE_PW") ?: "android"
            keyAlias = System.getenv("MD_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("MD_KEYSTORE_PW") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fest")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")

    val markwon = "4.6.2"
    implementation("io.noties.markwon:core:$markwon")
    implementation("io.noties.markwon:ext-strikethrough:$markwon")
    implementation("io.noties.markwon:ext-tables:$markwon")
    implementation("io.noties.markwon:ext-tasklist:$markwon")
    implementation("io.noties.markwon:html:$markwon")
    implementation("io.noties.markwon:linkify:$markwon")

    testImplementation("junit:junit:4.13.2")
}
