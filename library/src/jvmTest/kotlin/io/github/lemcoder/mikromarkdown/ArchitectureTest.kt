package io.github.lemcoder.mikromarkdown

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.architecture.KoArchitectureCreator.assertArchitecture
import com.lemonappdev.konsist.api.architecture.Layer
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the pipeline's boundaries: converters parse into the model, the renderer serializes it, and neither reaches
 * across. Without these, Markdown syntax leaks back into converters — the exact drift the document model was introduced
 * to end.
 */
class ArchitectureTest {

    private val scope = Konsist.scopeFromProject()
    private val production = scope.files.filterNot { it.path.contains("Test") }

    // ---- layering -------------------------------------------------------------------------

    @Test
    fun `layers depend in one direction only`() {
        scope.assertArchitecture {
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
        productionIn(".converters").assertFalse { file -> file.hasImport { it.name.contains(".render.") } }
    }

    @Test
    fun `converters do not depend on each other`() {
        productionIn(".converters").assertFalse { file ->
            file.hasImport { it.name.contains(".converters.") && !it.name.endsWith(file.name.removeSuffix(".kt")) }
        }
    }

    @Test
    fun `markdown syntax only lives in the renderer`() {
        val markdownLiterals = Regex("""\"(\|[^"]*\||#{1,6} |\*\*|!\[|```)""")

        production
            .filterNot { it.packagee?.name?.contains(".render") == true }
            .assertFalse { file -> markdownLiterals.containsMatchIn(file.text) }
    }

    @Test
    fun `the model stays free of platform dependencies`() {
        productionIn(".model").assertFalse { file ->
            file.hasImport { it.name.startsWith("java.") || it.name.startsWith("android.") }
        }
    }

    // ---- encapsulation --------------------------------------------------------------------

    @Test
    fun `helpers under utils stay internal`() {
        val utils = { name: String? -> name?.contains(".utils") == true }

        // internal or private: anything but part of the published API.
        scope.classes().filter { utils(it.packagee?.name) }.assertFalse { it.hasPublicOrDefaultModifier }
        scope.objects().filter { utils(it.packagee?.name) }.assertFalse { it.hasPublicOrDefaultModifier }
        scope
            .functions()
            .filter { utils(it.packagee?.name) && it.isTopLevel }
            .assertFalse { it.hasPublicOrDefaultModifier }
    }

    /** Builders hold mutable state by nature; the model must never expose any. */
    @Test
    fun `the model exposes no mutable state`() {
        scope
            .properties()
            .filter { it.packagee?.name?.contains(".model") == true }
            .assertFalse { it.isVar && it.hasPublicOrDefaultModifier }
    }

    @Test
    fun `converters implement DocumentConverter, are named for it, and live together`() {
        scope
            .classes()
            .filter { it.hasParentInterface { parent -> parent.name == "DocumentConverter" } }
            .assertTrue { it.name.endsWith("Converter") && it.resideInPackage("..converters..") }
    }

    // ---- hygiene --------------------------------------------------------------------------

    @Test
    fun `imports are not wildcards`() {
        scope.files.assertFalse { file -> file.hasImport { it.isWildcard } }
    }

    @Test
    fun `production code does not print`() {
        val printCall = Regex("""\bprintln?\(""")

        production.assertFalse { printCall.containsMatchIn(it.text) }
    }

    /**
     * JVM and Android share one source set; only the factory and the PDF converter differ, because pdfbox-android is a
     * separate library. Any other same-named file in two source sets means a copy that will drift.
     */
    @Test
    fun `production files are not copied between source sets`() {
        val expectedPerTarget = setOf("MikroMarkdownFactory", "PdfConverter")

        val copied =
            production.filter { it.path.contains("/src/") }.groupBy { it.name }.filterValues { it.size > 1 }.keys -
                expectedPerTarget

        assertEquals(emptySet(), copied, "these files exist in more than one source set")
    }

    private fun productionIn(packageFragment: String) = production.filter {
        it.packagee?.name?.contains(packageFragment) == true
    }
}
