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

val pdfiumLibDir = pdfiumRoot.dir("mac-arm64/lib").asFile.absolutePath

val sourceDef = layout.projectDirectory.file("src/nativeInterop/cinterop/pdfium.def")

val linkedDef = layout.buildDirectory.file("cinterop/pdfium-linked.def")

/**
 * The .def the native target binds, with pdfium's location written into it.
 *
 * cinterop records a .def's `linkerOpts` in the klib and hands them to whatever links against it, which is what lets a
 * consumer use the binding without naming pdfium a second time. The paths have to be absolute, for two reasons: a
 * relative one in a .def resolves against the working directory the compiler happens to run in rather than the file's
 * own location (kotlin-native#2314, still true on 2.3.21), and an rpath has to hold at run time wherever the binary is
 * started from. `libraryPaths` is no way round it either — the klib records it, but it is cinterop's own search path
 * and never reaches the consumer's linker, which then fails with "library 'pdfium' not found".
 *
 * So the file is generated. The JVM and Android legs bind the checked-in .def, which stays free of a macOS path.
 */
val writeLinkedDef by tasks.registering {
    val from = sourceDef
    val into = linkedDef
    val libDir = pdfiumLibDir
    inputs.file(from)
    inputs.property("libDir", libDir)
    outputs.file(into)
    doLast {
        val target = into.get().asFile
        target.parentFile.mkdirs()
        // The archive ships a dylib, so the binary carries an rpath to find it at run time.
        target.writeText(from.asFile.readText().trimEnd() + "\nlinkerOpts = -L$libDir -lpdfium -rpath $libDir\n")
    }
}

kotlin {
    macosArm64 {
        val platform = pdfiumRoot.dir("mac-arm64")
        compilations.getByName("main").cinterops.create("pdfium") {
            defFile(linkedDef.get().asFile)
            includeDirs(platform.dir("include"))
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
tasks.matching { it.name.startsWith("cinteropPdfium") }.configureEach { dependsOn("downloadPdfium", writeLinkedDef) }

tasks
    .matching { it.name.startsWith("generateJvmInterop") || it.name.startsWith("cmakeConfigure") }
    .configureEach { dependsOn("downloadPdfium") }

// The JNI generator reads the Kotlin/Native distribution, and the Kotlin plugin fetches that only when
// something compiles a native target. On a machine that has never built one — a CI runner, a fresh clone
// running `:pdfium:jvmTest` on its own — generating the bindings fails with "No Kotlin/Native distribution
// found" instead, so the download is ordered before it rather than left to whatever else ran first.
tasks
    .matching { it.name.startsWith("generateJvmInterop") }
    .configureEach { dependsOn("downloadKotlinNativeDistribution") }
