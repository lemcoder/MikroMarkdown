pluginManagement {
    repositories {
        // The Konan plugin's 2.0 line, built from ~/src/KonanPlugin and published locally until it
        // reaches the Plugin Portal. Scoped so nothing else is ever resolved from ~/.m2.
        mavenLocal { content { includeGroupByRegex("io\\.github\\.lemcoder.*") } }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mikromarkdown"

include(":library")

include(":benchmark")

include(":cli-native")

include(":pdfium")

include(":renderer-compose")

include(":sample-android")
