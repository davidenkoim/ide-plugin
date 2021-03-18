package modelsEvaluator

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.HttpRequests
import org.jetbrains.id.names.suggesting.VarNamePrediction
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor
import org.jetbrains.id.names.suggesting.dataset.DatasetManager
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.math.roundToInt

class VarNamer {
    companion object {
        private val LOG = logger<VarNamer>()
        private const val TRANSFORMER_SERVER_URL = "http://127.0.0.1:5000/evaluate"
        fun predict(project: Project, dir: Path) {
            val files = FileTypeIndex.getFiles(
                JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
            )
            var progress = .0
            val total = files.size.toDouble()

            val predictionsFile: File = dir.resolve(project.name + "_predictions.txt").toFile()
            predictionsFile.parentFile.mkdir()
            predictionsFile.createNewFile()
            predictionsFile.printWriter().print("")
            val predictionsWriter = FileOutputStream(predictionsFile, true).bufferedWriter()
            val mapper = ObjectMapper()
            val start = Instant.now()
            val psiManager = PsiManager.getInstance(project)
            print("Number of files to parse: ${files.size}")
            for (file in files) {
                val psiFile = psiManager.findFile(file)
                if (psiFile != null) {
                    val filePath = file.path
                    val filePredictions = HashMap<String, List<VarNamePredictions>>()
                    filePredictions[filePath] = predictPsiFile(psiFile)
                    predictionsWriter.use {
                        it.write(mapper.writeValueAsString(filePredictions))
                        it.newLine()
                    }
                    val timeLeft = Duration.between(start, Instant.now()).toMillis() * (total / ++progress - 1) / 1000.0
                    val minutesLeft = timeLeft.roundToInt() / 60
                    val secondsLeft = timeLeft - minutesLeft * 60.0
                    System.out.printf("Status:\t%.2f%%; Time left:\t%d min. %.1f s.\r", progress / total * 100.0, minutesLeft, secondsLeft)
                } else {
                    println("PSI isn't found")
                }
            }
            val end = Instant.now()
            val timeSpent = Duration.between(start, end)
            val minutes = timeSpent.toMinutes()
            val seconds = timeSpent.toMillis() / 1000.0 - 60.0 * minutes
            System.out.printf(
                "Done in %d min. %.1f s.\n",
                minutes, seconds
            )
        }

        private fun save(project: Project, predictions: Any, dir: Path) {
        }

        private fun predictPsiFile(file: PsiFile): List<VarNamePredictions> {
            val fileEditorManager = FileEditorManager.getInstance(file.project)
            val editor = fileEditorManager.openTextEditor(
                OpenFileDescriptor(
                    file.project,
                    file.virtualFile
                ), true
            )!!
            val predictionsList = SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(TextRange(0, 64 * 1024)) // first 128 KB of chars
                .filter { element: PsiElement? -> element is PsiVariable }
                .toList()
                .asSequence()
                .filterNotNull()
                .map { e -> e as PsiVariable }
                .map { v -> predictVarName(v, editor) }
                .filterNotNull()
                .toList()
            fileEditorManager.closeFile(file.virtualFile)
            return predictionsList
        }

        private fun predictVarName(variable: PsiVariable, editor: Editor): VarNamePredictions? {
            val nameIdentifier = variable.nameIdentifier
            if (nameIdentifier === null || nameIdentifier.text == "") return null

            var startTime = System.nanoTime()
            val nGramPredictions = predictWithNGram(variable)
            val nGramEvaluationTime = (System.nanoTime() - startTime) / 1.0e9

            startTime = System.nanoTime()
            val transformerPredictions = predictWithTransformer(variable)
            val transformerEvaluationTime = (System.nanoTime() - startTime) / 1e9

            return VarNamePredictions(
                nameIdentifier.text,
                nGramPredictions,
                nGramEvaluationTime,
                transformerPredictions,
                transformerEvaluationTime,
                getLinePosition(nameIdentifier, editor),
                variable.javaClass.interfaces[0].simpleName
            )
        }

        private fun predictWithNGram(variable: PsiVariable): List<ModelPrediction> {
            val nameSuggestions: List<VarNamePrediction> = ArrayList()
            val contributor = VariableNamesContributor.EP_NAME.findExtension(GlobalVariableNamesContributor::class.java)
            contributor!!.contribute(variable, nameSuggestions, false)
            return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
        }

        private fun predictWithTransformer(variable: PsiVariable): List<*> {
            val variableFeatures = DatasetManager.getVariableFeatures(variable, variable.containingFile)
            return HttpRequests.post(TRANSFORMER_SERVER_URL, HttpRequests.JSON_CONTENT_TYPE)
                .connect(HttpRequests.RequestProcessor {
                    val objectMapper = ObjectMapper()
                    it.write(objectMapper.writeValueAsBytes(variableFeatures))
                    val str = it.readString()
                    objectMapper.readValue(str, List::class.java)
                }, null, LOG)
        }

        private fun getLinePosition(identifier: PsiElement, editor: Editor): Int {
            return editor.offsetToLogicalPosition(identifier.textOffset).line
        }
    }
}

class VarNamePredictions(
    val groundTruth: String,
    val nGramPrediction: List<ModelPrediction>,
    val nGramEvaluationTime: Double,
    val transformerPrediction: Any,
    val transformerEvaluationTime: Double,
    val linePosition: Int,
    val psiInterface: String
)

class ModelPrediction(val name: Any, val probability: Double)