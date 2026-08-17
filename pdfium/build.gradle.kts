import java.security.MessageDigest

plugins { alias(libs.plugins.kotlinMultiplatform) }

/**
 * PDF support, kept in its own module so it can be dropped or extracted whole.
 *
 * pdfium is a prebuilt binary rather than a source dependency: the release is pinned and verified by checksum, unpacked
 * into the build directory, and never committed.
 */
val pdfiumRelease = "chromium/8009"

val pdfiumArchives = mapOf("mac-arm64" to "b1f2f17c7432a9942514dda5094ee9822c743bdfd07e7187725efbd34fde941f")

val pdfiumRoot: Provider<Directory> = layout.buildDirectory.dir("pdfium")

val downloadPdfium by tasks.registering {
    description = "Downloads and unpacks the pinned pdfium binaries."
    outputs.dir(pdfiumRoot)

    doLast {
        for ((platform, sha256) in pdfiumArchives) {
            val target = pdfiumRoot.get().dir(platform).asFile
            if (target.resolve("lib").exists()) continue

            val archive = pdfiumRoot.get().file("pdfium-$platform.tgz").asFile
            archive.parentFile.mkdirs()
            if (!archive.exists()) {
                val url =
                    "https://github.com/bblanchon/pdfium-binaries/releases/download/" +
                        "$pdfiumRelease/pdfium-$platform.tgz"
                logger.lifecycle("downloading pdfium $pdfiumRelease for $platform")
                uri(url).toURL().openStream().use { input ->
                    archive.outputStream().use { output -> input.copyTo(output) }
                }
            }

            val digest =
                MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") {
                    (it.toInt() and 0xFF).toString(16).padStart(2, '0')
                }
            check(digest == sha256) { "pdfium-$platform.tgz checksum $digest, expected $sha256" }

            target.mkdirs()
            providers
                .exec { commandLine("tar", "xzf", archive.absolutePath, "-C", target.absolutePath) }
                .standardOutput
                .asText
                .get()

            // The published dylib calls itself ./libpdfium.dylib, which the loader resolves
            // against the working directory rather than the binary. Rewrite it to @rpath so a
            // linked executable can find it wherever it runs from.
            val dylib = target.resolve("lib/libpdfium.dylib")
            if (dylib.exists()) {
                providers
                    .exec { commandLine("install_name_tool", "-id", "@rpath/libpdfium.dylib", dylib.absolutePath) }
                    .standardOutput
                    .asText
                    .get()
            }
        }
    }
}

kotlin {
    macosArm64 {
        val platform = pdfiumRoot.get().dir("mac-arm64")
        compilations.getByName("main").cinterops.create("pdfium") {
            defFile("src/nativeInterop/cinterop/pdfium.def")
            includeDirs(platform.dir("include"))
            // The archive ships a dylib, so the binary carries an rpath to find it at run time.
            extraOpts("-libraryPath", platform.dir("lib").asFile.absolutePath)
        }
        binaries.executable {
            entryPoint = "io.github.lemcoder.mikromarkdown.pdf.main"
            linkerOpts(
                "-L${platform.dir("lib").asFile.absolutePath}",
                "-lpdfium",
                "-rpath",
                platform.dir("lib").asFile.absolutePath,
            )
        }
    }

    sourceSets { macosArm64Main.dependencies { implementation(project(":library")) } }
}

tasks.matching { it.name.startsWith("cinteropPdfium") }.configureEach { dependsOn(downloadPdfium) }
