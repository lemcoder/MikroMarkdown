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
        // JVM and Android call the same generated bridges, so both the bindings and the actual
        // written over them live once, in a source set the two share.
        val jniMain by creating { dependsOn(commonMain.get()) }

        jvmMain { dependsOn(jniMain) }
        androidMain { dependsOn(jniMain) }

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
 * One declaration serves the JVM and every Android ABI: the same .def cinterop binds, generated into the source set
 * both share, and CMake linking a stub per platform against the pdfium built for it.
 */
jvmInterops(kotlin.sourceSets.getByName("jniMain")) {
    create("pdfium") {
        defFile(project.file("src/nativeInterop/cinterop/pdfium.def"))
        includeDirs.from(pdfiumRoot.dir("mac-arm64/include"))

        externalNativeBuild {
            cmake {
                path.set(project.file("native/CMakeLists.txt"))
                targets.add("pdfium-jni")
                arguments.add("-DPDFium_DIR=${pdfiumRoot.dir("mac-arm64").asFile.absolutePath}")
                // The desktop JVM needs the host stub too, not only the Android ABIs.
                hostBuild.set(true)

                for ((abi, platformName) in pdfiumAbis) {
                    abi(abi) {
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
