package li.kelp.hywindplugin

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue

class HywindDocumentationProvider : DocumentationProvider {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null

        // If caret is on the attribute name itself (e.g. user hovered on "class"), try to prefer
        // a Hywind class from that attribute's value if available.
        if (element is XmlAttribute && element.name == "class") {
            val valueEl = element.valueElement
            if (valueEl != null) {
                val classToken = findTokenUnderCaret(originalElement, element) ?: run {
                    val parts = valueEl.value.split(Regex("\\s+"))
                    val known = HywindMetaLoader.getClasses().map { it.className }.toSet()
                    parts.firstOrNull { it in known }
                }
                if (classToken != null) {
                    val cls = HywindMetaLoader.getClasses().find { it.className == classToken }
                    if (cls != null) return buildClassDoc(cls.className, cls.description, cls.previewColor, cls.code, cls.origin)
                }
            }
        }

        // First: if caret is inside a class attribute value, prefer showing the Hywind class doc
        var attrValue: XmlAttributeValue? = PsiTreeUtil.getParentOfType(originalElement ?: element, XmlAttributeValue::class.java, false)
        if (attrValue == null) {
            attrValue = PsiTreeUtil.getParentOfType(element, XmlAttributeValue::class.java, false)
        }
        if (attrValue == null) {
            // maybe the caret was over the attribute name token; try finding the attribute and its valueElement
            val attrParent = PsiTreeUtil.getParentOfType(originalElement ?: element, XmlAttribute::class.java, false)
                ?: PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false)
            attrValue = attrParent?.valueElement
        }
        if (attrValue != null) {
            // try precise detection first
            val classToken = findTokenUnderCaret(originalElement, element)
                ?: run {
                    // fallback: find any known Hywind class inside attribute value
                    val parts = attrValue.value.split(Regex("\\s+"))
                    val known = HywindMetaLoader.getClasses().map { it.className }.toSet()
                    parts.firstOrNull { it in known }
                }
            if (classToken != null) {
                val cls = HywindMetaLoader.getClasses().find { it.className == classToken }
                if (cls != null) return buildClassDoc(cls.className, cls.description, cls.previewColor, cls.code, cls.origin)
            }
        }

        // Try to determine the token under caret more reliably using originalElement and ancestors
        val token = findTokenUnderCaret(originalElement, element)

        var name: String? = token
        if (name == null) {
            // Fallback heuristics
            if (element is XmlAttributeValue) {
                val v = element.value
                name = extractToken(v)
            } else if (element is XmlAttribute) {
                name = element.name
            } else {
                val text = originalElement?.text ?: element.text
                name = extractToken(text)
            }
        }

        if (name == null) return null

        // Lookup in props and classes (normal flow)
        val prop = HywindMetaLoader.getProps().find { it.propName == name }
        if (prop != null) {
            return buildPropDoc(prop.propName, prop.description, prop.code, prop.origin)
        }
        val cls = HywindMetaLoader.getClasses().find { it.className == name }
        if (cls != null) {
            return buildClassDoc(cls.className, cls.description, cls.previewColor, cls.code, cls.origin)
        }
        return null
    }

    // Provide a short quick-info string used by some hover/quick-info flows to override generic details
    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null

        // If hovering the attribute name "class", try to show the first known Hywind class from its value
        if (element is XmlAttribute && element.name == "class") {
            val valueEl = element.valueElement
            if (valueEl != null) {
                val classToken = findTokenUnderCaret(originalElement, element) ?: run {
                    val parts = valueEl.value.split(Regex("\\s+"))
                    val known = HywindMetaLoader.getClasses().map { it.className }.toSet()
                    parts.firstOrNull { it in known }
                }
                if (classToken != null) {
                    val cls = HywindMetaLoader.getClasses().find { it.className == classToken }
                    if (cls != null) {
                        val colorHex = cls.previewColor?.let { toHex(it.r, it.g, it.b) } ?: ""
                        val namePart = if (colorHex.isNotEmpty()) "$classToken ($colorHex)" else classToken
                        return "$namePart — ${shortSummary(cls.description)}"
                    }
                }
            }
        }

        val token = findTokenUnderCaret(originalElement, element) ?: when (element) {
            is XmlAttributeValue -> extractToken(element.value)
            is XmlAttribute -> element.name
            else -> extractToken(originalElement?.text ?: element.text)
        }
        if (token == null) return null

        val prop = HywindMetaLoader.getProps().find { it.propName == token }
        if (prop != null) {
            return shortSummary(prop.description)
        }
        val cls = HywindMetaLoader.getClasses().find { it.className == token }
        if (cls != null) {
            val colorHex = cls.previewColor?.let { toHex(it.r, it.g, it.b) } ?: ""
            val namePart = if (colorHex.isNotEmpty()) "$token ($colorHex)" else token
            return "$namePart — ${shortSummary(cls.description)}"
        }
        return null
    }

    private fun toHex(r: Int, g: Int, b: Int): String {
        return String.format("#%02X%02X%02X", r, g, b)
    }

    private fun shortSummary(desc: String?): String {
        if (desc == null) return ""
        val firstLine = desc.lines().firstOrNull()?.trim() ?: ""
        return escapeHtml(firstLine)
    }

    private fun findTokenUnderCaret(originalElement: PsiElement?, contextElement: PsiElement?): String? {
        val tokenRegex = "[A-Za-z0-9:_-]+".toRegex()
        val origEl = originalElement ?: contextElement ?: return null

        // If originalElement is inside an attribute value, try to find the token under caret first
        var attrValue = PsiTreeUtil.getParentOfType(origEl, XmlAttributeValue::class.java, false)
        if (attrValue == null) {
            // maybe original element is inside the attribute node (e.g., attribute name token).
            val attrParent = PsiTreeUtil.getParentOfType(origEl, XmlAttribute::class.java, false)
            attrValue = attrParent?.valueElement
        }
        if (attrValue != null) {
            try {
                val value = attrValue.value // unquoted value
                val attrRange = attrValue.textRange
                val origRange = origEl.textRange
                // compute caret index within the value text.
                // Prefer the midpoint of the original element (handles multi-character leaves).
                val origMid = origRange.startOffset + origRange.length / 2
                var caretIndex = origMid - (attrRange.startOffset + 1) // subtract opening quote
                if (caretIndex < 0) caretIndex = 0
                if (caretIndex >= value.length) caretIndex = value.length - 1

                // Scan left to token boundary
                var left = caretIndex
                while (left > 0) {
                    val ch = value[left - 1]
                    if (!ch.toString().matches("[A-Za-z0-9:_-]".toRegex())) break
                    left--
                }
                // Scan right to token boundary
                var right = caretIndex
                while (right < value.length - 1) {
                    val ch = value[right + 1]
                    if (!ch.toString().matches("[A-Za-z0-9:_-]".toRegex())) break
                    right++
                }
                if (left <= right && left >= 0 && right < value.length) {
                    val token = value.substring(left, right + 1)
                    if (token.matches(tokenRegex)) return token
                }
            } catch (_: Exception) {
                // fall through to other heuristics
            }

            // fallback heuristics based on originalElement text
            val origText = origEl.text
            if (origText.isNotBlank()) {
                val cleaned = origText.trim().trim('"', '\'')
                if (cleaned.matches(tokenRegex)) return cleaned
            }
            val parts = attrValue.value.split(Regex("\\s+"))
            parts.firstOrNull { it == origText }?.let { return it }
            parts.firstOrNull { it.contains(origText) }?.let { return it }

            // Additional fallback: prefer the first token that matches a known Hywind class
            val known = HywindMetaLoader.getClasses().map { it.className }.toSet()
            parts.firstOrNull { it in known }?.let { return it }

            // fallback: return first token
            parts.firstOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        // If the original element text itself looks like a token, return it
        val origText = (originalElement ?: contextElement)?.text ?: return null
        if (origText.matches(tokenRegex)) return origText

        // If originalElement is part of an attribute name, return the attribute's name
        val attrParent = PsiTreeUtil.getParentOfType(origEl, XmlAttribute::class.java, false)
        if (attrParent != null) return attrParent.name

        return null
    }

    private fun extractToken(text: String?): String? {
        if (text == null) return null
        // find a hyphenated or word token like hy-prop-active or p-4
        val regex = "[A-Za-z0-9:_-]+".toRegex()
        val match = regex.find(text)
        return match?.value
    }

    // Simple HTML-escaping to safely embed descriptions/names in the doc HTML
    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun buildPropDoc(name: String, description: String?, code: String?, origin: String?): String {
        val escName = escapeHtml(name)
        val descHtml = description?.let { escapeHtml(it).replace("\n", "<br/>") } ?: ""
        val originHtml = origin?.let { "<div style=\"font-size:0.9em;color:#666;margin-top:6px;\">Origin: ${escapeHtml(it)}</div>" } ?: ""
        val codeHtml = code?.let { "<pre style=\"background:#f6f8fa;padding:8px;border-radius:4px;overflow:auto;\"><code>${escapeHtml(it)}</code></pre>" } ?: ""
        return """
            <html>
            <body>
              <h3>$escName</h3>
              <div>$descHtml</div>
              $codeHtml
              $originHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildClassDoc(name: String, description: String?, color: ColorObject?, code: String?, origin: String?): String {
        val escName = escapeHtml(name)
        val descHtml = description?.let { escapeHtml(it).replace("\n", "<br/>") } ?: ""
        val colorHtml = color?.let { c ->
            "<div style=\"width:48px;height:20px;border:1px solid #000;display:inline-block;background-color:rgb(${c.r},${c.g},${c.b});margin-bottom:8px;vertical-align:middle;\"></div>"
        } ?: ""
        val originHtml = origin?.let { "<div style=\"font-size:0.9em;color:#666;margin-top:6px;\">Origin: ${escapeHtml(it)}</div>" } ?: ""
        val codeHtml = code?.let { "<pre style=\"background:#f6f8fa;padding:8px;border-radius:4px;overflow:auto;\"><code>${escapeHtml(it)}</code></pre>" } ?: ""
        return """
            <html>
            <body>
              <h3>$escName</h3>
              $colorHtml
              <div>$descHtml</div>
              $codeHtml
              $originHtml
            </body>
            </html>
        """.trimIndent()
    }
}
