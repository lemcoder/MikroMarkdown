import io.github.lemcoder.model.NativeTarget

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
    // The binding below declares this target's cinterop; nothing is declared here by hand.
    macosArm64()

    androidLibrary {
        namespace = "io.github.lemcoder.mikromarkdown.pdfium"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTestBuilder {}.configure {}
    }

    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":library"))
            // The deflate inside PngEncoder, which wraps the raw bitmaps pdfium hands back.
            implementation(libs.korlibs.compression)
        }
        jvmTest.dependencies { implementation(libs.kotlin.test) }
    }
}

tasks.named<Test>("jvmTest") { useJUnitPlatform() }

/** The Konan targets each Android ABI is built as. */
val androidTargets = mapOf("arm64-v8a" to NativeTarget.ANDROID_ARM64, "x86_64" to NativeTarget.ANDROID_X64)

/**
 * One binding for every consumer: KGP's cinterop for macosArm64, generated JNI bindings plus a stub library for the JVM
 * and Android.
 *
 * Every consumer reads the generated .def. Kotlin/Native needs its `linkerOpts`; the JNI legs ignore them, because
 * CMake links the stub, and pdfium is C++ whose runtime comes along correctly only that way. The package is named here
 * rather than read from the .def, which does not exist until `writeLinkedDef` runs.
 */
konan {
    // The Kotlin/Native distribution of this project's Kotlin version; the JNI generator runs inside it.
    config { konanHome.set(kotlinNativeDistribution()) }

    bindings {
        create("pdfium") {
            defFile.set(linkedDef)
            packageName.set("pdfium")
            includeDirs.from(pdfiumRoot.dir("mac-arm64/include"))

            jni {
                cmake {
                    path.set(project.file("native/CMakeLists.txt"))
                    buildTargets.add("pdfium-jni")
                    target(NativeTarget.MACOS_ARM64) {
                        arguments.add("-DPDFium_DIR=${pdfiumRoot.dir("mac-arm64").asFile.absolutePath}")
                    }
                    for ((abi, platform) in pdfiumAbis) {
                        target(androidTargets.getValue(abi)) {
                            arguments.add("-DPDFium_DIR=${pdfiumRoot.dir(platform).asFile.absolutePath}")
                        }
                    }
                }
            }
        }
    }
}

/**
 * pdfium's own library, packaged beside the JNI stub.
 *
 * The stub is 34 KB and carries a `DT_NEEDED` on `libpdfium.so`. The Konan plugin packages the stub it builds and knows
 * nothing about the prebuilt library that stub links against, so an APK gets the stub alone and `System.loadLibrary`
 * fails at the first PDF with "library libpdfium.so not found". Only ABI-named directories may sit under jniLibs, which
 * is what the copy is arranging.
 */
val pdfiumJniLibs = layout.buildDirectory.dir("pdfiumJniLibs")

val copyPdfiumJniLibs by
    tasks.registering(Copy::class) {
        dependsOn("downloadPdfium")
        into(pdfiumJniLibs)
        for ((abi, platform) in pdfiumAbis) {
            from(pdfiumRoot.dir("$platform/lib")) {
                include("libpdfium.so")
                into(abi)
            }
        }
    }

run {
    val relative = pdfiumJniLibs.get().asFile.apply { mkdirs() }.relativeTo(projectDir).path

    plugins.withId("com.android.kotlin.multiplatform.library") {
        extensions
            .findByType(com.android.build.api.variant.KotlinMultiplatformAndroidComponentsExtension::class.java)
            ?.onVariants { variant -> variant.sources.jniLibs?.addStaticSourceDirectory(relative) }
    }

    // Nothing consumes the directory as a task output, so packaging needs the ordering spelled out.
    tasks
        .matching { it.name.contains("JniLibFolders") || it.name.contains("MergeJniLib") }
        .configureEach { dependsOn(copyPdfiumJniLibs) }
}

// Every binding path needs the headers and the library unpacked first.
tasks.matching { it.name.startsWith("cinteropPdfium") }.configureEach { dependsOn("downloadPdfium", writeLinkedDef) }

tasks
    .matching { it.name.startsWith("generateJniPdfium") || it.name.startsWith("cmakeBuildJniPdfium") }
    .configureEach { dependsOn("downloadPdfium", writeLinkedDef) }

// The JNI generator reads the Kotlin/Native distribution, and the Kotlin plugin fetches that only when
// something compiles a native target. On a machine that has never built one — a CI runner, a fresh clone
// running `:pdfium:jvmTest` on its own — generating the bindings fails with "No Kotlin/Native distribution
// found" instead, so the download is ordered before it rather than left to whatever else ran first.
tasks
    .matching { it.name.startsWith("generateJniPdfium") }
    .configureEach { dependsOn("downloadKotlinNativeDistribution") }
