import java.util.Properties

plugins {
    id("com.android.application") // AGP 9 compiles Kotlin itself; no kotlin-android plugin
}

// Release signing: android/keystore.properties (gitignored) or env vars. Missing -> unsigned release APK.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signing(key: String, env: String): String? = keystoreProps.getProperty(key) ?: System.getenv(env)

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

    signingConfigs {
        signing("storeFile", "PV_KEYSTORE")?.let { path ->
            create("release") {
                storeFile = rootProject.file(path)
                storePassword = signing("storePassword", "PV_KEYSTORE_PASSWORD")
                keyAlias = signing("keyAlias", "PV_KEY_ALIAS")
                keyPassword = signing("keyPassword", "PV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
