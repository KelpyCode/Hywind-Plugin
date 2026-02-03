package li.kelp.hywindplugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service

@State(name = "HywindSettings", storages = [Storage("hywind-plugin.xml")])
@Service
class HywindSettings : PersistentStateComponent<HywindSettings.State> {
    data class State(var originOverride: String? = null, var metaUrl: String = "https://raw.githubusercontent.com/KelpyCode/HyUI-Hywind/refs/heads/main/hywind-meta.json")

    private var state = State()

    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    companion object {
        fun getInstance(): HywindSettings = com.intellij.openapi.application.ApplicationManager.getApplication().getService(HywindSettings::class.java)
    }

    fun originOverride(): String? = state.originOverride
    fun metaUrl(): String = state.metaUrl
    fun setOriginOverride(origin: String?) { state = state.copy(originOverride = origin) }
    fun setMetaUrl(url: String) { state = state.copy(metaUrl = url) }
}
