import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlinx.resources)
}

group = "io.github.lemcoder"

version = "0.1.0"

kotlin {
    // Public API must be spelled out: visibility and return types, no accidental exports.
    explicitApi()

    // Declaring jvmShared by hand switches off the automatic hierarchy, which macosMain needs.
    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_21 }
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }

    // Spike: a native target to see how close a real binary gets to the Rust implementation.
    // The shared integration tests expect JVM-only formats, so native test compilation stays off
    // until the native target carries real converters.
    macosArm64 { compilations.getByName("test") { compileTaskProvider.configure { enabled = false } } }

    androidLibrary {
        namespace = "io.github.lemcoder.mikromarkdown"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder { sourceSetTreeName = "test" }

        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
            // Phase 0 of the commonMain migration: HTML parsing and the inflate that ZIP needs.
            implementation(libs.ksoup)
            implementation(libs.korlibs.compression)
        }

        // JVM and Android run the same parsers on the same libraries; only PDF and MIME
        // detection differ. Converters live here once instead of being copied per target.
        val jvmShared by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.jsoup) }
        }

        jvmMain {
            dependsOn(jvmShared)
            dependencies {
                implementation(libs.tika.core)
                implementation(libs.pdfbox)
            }
        }

        androidMain {
            dependsOn(jvmShared)
            dependencies { implementation(libs.pdfbox.android) }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.resources)
        }

        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.kotlin.test)
            implementation(libs.konsist)
        }
    }
}

// ktfmt-gradle only derives tasks for the common and JVM source sets, so the Android
// ones — where half the converters live — would go unformatted and unchecked.
run {
    val androidSources = fileTree("src") { include("android*/**/*.kt") }
    val template = tasks.named<com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask>("ktfmtFormatKmpCommonMain")

    val formatAndroid =
        tasks.register<com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask>("ktfmtFormatAndroidSourceSets") {
            ktfmtClasspath.from(template.map { it.ktfmtClasspath })
            formattingOptionsBean.set(template.flatMap { it.formattingOptionsBean })
            setSource(androidSources)
        }
    val checkAndroid =
        tasks.register<com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask>("ktfmtCheckAndroidSourceSets") {
            ktfmtClasspath.from(template.map { it.ktfmtClasspath })
            formattingOptionsBean.set(template.flatMap { it.formattingOptionsBean })
            setSource(androidSources)
        }

    tasks.named("ktfmtFormat") { dependsOn(formatAndroid) }
    tasks.named("ktfmtCheck") { dependsOn(checkAndroid) }
    tasks.named("check") { dependsOn(checkAndroid) }
}
