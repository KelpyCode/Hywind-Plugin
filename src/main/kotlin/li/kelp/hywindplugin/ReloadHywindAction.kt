package li.kelp.hywindplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import kotlin.concurrent.thread

class ReloadHywindAction : AnAction("Reload Hywind Meta") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        // Run download on background thread
        thread {
            val ok = HywindMetaLoader.reloadFromRemote()
            ApplicationManager.getApplication().invokeLater {
                if (ok) {
                    Messages.showInfoMessage(project, "Hywind metadata reloaded successfully.", "Hywind")
                } else {
                    Messages.showErrorDialog(project, "Failed to reload Hywind metadata. Check your network or settings.", "Hywind")
                }
            }
        }
    }
}
