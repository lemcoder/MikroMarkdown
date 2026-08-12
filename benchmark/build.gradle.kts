plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

application { mainClass = "io.github.lemcoder.mikromarkdown.benchmark.MainKt" }

// Fixtures are addressed from the repository root, not this module's directory.
tasks.named<JavaExec>("run") { workingDir = rootProject.projectDir }

dependencies { implementation(project(":library")) }
