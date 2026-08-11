package io.github.lemcoder.mikromarkdown

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test

/**
 * Guards the pipeline's boundaries: converters parse into the model, the renderer serializes it, and neither reaches
 * across. Without these, Markdown syntax leaks back into converters — the exact drift the document model was introduced
 * to end.
 */
class ArchitectureTest {

    private val production = Konsist.scopeFromProject(sourceSetName = null).files.filterNot { it.path.contains("Test") }

    @Test
    fun `layers depend in one direction only`() {
        Konsist.scopeFromProject().assertArchitecture {
            val model = Layer("Model", "io.github.lemcoder.mikromarkdown.model..")
            val render = Layer("Render", "io.github.lemcoder.mikromarkdown.render..")
            val converters = Layer("Converters", "io.github.lemcoder.mikromarkdown.converters..")

            // The model is the shared vocabulary: it may not know about its producers or consumers.
            model.dependsOnNothing()
            render.dependsOn(model)
            converters.dependsOn(model)
        }
    }

    @Test
    fun `converters do not reach into the renderer`() {
        production
            .filter { it.packagee?.name?.contains(".converters") == true }
            .assertFalse { file -> file.hasImport { it.name.contains(".render.") } }
    }

    @Test
    fun `markdown syntax only lives in the renderer`() {
        val markdownLiterals = Regex("""\"(\|[^"]*\||#{1,6} |\*\*|!\[|```)""")

        production
            .filterNot { it.packagee?.name?.contains(".render") == true }
            .assertFalse { file -> markdownLiterals.containsMatchIn(file.text) }
    }

    @Test
    fun `converters implement DocumentConverter and are named accordingly`() {
        Konsist.scopeFromProject()
            .classes()
            .filter { it.resideInPackage("..converters..") && it.name.endsWith("Converter") }
            .assertTrue { it.hasParentInterface { parent -> parent.name == "DocumentConverter" } }
    }

    @Test
    fun `the model stays free of platform dependencies`() {
        production
            .filter { it.packagee?.name?.contains(".model") == true }
            .assertFalse { file -> file.hasImport { it.name.startsWith("java.") || it.name.startsWith("android.") } }
    }
}
