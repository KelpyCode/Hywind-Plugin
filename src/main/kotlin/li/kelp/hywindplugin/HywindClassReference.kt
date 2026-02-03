package li.kelp.hywindplugin

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceContributor
import com.intellij.openapi.util.TextRange

class HywindClassReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Register for XML/HTML attribute values; provider will split tokens and resolve to HywindClassPsiElement
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttributeValue::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val xmlValue = element as? XmlAttributeValue ?: return PsiReference.EMPTY_ARRAY
                val text = xmlValue.value

                val refs = mutableListOf<PsiReference>()
                // Build token ranges inside unquoted value using same logic as documentation provider
                val regex = "[A-Za-z0-9:_-]+".toRegex()
                for (m in regex.findAll(text)) {
                    val startInValue = m.range.first
                    val endInValue = m.range.last + 1
                    val token = m.value

                    // The attribute's text includes quotes; compute offset of value inside attribute text
                    val attrText = xmlValue.text
                    val valueIndexInAttr = attrText.indexOf(text).coerceAtLeast(0)
                    val rangeInAttr = TextRange(valueIndexInAttr + startInValue, valueIndexInAttr + endInValue)

                    refs.add(object : PsiReferenceBase<XmlAttributeValue>(xmlValue, rangeInAttr) {
                        override fun resolve(): PsiElement? {
                            val manager = element.manager
                            return HywindClassPsiElement(manager, element.language, token)
                        }

                        override fun getVariants(): Array<Any> = emptyArray()
                    })
                }
                return refs.toTypedArray()
            }
        })
    }
}
