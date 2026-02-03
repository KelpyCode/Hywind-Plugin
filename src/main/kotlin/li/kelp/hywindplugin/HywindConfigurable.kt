package li.kelp.hywindplugin

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.JLabel
import javax.swing.BoxLayout

class HywindConfigurable : Configurable {
    private var panel: JPanel? = null
    private var originField: JTextField? = null
    private var urlField: JTextField? = null

    override fun getDisplayName(): String = "Hywind"

    override fun createComponent(): JComponent? {
        val settings = HywindSettings.getInstance()
        panel = JPanel()
        panel!!.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel!!.add(JLabel("Origin override (optional):"))
        originField = JTextField(settings.originOverride() ?: "")
        panel!!.add(originField)

        panel!!.add(JLabel("Hywind meta URL:"))
        urlField = JTextField(settings.metaUrl())
        panel!!.add(urlField)

        return panel
    }

    override fun isModified(): Boolean {
        val settings = HywindSettings.getInstance()
        return originField?.text != settings.originOverride() || urlField?.text != settings.metaUrl()
    }

    override fun apply() {
        val settings = HywindSettings.getInstance()
        settings.setOriginOverride(originField?.text?.takeIf { it.isNotBlank() })
        settings.setMetaUrl(urlField?.text ?: settings.metaUrl())
    }

    override fun reset() {
        val settings = HywindSettings.getInstance()
        originField?.text = settings.originOverride() ?: ""
        urlField?.text = settings.metaUrl()
    }

    override fun disposeUIResources() {
        panel = null
        originField = null
        urlField = null
    }
}
