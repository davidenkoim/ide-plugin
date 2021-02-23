package dataMiner

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.id.names.suggesting.NotificationsUtil
import org.jetbrains.id.names.suggesting.dataset.DatasetManager
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {

    override fun getCommandName(): String = "psiMiner"

    override fun main(args: Array<out String>) {
        try {
            val dataset = File(args[1])
            var projectToClose: Project? = null
            val projectList = dataset.list { dir, _ ->
                return@list dir.isDirectory
            } ?: return
            for (projectDir in projectList) {
                val projectPath = dataset.resolve(projectDir)
                val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue
                println("Building dataset for $projectDir...")
                println("Project is opened.")

                DatasetManager.build(project)

                println("Dataset for $projectDir is build.")
                projectToClose = project
            }
        } catch (e: IllegalArgumentException) {
            println(e.message)
        } finally {
            exitProcess(0)
        }
    }
}
