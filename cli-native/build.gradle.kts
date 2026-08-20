plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    macosArm64 {
        // Nothing about pdfium here: its cinterop .def records where the library is, and cinterop
        // hands those options to whatever links the binding.
        binaries.executable { entryPoint = "io.github.lemcoder.mikromarkdown.cli.main" }

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
