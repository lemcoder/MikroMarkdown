import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

group = "com.mikromarkdown"
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
        namespace = "com.mikromarkdown"
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
        val jvmAndroidMain by creating {
            dependsOn(getByName("commonMain"))
        }

        getByName("jvmMain") {
            dependsOn(jvmAndroidMain)
            dependencies {
                implementation(libs.jsoup)
                implementation(libs.flexmark.html2md)
                implementation(libs.jackson.kotlin)
                implementation(libs.commons.csv)
                implementation(libs.poi.ooxml)
                implementation(libs.tika.core)
                implementation(libs.pdfbox)
            }
        }

        getByName("androidMain") {
            dependsOn(jvmAndroidMain)
            dependencies {
                implementation(libs.jsoup)
                implementation(libs.flexmark.html2md)
                implementation(libs.jackson.kotlin)
                implementation(libs.commons.csv)
                implementation(libs.poi.ooxml)
                implementation(libs.pdfbox.android)
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        getByName("jvmTest") {
            dependencies {
                implementation(libs.junit.jupiter)
                implementation(libs.kotlin.test)
            }
        }
    }
}
