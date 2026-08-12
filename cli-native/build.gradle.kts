plugins { alias(libs.plugins.kotlinMultiplatform) }

kotlin {
    macosArm64 { binaries.executable { entryPoint = "io.github.lemcoder.mikromarkdown.cli.main" } }

    sourceSets { macosArm64Main.dependencies { implementation(project(":library")) } }
}
