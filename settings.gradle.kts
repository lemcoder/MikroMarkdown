pluginManagement {
    repositories {
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
