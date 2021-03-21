package modelsEvaluator

import com.fasterxml.jackson.core.JsonProcessingException
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
import com.jetbrains.rd.util.string.printToString
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
import kotlin.streams.asSequence

class VarNamer {
    companion object {
        private val LOG = logger<VarNamer>()
        private const val TRANSFORMER_SERVER_URL = "http://127.0.0.1:5000/"
        fun predict(project: Project, dir: Path) {
            val mapper = ObjectMapper()
            val predictionsFile: File = dir.resolve(project.name + "_predictions.txt").toFile()
            predictionsFile.parentFile.mkdir()
            predictionsFile.createNewFile()
            val predictedFilePaths = predictionsFile.bufferedReader().lines().asSequence()
                .map { line ->
                    try {
                        mapper.readValue(line, Map::class.java).keys.first()
                    } catch (e: JsonProcessingException) {
                        null
                    }
                }.filterNotNull()
                .toHashSet()
            val files = FileTypeIndex.getFiles(
                JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)
            ).filter { file -> file.path !in predictedFilePaths }
            var progress = 0
            val total = files.size
            val start = Instant.now()
            val psiManager = PsiManager.getInstance(project)
            println("Number of files to parse: $total")
            for (file in files) {
                val psiFile = psiManager.findFile(file)
                if (psiFile != null) {
                    val filePath = file.path
                    val filePredictions = HashMap<String, List<VarNamePredictions>>()
                    filePredictions[filePath] = predictPsiFile(psiFile)
                    FileOutputStream(predictionsFile, true).bufferedWriter().use {
                        it.write(mapper.writeValueAsString(filePredictions))
                        it.newLine()
                    }
                    val fraction = ++progress / total.toDouble()
                    if (progress % (total / 100) == 0) {
                        val timeLeft = Duration.ofSeconds(
                            (Duration.between(start, Instant.now()).toSeconds() * (1 / fraction - 1)).toLong()
                        )
                        System.out.printf(
                            "Status:\t%.0f%%; Time left:\t%s\r",
                            fraction * 100.0,
                            timeLeft.printToString()
                        )
                    }
                } else {
                    println("PSI isn't found")
                }
            }
            val end = Instant.now()
            val timeSpent = Duration.between(start, end)
            System.out.printf(
                "Done in %s\n",
                timeSpent.printToString()
            )
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

        private fun predictWithTransformer(variable: PsiVariable): Any {
            val variableFeatures = DatasetManager.getVariableFeatures(variable, variable.containingFile)
            return HttpRequests.post(TRANSFORMER_SERVER_URL, HttpRequests.JSON_CONTENT_TYPE)
                .connect(HttpRequests.RequestProcessor {
                    val objectMapper = ObjectMapper()
                    it.write(objectMapper.writeValueAsBytes(variableFeatures))
                    val str = it.readString()
                    objectMapper.readValue(str, Any::class.java)
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
    val transformerResponseTime: Double,
    val linePosition: Int,
    val psiInterface: String
)

class ModelPrediction(val name: Any, val p: Double)