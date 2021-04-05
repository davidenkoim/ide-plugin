package varMiner

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import org.jetbrains.id.names.suggesting.dataset.DatasetManager
import java.io.File
import java.nio.file.Paths
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {

    override fun getCommandName(): String = "varMiner"

    override fun main(args: Array<out String>) {
        try {
            val dataset = File(args[1])
            val saveDir = Paths.get(args[2])
            var projectToClose: Project? = null
            val projectList = dataset.list { dir, _ ->
                return@list dir.isDirectory
            } ?: return
            var progress = 0
            val total = projectList.size
            for (projectDir in projectList) {
                println("${++progress}/$total")
                println("Building dataset for $projectDir...")
                val projectPath = dataset.resolve(projectDir)
                val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue
                println("Project is opened.")

                DatasetManager.build(project, saveDir)

                println("Dataset for $projectDir is built.")
                projectToClose = project
            }
        } catch (e: IllegalArgumentException) {
            println(e.message)
        } finally {
            exitProcess(0)
        }
    }
}
