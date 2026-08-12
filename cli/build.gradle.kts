plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

val cdsArchiveName = "mikromarkdown.jsa"

application {
    mainClass = "com.mikromarkdown.cli.MainKt"
    // Class loading, not conversion, is what a short CLI run spends its time on. A class-data-sharing
    // archive maps those classes in pre-parsed. -Xshare:auto keeps the CLI working when it is absent.
    // Every CLI run is short, so C2 never pays for itself: compiling with C1 only is faster
    // end to end. Long-running embedders use the library directly and are unaffected.
    applicationDefaultJvmArgs = listOf("-Xshare:auto", "-XX:-UsePerfData", "-XX:TieredStopAtLevel=1")
}

dependencies {
    implementation(project(":library"))
    implementation(libs.clikt)
}

tasks.test { useJUnitPlatform() }

// The archive flag is added by the script itself, and only when the archive is there: naming a
// missing archive stops the JVM loading its base one, which is also what recording needs.
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        val unixGuard =
            """
            CDS_ARCHIVE="${'$'}APP_HOME/lib/$cdsArchiveName"
            if [ -f "${'$'}CDS_ARCHIVE" ] && [ -z "${'$'}MIKROMARKDOWN_NO_CDS" ] ; then
                DEFAULT_JVM_OPTS="${'$'}DEFAULT_JVM_OPTS \"-XX:SharedArchiveFile=${'$'}CDS_ARCHIVE\""
            fi
            """
                .trimIndent()

        // Lambda form: the guard contains $ sequences that must not be read as group references.
        unixScript.writeText(
            unixScript.readText().replace(Regex("(?m)^(DEFAULT_JVM_OPTS=.*)${'$'}")) { match ->
                "${match.value}\n\n$unixGuard"
            }
        )

        val windowsGuard =
            """
            set CDS_ARCHIVE=%APP_HOME%\\lib\\$cdsArchiveName
            if exist "%CDS_ARCHIVE%" if not defined MIKROMARKDOWN_NO_CDS set DEFAULT_JVM_OPTS=%DEFAULT_JVM_OPTS% "-XX:SharedArchiveFile=%CDS_ARCHIVE%"
            """
                .trimIndent()

        windowsScript.writeText(
            windowsScript.readText().replace(Regex("(?m)^(set DEFAULT_JVM_OPTS=.*)${'$'}")) { match ->
                "${match.value}\r\n$windowsGuard"
            }
        )
    }
}

/**
 * Builds a class-data-sharing archive for the installed distribution, in two steps:
 *
 * 1. run the CLI once with `-XX:DumpLoadedClassList` to learn which classes a conversion touches;
 * 2. `-Xshare:dump` that list into an archive.
 *
 * This static form is used rather than `-XX:ArchiveClassesAtExit` because the dynamic one needs the JDK's own base
 * archive, which some distributions (JetBrains Runtime among them) do not ship.
 *
 * Step 1 goes through the start script so the classpath recorded is the one real runs use; step 2 reads that same
 * classpath back out of the script, because a mismatch makes the JVM drop the archive without saying so.
 */
val cdsArchive by tasks.registering {
    group = "distribution"
    description = "Builds a class-data-sharing archive into the installed distribution."

    val installDir = layout.buildDirectory.dir("install/${application.applicationName}").get()
    val appName = application.applicationName
    val sample = rootProject.layout.projectDirectory.file("library/src/commonTest/resources/test_files/test.docx")
    val javaHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath

    doLast {
        val script = installDir.file("bin/$appName").asFile
        val classList = installDir.file("lib/$cdsArchiveName.classlist").asFile
        val archive = installDir.file("lib/$cdsArchiveName").asFile
        archive.delete()

        providers
            .exec {
                commandLine(script.absolutePath, sample.asFile.absolutePath)
                environment("JAVA_OPTS", "-XX:DumpLoadedClassList=${classList.absolutePath}")
                environment("MIKROMARKDOWN_NO_CDS", "1")
            }
            .standardOutput
            .asText
            .get()
        check(classList.exists()) { "the JVM recorded no class list" }

        val classpath =
            script
                .readLines()
                .first { it.startsWith("CLASSPATH=") }
                .removePrefix("CLASSPATH=")
                .replace("\$APP_HOME", installDir.asFile.absolutePath)

        providers
            .exec {
                commandLine(
                    javaHome.file("bin/java").asFile.absolutePath,
                    "-Xshare:dump",
                    "-XX:SharedClassListFile=${classList.absolutePath}",
                    "-XX:SharedArchiveFile=${archive.absolutePath}",
                    "-cp",
                    classpath,
                )
            }
            .standardOutput
            .asText
            .get()
        check(archive.exists()) { "no CDS archive was produced" }
        logger.lifecycle("CDS archive: ${archive.length() / 1024} KB")
    }
}

tasks.named("installDist") { finalizedBy(cdsArchive) }
