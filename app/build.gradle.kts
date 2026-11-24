import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.batlh.mdreader"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.batlh.mdreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "1.9.0"
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")

    // Editor-Komponente (LGPL 2.1)
    val sora = "0.23.7"
    implementation("io.github.rosemoe:editor:$sora")
    implementation("io.github.rosemoe:language-textmate:$sora")

    // Syntax prüfen: JSON, YAML, Lua (XML kann Android selbst)
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.snakeyaml:snakeyaml-engine:2.9")
    implementation("org.luaj:luaj-jse:3.0.1")

    val markwon = "4.6.2"
    implementation("io.noties.markwon:core:$markwon")
    implementation("io.noties.markwon:ext-strikethrough:$markwon")
    implementation("io.noties.markwon:ext-tables:$markwon")
    implementation("io.noties.markwon:ext-tasklist:$markwon")
    implementation("io.noties.markwon:html:$markwon")
    implementation("io.noties.markwon:linkify:$markwon")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
