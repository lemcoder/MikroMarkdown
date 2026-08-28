plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

/**
 * A Compose renderer for [io.github.lemcoder.mikromarkdown.model.Document].
 *
 * A sibling of `MarkdownRenderer`, not a consumer of it: the document model is already the shared vocabulary, so
 * rendering to the screen is one `when` over blocks rather than Markdown parsed a second time on the device.
 */
android {
    namespace = "io.github.lemcoder.mikromarkdown.compose"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin { jvmToolchain(17) }
}

dependencies {
    api(project(":library"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
}
