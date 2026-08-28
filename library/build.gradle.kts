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

    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions { jvmTarget = JvmTarget.JVM_21 }
        testRuns["test"].executionTask.configure { useJUnitPlatform() }
    }

    macosArm64()

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
            // HTML parsing, and the inflate that EPUB's ZIP container needs.
            implementation(libs.ksoup)
            implementation(libs.korlibs.compression)
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

// ktfmt-gradle only derives tasks for the common and JVM source sets, so every other one — Android's
// today, a native one tomorrow — would go unformatted and unchecked. Everything under src/ that ktfmt
// does not already cover is named here by exclusion, so a new target needs no edit to this block.
run {
    val derived = listOf("commonMain", "commonTest", "jvmMain", "jvmTest")
    val remainingSources = fileTree("src") { include("**/*.kt").exclude(derived.map { "$it/**" }) }
    val template = tasks.named<com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask>("ktfmtFormatKmpCommonMain")

    val formatRemaining =
        tasks.register<com.ncorti.ktfmt.gradle.tasks.KtfmtFormatTask>("ktfmtFormatRemainingSourceSets") {
            ktfmtClasspath.from(template.map { it.ktfmtClasspath })
            formattingOptionsBean.set(template.flatMap { it.formattingOptionsBean })
            setSource(remainingSources)
        }
    val checkRemaining =
        tasks.register<com.ncorti.ktfmt.gradle.tasks.KtfmtCheckTask>("ktfmtCheckRemainingSourceSets") {
            ktfmtClasspath.from(template.map { it.ktfmtClasspath })
            formattingOptionsBean.set(template.flatMap { it.formattingOptionsBean })
            setSource(remainingSources)
        }

    tasks.named("ktfmtFormat") { dependsOn(formatRemaining) }
    tasks.named("ktfmtCheck") { dependsOn(checkRemaining) }
    tasks.named("check") { dependsOn(checkRemaining) }
}
