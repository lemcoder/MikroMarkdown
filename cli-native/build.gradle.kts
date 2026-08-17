plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    macosArm64 {
        // Kotlin/Native does not carry a klib's linker options to the binary that uses it, so the
        // consumer names pdfium itself. Worth turning into a shared convention if a second
        // consumer appears.
        val pdfiumLib = rootProject.layout.projectDirectory.dir("pdfium/build/pdfium/mac-arm64/lib").asFile

        binaries.executable {
            entryPoint = "io.github.lemcoder.mikromarkdown.cli.main"
            linkerOpts("-L${pdfiumLib.absolutePath}", "-lpdfium", "-rpath", pdfiumLib.absolutePath)
        }

        compilerOptions {
            // Worth about 8% on large inputs and nothing on small ones. Measured, not assumed:
            // the GC binary options were all neutral or worse, so the defaults stay.
            //
            // gcSchedulerType=manual is the one real alternative — 20% faster on a 1.8 MB CSV,
            // because a process that exits never needs to collect — but peak memory goes from
            // 125 MB to 169 MB on that input, and it grows without bound on larger ones.
            freeCompilerArgs.add("-Xbinary=preCodegenInlineThreshold=40")
        }
    }

    sourceSets {
        macosArm64Main.dependencies {
            implementation(project(":library"))
            // PDF is a separate module by design; the CLI is the thing that opts in.
            implementation(project(":pdfium"))
        }
    }
}
