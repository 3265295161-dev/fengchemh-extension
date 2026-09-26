plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "2.3.10"
}

android {
    namespace = "eu.kanade.tachiyomi.extension.zh.fengchemh"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // AGP 8.5's lint cannot parse Kotlin 2.3 metadata of extensions-lib; skip it.
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sign the release build with the debug key so the APK is directly
            // installable from "Build" without a dedicated keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Name the APK like other Mihon extensions: <name>-v<version>.apk
    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            outputImpl.outputFileName = "fengchemh-v${versionName}.apk"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly("com.github.keiyoushi:extensions-lib:18a8e26be2")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.10")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
}
