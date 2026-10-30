import java.util.Properties

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

/**
 * One document per supported format, bundled so the reader has something to open on a fresh install.
 *
 * Taken from the library's own test fixtures rather than copied into this module, so the app shows exactly what the
 * tests convert. The Office fixtures are left out: those formats were removed from the library.
 */
abstract class BundleSamples : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val samples: ConfigurableFileCollection

    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction
    fun bundle() {
        val into = output.get().dir("samples").asFile
        into.deleteRecursively()
        into.mkdirs()
        samples.forEach { it.copyTo(into.resolve(it.name)) }
    }
}

val bundleSamples by
    tasks.registering(BundleSamples::class) {
        val fixtures = rootProject.layout.projectDirectory.dir("library/src/commonTest/resources/test_files")
        samples.from(
            listOf(
                    "test.epub",
                    "test.pdf",
                    "test_wikipedia.html",
                    "test_blog.html",
                    "test.csv",
                    "test.json",
                    "test.xml",
                )
                .map { fixtures.file(it) }
        )
        // Documents of the developer's own — books, scans — listed in the git-ignored local.properties as
        // `samples.extra=/path/a.pdf;/path/b.epub`, so they reach a device without ever entering the repo.
        val local = rootProject.file("local.properties")
        if (local.isFile) {
            val extra = Properties().apply { local.inputStream().use(::load) }.getProperty("samples.extra")
            samples.from(extra.orEmpty().split(';').map(String::trim).filter(String::isNotEmpty))
        }
        output.set(layout.buildDirectory.dir("generated/samples"))
    }

androidComponents {
    onVariants { variant -> variant.sources.assets?.addGeneratedSourceDirectory(bundleSamples, BundleSamples::output) }
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
