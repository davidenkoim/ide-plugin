package model

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiMethod
import downloader.Downloader.dictSubDir
import downloader.Downloader.getModelPath
import downloader.Downloader.getPathToBeamModule
import downloader.Downloader.modelSubDir
import org.tensorflow.SavedModelBundle
import org.tensorflow.Session
import org.tensorflow.Tensor
import helpers.TensorConverter.parsePredictions
import helpers.TensorConverter.parseScores
import inspections.Suggestion
import net.razorvine.pickle.Unpickler
import utils.PathUtils.getCombinedPaths
import utils.PsiUtils
import java.io.FileInputStream
import java.util.*
import kotlin.collections.ArrayList

class ModelFacade {

    companion object {
        private val log: Logger = Logger.getInstance(ModelFacade::class.java)
        private val beamSearchModule = org.tensorflow.TensorFlow.loadLibrary(getPathToBeamModule());
        private val tfModel: SavedModelBundle = SavedModelBundle.load(getModelPath().toString() + modelSubDir, "serve")
    }

    fun getSuggestions(method: PsiMethod): Suggestion {
        val methodBody = PsiUtils.getMethodBody(method)
        return Suggestion(generatePredictions(methodBody))
    }

    fun getSuggestions(methodBody: String): Suggestion {
        return Suggestion(generatePredictions(methodBody))
    }

    private fun parseResults(listOfIndexes: List<List<Any>>): List<String> {
        var predictions = ArrayList<String>()
        val stream = FileInputStream(getModelPath().toString() + dictSubDir)
        val unpickler = Unpickler()
        val dictionary = unpickler.load(stream)
        val mapOfSubtokens = dictionary as HashMap<Int, String>
        for (indexes in listOfIndexes) {
            var name = mapOfSubtokens.get(indexes[0]) ?: ""
            for (i in indexes.subList(1, indexes.size)) {
                var subtoken: String? = mapOfSubtokens.get(i)
                if (subtoken != null && !subtoken.equals("<PAD>") && !subtoken.equals("<UNK>"))
                    name += subtoken.substring(0, 1).toUpperCase() + subtoken.substring(1)
            }
            predictions.add(name)
        }
        return predictions
    }

    private fun generatePredictions(methodBody: String): ArrayList<Pair<String, Double>> {
        val resultPairs: ArrayList<Pair<String, Double>> = ArrayList()
        try {
            val paths = getCombinedPaths(methodBody)
            if (paths.isEmpty()) return arrayListOf()
            val session: Session = tfModel.session()
            val runnerForNames = session.runner()
            val runnerForScores = session.runner()
            val inputTensor = Tensor.create(paths.toByteArray(Charsets.UTF_8), String::class.java)

            val outputTensorForNames: Tensor<*> = runnerForNames.feed("Placeholder:0", inputTensor).fetch("model/decoder/transpose:0").run()[0]
            val predictions: List<List<Any>> = parsePredictions(outputTensorForNames) as List<List<Any>>
            val parsedPredictions: List<String> = parseResults(predictions)

            val outputTensorScores: Tensor<*> = runnerForScores.feed("Placeholder:0", inputTensor).fetch("model/decoder/transpose_1:0").run()[0]
            val scores: List<Double> = parseScores(outputTensorScores) as List<Double>

            for (i in 0 until parsedPredictions.size) {
                val currentPrediction: String = parsedPredictions[i]
                if (currentPrediction.isNotEmpty() && !currentPrediction.equals(currentPrediction.toLowerCase())
                        && !currentPrediction.equals("<UNK>") && !currentPrediction.equals("<PAD>")) {
                    resultPairs.add(Pair(currentPrediction, scores[i]))
                }
            }
            outputTensorForNames.close()
            outputTensorScores.close()
        } catch (e: Exception) {
            log.info("Error was occurred while handling result tensor.")
        }

        return resultPairs
    }
}