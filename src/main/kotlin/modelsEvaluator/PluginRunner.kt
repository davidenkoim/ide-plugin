package modelsEvaluator

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {
    private val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    private val javaSmallTest =
        listOf("hadoop", "libgdx")
//        listOf("TestProject")
    private val saveDir = "C:\\Users\\Igor\\IdeaProjects\\ide-plugin\\predictions"

    override fun getCommandName(): String = "modelsEvaluator"

    override fun main(args: Array<out String>) {
        try {
            val dataset = File(args[1])
            trainNGramOn(dataset, javaSmallTrain)
//            evaluateOn(dataset, javaSmallTrain, Paths.get(saveDir, "train"))
            evaluateOn(dataset, javaSmallTest, Paths.get(saveDir, "test"))
        } catch (e: IllegalArgumentException) {
            println(e.message)
        } catch (e: OutOfMemoryError) {
            println("Not enough memory!")
            println(e.message)
        } finally {
            exitProcess(0)
        }
    }

    private fun trainNGramOn(dataset: File, projectList: List<String>) {
        println("Training NGram global model...")
        var projectToClose: Project? = null
        val modelRunner = IdNamesSuggestingModelManager.getInstance()
            .getModelRunner(GlobalVariableNamesContributor::class.java) as IdNamesNGramModelRunner
        val vocabulary = modelRunner.vocabulary
        for (projectDir in projectList) {
            val projectPath = dataset.resolve(projectDir)
            println("Opening project $projectDir...")
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue

//            println("Loading global model...")
//            (IdNamesSuggestingModelManager.getInstance()
//                .getModelRunner(GlobalVariableNamesContributor::class.java) as IdNamesNGramModelRunner).load()
            IdNamesSuggestingModelManager.getInstance().trainGlobalNGramModel(project, null, false)
            println("Vocabulary size: ${vocabulary.size()}")

            projectToClose = project
        }
        if (projectToClose != null) {
            ProjectUtil.closeAndDispose(projectToClose)
        }
    }

    private fun evaluateOn(dataset: File, projectList: List<String>, dir: Path) {
        println("Evaluating models...")
        var projectToClose: Project? = null
        for (projectDir in projectList) {
            val projectPath = dataset.resolve(projectDir)
            println("Opening project $projectDir...")
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue

            VarNamer.predict(project, dir)

            projectToClose = project
        }
        if (projectToClose != null) {
            ProjectUtil.closeAndDispose(projectToClose)
        }
    }
}
