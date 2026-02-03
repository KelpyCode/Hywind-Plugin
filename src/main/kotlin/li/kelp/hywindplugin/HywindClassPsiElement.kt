package li.kelp.hywindplugin

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiFile

// Lightweight PSI element representing a resolved Hywind class token. We keep it minimal —
// the documentation provider will look up metadata by the element name.
class HywindClassPsiElement(manager: PsiManager, language: Language, private val _name: String) : LightElement(manager, language), PsiNamedElement {
    override fun getName(): String = _name
    override fun setName(name: String): PsiElement { throw UnsupportedOperationException() }
    override fun getText(): String = _name
    override fun getContainingFile(): PsiFile? = null
    override fun toString(): String = "HywindClassPsiElement(${_name})"
}
