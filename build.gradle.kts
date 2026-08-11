plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ktfmt)
    alias(libs.plugins.detekt)
}

allprojects {
    apply(plugin = rootProject.libs.plugins.ktfmt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    ktfmt {
        // Matches the existing sources: 4-space indent, no import reordering surprises.
        kotlinLangStyle()
        maxWidth = 120
    }

    detekt {
        buildUponDefaultConfig = true
        parallel = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        basePath = rootProject.projectDir.absolutePath
        // Generated sources and the vendored reference checkouts are not ours to lint.
        source.setFrom(files("src").filter { it.exists() })
    }

    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "21"
        reports {
            html.required = true
            sarif.required = true
            md.required = false
            txt.required = false
        }
    }
}
