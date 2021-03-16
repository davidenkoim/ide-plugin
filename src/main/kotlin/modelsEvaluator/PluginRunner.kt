package modelsEvaluator

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner
import java.io.File
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {
    val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    val javaSmallTest = listOf("hadoop", "libgdx")

    override fun getCommandName(): String = "modelsEvaluator"

    override fun main(args: Array<out String>) {
        try {
            val dataset = File(args[1])
            trainNGramOn(dataset, javaSmallTrain)
            evaluateOn(dataset, javaSmallTrain)
            evaluateOn(dataset, javaSmallTest)
        } catch (e: IllegalArgumentException) {
            println(e.message)
        } finally {
            exitProcess(0)
        }
    }

    private fun trainNGramOn(dataset: File, projectList: List<String>) {
        println("Training NGram global model...")
        var projectToClose: Project? = null
        for (projectDir in projectList) {
            val projectPath = dataset.resolve(projectDir)
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue
            println("Project is opened.")

            (IdNamesSuggestingModelManager.getInstance()
                .getModelRunner(GlobalVariableNamesContributor::class.java) as IdNamesNGramModelRunner).load()
            IdNamesSuggestingModelManager.getInstance().trainGlobalNGramModel(project, null, true)

            projectToClose = project
        }
        if (projectToClose != null) {
            ProjectUtil.closeAndDispose(projectToClose)
        }
    }

    private fun evaluateOn(dataset: File, projectList: List<String>) {
        TODO("Not yet implemented")
    }
}
