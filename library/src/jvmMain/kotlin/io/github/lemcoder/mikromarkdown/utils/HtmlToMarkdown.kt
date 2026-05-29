package io.github.lemcoder.mikromarkdown.utils

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.util.data.MutableDataSet
import org.jsoup.Jsoup

object HtmlToMarkdown {
    private val options = MutableDataSet().apply {
        set(FlexmarkHtmlConverter.SETEXT_HEADINGS, false)
    }
    private val converter = FlexmarkHtmlConverter.builder(options).build()

    fun convert(html: String, baseUri: String = ""): Pair<String, String?> {
        val doc = Jsoup.parse(html, baseUri)
        doc.select("script, style, button").remove()
        // Flexmark drops rows whose header cell uses <th scope="row"> (infobox/navbox pattern).
        // Converting them to <td> preserves content while allowing Flexmark to process the rows.
        doc.select("th[scope=row]").tagName("td")
        val title = doc.title().ifEmpty { null }
        val body = doc.body()?.html() ?: doc.html()
        val markdown = converter.convert(body)
        return markdown to title
    }
}
