pluginManagement {
    repositories {
        // KonanPlugin is consumed from a local publish while the void-buffer marshalling it needs
        // for pdfium is unreleased; drop this once 1.2.0-alpha06 is on the portal.
        mavenLocal()
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

include(":cli")

include(":benchmark")

include(":cli-native")

include(":pdfium")
