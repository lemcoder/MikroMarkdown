import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlinx.resources)
}

group = "io.github.lemcoder"
version = "0.1.0"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_21
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    androidLibrary {
        namespace = "io.github.lemcoder.mikromarkdown"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.io.core)
        }

        jvmMain.dependencies {
            implementation(libs.jsoup)
            implementation(libs.flexmark.html2md)
            implementation(libs.jackson.kotlin)
            implementation(libs.commons.csv)
            implementation(libs.poi.ooxml)
            implementation(libs.tika.core)
            implementation(libs.pdfbox)
        }

        androidMain.dependencies {
            implementation(libs.jsoup)
            implementation(libs.flexmark.html2md)
            implementation(libs.jackson.kotlin)
            implementation(libs.commons.csv)
            implementation(libs.poi.ooxml)
            implementation(libs.pdfbox.android)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.resources)
        }

        jvmTest.dependencies {
            implementation(libs.junit.jupiter)
            implementation(libs.kotlin.test)
        }
    }
}
