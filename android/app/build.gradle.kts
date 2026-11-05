plugins {
    id("com.android.application") // AGP 9 compiles Kotlin itself; no kotlin-android plugin
}

android {
    namespace = "io.github.selimerenkaya.phoneviewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.selimerenkaya.phoneviewer"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
