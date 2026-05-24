plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.mikromarkdown.cli.MainKt"
}

dependencies {
    implementation(project(":library"))
    implementation(libs.clikt)
}

tasks.test {
    useJUnitPlatform()
}
