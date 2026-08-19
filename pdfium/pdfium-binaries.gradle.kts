import java.net.URI
import java.security.MessageDigest
import javax.inject.Inject
import org.gradle.process.ExecOperations

/**
 * Fetches the prebuilt pdfium binaries.
 *
 * Applied by the module's build file rather than living in it: pinning a release, verifying it and unpacking it per
 * platform is its own concern, and it is the part most likely to grow — one entry per platform we support.
 *
 * Exposes through `extra`:
 * - `pdfiumRoot` the directory holding `<platform>/include` and `<platform>/lib`
 * - `pdfiumAbis` Android ABI name to the platform name pdfium publishes
 */
val pdfiumRelease = "chromium/8009"

val pdfiumArchives =
    mapOf(
        "mac-arm64" to "b1f2f17c7432a9942514dda5094ee9822c743bdfd07e7187725efbd34fde941f",
        "android-arm64" to "eebf9df88c68a080efd379058651596c96043117dfdbe71ec0d03c953ae7e805",
        "android-x64" to "d1068eca5710d77653d453fa7d922dbba8e97b9d1d5dc06ce02aa0d8d599c20a",
    )

val pdfiumRoot: Directory = layout.buildDirectory.dir("pdfium").get()

extra["pdfiumRoot"] = pdfiumRoot

extra["pdfiumAbis"] = mapOf("arm64-v8a" to "android-arm64", "x86_64" to "android-x64")

/**
 * A task class rather than a `doLast` block, because the configuration cache cannot serialize the script object that a
 * block in a script plugin captures the moment it calls `uri()`, `providers` or `logger`. Everything the action needs
 * arrives as an input or an injected service.
 */
abstract class DownloadPdfium : DefaultTask() {

    @get:Input abstract val release: Property<String>

    /** platform name to the SHA-256 of its archive. */
    @get:Input abstract val archives: MapProperty<String, String>

    @get:OutputDirectory abstract val root: DirectoryProperty

    @get:Inject abstract val exec: ExecOperations

    @TaskAction
    fun download() {
        val releaseTag = release.get()
        for ((platform, sha256) in archives.get()) {
            val target = root.get().dir(platform).asFile
            if (target.resolve("lib").exists()) continue

            val archive = root.get().file("pdfium-$platform.tgz").asFile
            archive.parentFile.mkdirs()
            if (!archive.exists()) {
                val url =
                    "https://github.com/bblanchon/pdfium-binaries/releases/download/" +
                        "$releaseTag/pdfium-$platform.tgz"
                logger.lifecycle("downloading pdfium $releaseTag for $platform")
                URI(url).toURL().openStream().use { input ->
                    archive.outputStream().use { output -> input.copyTo(output) }
                }
            }

            val digest =
                MessageDigest.getInstance("SHA-256").digest(archive.readBytes()).joinToString("") {
                    (it.toInt() and 0xFF).toString(16).padStart(2, '0')
                }
            check(digest == sha256) { "pdfium-$platform.tgz checksum $digest, expected $sha256" }

            target.mkdirs()
            exec.exec { commandLine("tar", "xzf", archive.absolutePath, "-C", target.absolutePath) }

            // The macOS dylib calls itself ./libpdfium.dylib, which the loader resolves against the
            // working directory rather than the binary. Rewrite it to @rpath so anything linking it
            // can find it. Mach-O only; the Android .so needs nothing.
            val dylib = target.resolve("lib/libpdfium.dylib")
            if (dylib.exists()) {
                exec.exec { commandLine("install_name_tool", "-id", "@rpath/libpdfium.dylib", dylib.absolutePath) }
            }
        }
    }
}

tasks.register<DownloadPdfium>("downloadPdfium") {
    description = "Downloads and unpacks the pinned pdfium binaries."
    group = "build setup"
    release.set(pdfiumRelease)
    archives.set(pdfiumArchives)
    root.set(pdfiumRoot)
}
