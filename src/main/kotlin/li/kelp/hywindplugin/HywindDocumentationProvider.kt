package li.kelp.hywindplugin

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue

class HywindDocumentationProvider : DocumentationProvider {
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null

        // If the hovered element is a resolved HywindClassPsiElement, return its docs directly
        if (element is HywindClassPsiElement) {
            val name = element.name
            val cls = HywindMetaLoader.getClasses().find { it.className == name }
            if (cls != null) return buildClassDoc(cls.className, cls.description, cls.previewColor, cls.code, cls.origin)
        }

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

        // If the hovered element is a resolved HywindClassPsiElement, show a short quick info
        if (element is HywindClassPsiElement) {
            val name = element.name
            val cls = HywindMetaLoader.getClasses().find { it.className == name }
            if (cls != null) {
                val colorHex = cls.previewColor?.let { toHex(it.r, it.g, it.b) } ?: ""
                val namePart = if (colorHex.isNotEmpty()) "$name ($colorHex)" else name
                return "$namePart — ${shortSummary(cls.description)}"
            }
        }

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

                // Build tokens using regex.findAll to get exact offsets inside the unquoted value
                val tokens = mutableListOf<Triple<Int, Int, String>>()
                val regex = "[A-Za-z0-9:_-]+".toRegex()
                for (m in regex.findAll(value)) {
                    tokens.add(Triple(m.range.first, m.range.last + 1, m.value))
                }

                // Try direct match based on the original element text
                val origText = (originalElement ?: contextElement)?.text?.trim()?.trim('"', '\'')
                if (!origText.isNullOrBlank()) {
                    tokens.firstOrNull { it.third == origText }?.let { return it.third }
                    tokens.firstOrNull { it.third.contains(origText) }?.let { return it.third }
                }

                // Choose smallest overlapping PSI element (originalElement or contextElement), or attrValue
                val candidates = listOfNotNull(originalElement, contextElement)
                    .mapNotNull { el ->
                        try {
                            val r = el.textRange
                            if (r.endOffset <= attrRange.startOffset || r.startOffset >= attrRange.endOffset) return@mapNotNull null
                            Pair(el, r)
                        } catch (_: Exception) {
                            null
                        }
                    }
                val chosenEl = candidates.minByOrNull { it.second.endOffset - it.second.startOffset }?.first ?: attrValue

                // compute caret index inside unquoted value using chosenEl midpoint
                val attrText = attrValue.text
                val indexInAttr = attrText.indexOf(value).coerceAtLeast(0)
                val valueOffset = attrRange.startOffset + indexInAttr

                // Prefer using the most precise overlapping element's range for caret mapping.
                // If originalElement or contextElement overlapped, use its start offset; otherwise use chosenEl midpoint.
                var caretIndex: Int
                try {
                    val precise = listOfNotNull(originalElement, contextElement)
                        .firstOrNull { el ->
                            val r = el.textRange
                            r.endOffset > attrRange.startOffset && r.startOffset < attrRange.endOffset
                        }
                    if (precise != null) {
                        val r = precise.textRange
                        // prefer the start offset so hovering at the start of a token maps inside it
                        caretIndex = (r.startOffset - valueOffset)
                    } else {
                        val chosenRange = chosenEl.textRange
                        val chosenMid = (chosenRange.startOffset + chosenRange.endOffset) / 2
                        caretIndex = (chosenMid - valueOffset)
                    }
                } catch (_: Exception) {
                    val chosenRange = chosenEl.textRange
                    val chosenMid = (chosenRange.startOffset + chosenRange.endOffset) / 2
                    caretIndex = (chosenMid - valueOffset)
                }

                if (caretIndex < 0) caretIndex = 0
                if (value.isEmpty()) return null
                if (caretIndex >= value.length) caretIndex = value.length - 1

                // Find token that contains caretIndex
                tokens.firstOrNull { caretIndex >= it.first && caretIndex < it.second }?.let { return it.third }

                // If none contains caret, pick nearest token by center distance
                var best: String? = null
                var bestDist = Int.MAX_VALUE
                for ((s, e, t) in tokens) {
                    val center = (s + e) / 2
                    val dist = kotlin.math.abs(center - caretIndex)
                    if (dist < bestDist) {
                        bestDist = dist
                        best = t
                    }
                }
                if (best != null) return best

            } catch (_: Exception) {
                // fall through to other heuristics
            }

            // fallback heuristics based on originalElement text
            val origText2 = origEl.text
            if (origText2.isNotBlank()) {
                val cleaned = origText2.trim().trim('"', '\'')
                if (cleaned.matches(tokenRegex)) return cleaned
            }
            val parts = attrValue.value.split(Regex("\\s+"))
            parts.firstOrNull { it == origText2 }?.let { return it }
            parts.firstOrNull { it.contains(origText2) }?.let { return it }

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
