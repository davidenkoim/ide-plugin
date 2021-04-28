package tools.graphVarMiner

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import java.io.File
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {

    override fun getCommandName(): String = "graphVarMiner"

    override fun main(args: Array<out String>) {
        try {
            val dataset = File(args[1])
            val prefix = args[2]
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

//                Clear cache if there is not enough RAM
//                val memoryWathcer = LowMemoryWatcher.register {
//                    println("Clear cache for $projectDir")
//                    ResolveCache.getInstance(project).clearCache(true)
//                }
                GraphDatasetExtractor.build(project, prefix + "_$projectDir")
//                memoryWathcer.stop()

                println("Dataset for $projectDir is built.")
                projectToClose = project
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            exitProcess(0)
        }
    }
}
