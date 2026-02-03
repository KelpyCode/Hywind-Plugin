package li.kelp.hywindplugin

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext
import com.intellij.lang.xml.XMLLanguage
import javax.swing.Icon
import com.intellij.ui.JBColor
import java.awt.Graphics2D
import java.awt.RenderingHints

class MyHtmlCompletionContributor : CompletionContributor() {
    init {
        // Suggestions for HTML attribute values (e.g. class="...")
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(XmlAttributeValue::class.java).withLanguage(XMLLanguage.INSTANCE),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val attrValue = parameters.position.parent as? XmlAttributeValue ?: return
                    val attr = attrValue.parent as? XmlAttribute ?: return
                    if (attr.name == "class") {
                        val hywindClasses = HywindMetaLoader.getClasses()
                        for (c in hywindClasses) {
                            var builder = LookupElementBuilder.create(c.className)
                                .withTypeText(c.description ?: "", true)

                            val icon = c.previewColor?.let { rgb ->
                                createColorIcon(rgb.r, rgb.g, rgb.b)
                            }
                            if (icon != null) builder = builder.withIcon(icon)

                            // show origin (if present) as right-side type text, prefer origin over description if short
                            if (!c.origin.isNullOrBlank()) {
                                builder = builder.withTypeText(c.origin, true)
                            }

                            // show short description as tail text
                            if (!c.description.isNullOrBlank()) {
                                builder = builder.withTailText("  ${c.description!!.lines().firstOrNull()}", true)
                            }

                            result.addElement(builder)
                        }
                    }
                }
            }
        )

        // Suggestions for HTML attribute names (props)
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withParent(XmlAttribute::class.java),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val hywindProps = HywindMetaLoader.getProps()
                    for (p in hywindProps) {
                        var builder = LookupElementBuilder.create(p.propName)
                            .withTypeText(p.description ?: "", true)
                        if (!p.origin.isNullOrBlank()) builder = builder.withTailText("  ${p.origin}", true)
                        result.addElement(builder)
                    }
                }
            }
        )
    }

    private fun createColorIcon(r: Int, g: Int, b: Int): Icon {
        val w = 16
        val h = 12
        val fill = JBColor(java.awt.Color(r, g, b), java.awt.Color(r, g, b))
        val border = JBColor(java.awt.Color(0, 0, 0, 90), java.awt.Color(0, 0, 0, 90))
        return object : Icon {
            override fun getIconWidth(): Int = w
            override fun getIconHeight(): Int = h
            override fun paintIcon(c: java.awt.Component?, g: java.awt.Graphics?, x: Int, y: Int) {
                if (g == null) return
                val g2 = g.create() as Graphics2D
                try {
                    g2.translate(x, y)
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    g2.color = fill
                    g2.fillRect(0, 0, w, h)
                    g2.color = border
                    g2.drawRect(0, 0, w - 1, h - 1)
                } finally {
                    g2.dispose()
                }
            }
        }
    }
}