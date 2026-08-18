import io.github.lemcoder.interop.jvmInterops

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.konanplugin)
}

/**
 * PDF support, kept in its own module so it can be dropped or extracted whole.
 *
 * pdfium is a prebuilt binary rather than a source dependency; fetching and verifying it lives in
 * pdfium-binaries.gradle.kts.
 */
apply(from = "pdfium-binaries.gradle.kts")

@Suppress("UNCHECKED_CAST") val pdfiumRoot = extra["pdfiumRoot"] as Directory

@Suppress("UNCHECKED_CAST") val pdfiumAbis = extra["pdfiumAbis"] as Map<String, String>

kotlin {
    macosArm64 {
        val platform = pdfiumRoot.dir("mac-arm64")
        compilations.getByName("main").cinterops.create("pdfium") {
            defFile("src/nativeInterop/cinterop/pdfium.def")
            includeDirs(platform.dir("include"))
            // The archive ships a dylib, so the binary carries an rpath to find it at run time.
            extraOpts("-libraryPath", platform.dir("lib").asFile.absolutePath)
        }
    }

    androidLibrary {
        namespace = "io.github.lemcoder.mikromarkdown.pdfium"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTestBuilder {}.configure {}
    }

    jvm()

    sourceSets {
        commonMain.dependencies { implementation(project(":library")) }
        jvmTest.dependencies { implementation(libs.kotlin.test) }
    }
}

// The bindings load the stub by name, and the stub finds pdfium through the rpath CMake gave it.
tasks.named<Test>("jvmTest") {
    dependsOn("linkJvmInteropPdfium")
    useJUnitPlatform()
    systemProperty(
        "java.library.path",
        layout.buildDirectory.dir("jvmInterop/pdfium/lib").get().asFile.absolutePath,
    )
}

/**
 * The JVM and Android legs bind the same .def separately, one declaration each.
 *
 * They could share one, and deliberately do not: the two runtimes diverge over time — how a library is loaded, what a
 * file path means — and a shared declaration turns the first difference into a restructure rather than an edit.
 */
kotlin.jvm().compilations["main"].jvmInterops {
    create("pdfium") {
        defFile(project.file("src/nativeInterop/cinterop/pdfium.def"))
        includeDirs.from(pdfiumRoot.dir("mac-arm64/include"))

        externalNativeBuild {
            cmake {
                path.set(project.file("native/CMakeLists.txt"))
                targets.add("pdfium-jni")
                arguments.add("-DPDFium_DIR=${pdfiumRoot.dir("mac-arm64").asFile.absolutePath}")
            }
        }
    }
}

kotlin.targets.getByName("android").compilations.getByName("main").jvmInterops {
    create("pdfiumAndroid") {
        defFile(project.file("src/nativeInterop/cinterop/pdfium.def"))
        includeDirs.from(pdfiumRoot.dir("android-arm64/include"))

        externalNativeBuild {
            cmake {
                path.set(project.file("native/CMakeLists.txt"))
                targets.add("pdfium-jni")

                for ((abiName, platformName) in pdfiumAbis) {
                    abi(abiName) {
                        platform.set(libs.versions.android.minSdk.get().toInt())
                        arguments.add("-DPDFium_DIR=${pdfiumRoot.dir(platformName).asFile.absolutePath}")
                    }
                }
            }
        }
    }
}

// Every binding path needs the headers and the library unpacked first.
tasks.matching { it.name.startsWith("cinteropPdfium") }.configureEach { dependsOn("downloadPdfium") }

tasks
    .matching { it.name.startsWith("generateJvmInterop") || it.name.startsWith("cmakeConfigure") }
    .configureEach { dependsOn("downloadPdfium") }
