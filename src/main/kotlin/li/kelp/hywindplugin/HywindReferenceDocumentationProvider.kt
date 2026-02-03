package li.kelp.hywindplugin

import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement

class HywindReferenceDocumentationProvider : DocumentationProvider {
    // This provider will be used when the hovered element is a HywindClassPsiElement returned by the reference resolver
    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        if (element == null) return null

        val token = when (element) {
            is HywindClassPsiElement -> element.text
            else -> originalElement?.text ?: element.text
        } ?: return null

        val cleaned = token.trim().trim('"', '\'')
        val cls = HywindMetaLoader.getClasses().find { it.className == cleaned }
        if (cls != null) return buildClassDoc(cls.className, cls.description, cls.previewColor, cls.code, cls.origin)
        return null
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val token = when (element) {
            is HywindClassPsiElement -> element.text
            else -> originalElement?.text ?: element?.text
        } ?: return null
        val cleaned = token.trim().trim('"', '\'')
        val cls = HywindMetaLoader.getClasses().find { it.className == cleaned }
        return cls?.description?.lines()?.firstOrNull()?.trim()
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
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
