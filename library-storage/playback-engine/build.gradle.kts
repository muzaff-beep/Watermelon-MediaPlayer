plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.watermelon.playback"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        // targetSdk lives on the app module for libraries; kept for parity with the blueprint.
    }
    buildFeatures { buildConfig = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Media3 session APIs (e.g. MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS)
        // are annotated @UnstableApi. Opt in module-wide rather than at each call site.
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }
}

dependencies {
    implementation(project(":common-interfaces"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
