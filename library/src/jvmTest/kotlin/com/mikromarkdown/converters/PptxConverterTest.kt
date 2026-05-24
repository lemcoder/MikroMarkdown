package com.mikromarkdown.converters

import com.mikromarkdown.StreamInfo
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.junit.jupiter.api.Test
import java.awt.geom.Rectangle2D
import java.io.ByteArrayOutputStream
import kotlin.test.assertTrue

class PptxConverterTest {
    private val converter = PptxConverter()
    private val info = StreamInfo(extension = "pptx")

    @Test
    fun `accepts pptx extension`() {
        assertTrue(converter.accepts("".byteInputStream(), info))
    }

    @Test
    fun `slide number heading included`() {
        val baos = buildSlideShow {
            createSlide()
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("## Slide 1"), "Expected slide heading in:\n${result.markdown}")
    }

    @Test
    fun `text box content is extracted`() {
        val baos = buildSlideShow {
            val slide = createSlide()
            val box = slide.createTextBox()
            box.anchor = Rectangle2D.Double(0.0, 0.0, 5_000_000.0, 1_000_000.0)
            box.setText("Hello from PPTX")
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("Hello from PPTX"), "Expected text in:\n${result.markdown}")
    }

    @Test
    fun `multiple slides produce multiple headings`() {
        val baos = buildSlideShow {
            repeat(3) { createSlide() }
        }
        val result = converter.convert(baos.toByteArray().inputStream(), info)
        assertTrue(result.markdown.contains("## Slide 1"))
        assertTrue(result.markdown.contains("## Slide 2"))
        assertTrue(result.markdown.contains("## Slide 3"))
    }

    private fun buildSlideShow(block: XMLSlideShow.() -> Unit): ByteArrayOutputStream {
        val slideShow = XMLSlideShow()
        slideShow.block()
        val baos = ByteArrayOutputStream()
        slideShow.write(baos)
        slideShow.close()
        return baos
    }
}
