plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * A reader over the library: pick a document, convert it, render the model.
 *
 * It exists to exercise the real path on a device — the same converters, the same `Document`, and `:renderer-compose`
 * rather than any Markdown re-parsed on the way to the screen.
 */
android {
    namespace = "io.github.lemcoder.mikromarkdown.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.lemcoder.mikromarkdown.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin { jvmToolchain(17) }
}

dependencies {
    implementation(project(":renderer-compose"))
    // PDF is opt-in: it costs a native library, so the app asks for it explicitly.
    implementation(project(":pdfium"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.compose.ui.tooling)
}
