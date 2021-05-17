package org.jetbrains.id.names.suggesting.api;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import org.jetbrains.id.names.suggesting.VarNamePrediction;

import java.util.List;

public interface IdNamesSuggestingModelRunner {
    /**
     * Makes predictions for the last token from a set of N-gram sequences.
     *
     * @param identifierClass class of identifier (to check if we support suggesting for it).
     * @param usageNGrams     n-grams from which model should get suggestions.
     * @return List of predictions.
     */
    List<VarNamePrediction> suggestNames(Class<? extends PsiNameIdentifierOwner> identifierClass, List<List<String>> usageNGrams, boolean forgetUsages);

    /**
     * Predict probability of last token in a series of n-grams.
     *
     * @param usageNGrams  : n-grams from which model should get probability of the last token.
     * @param forgetUsages :
     * @return probability, modelPriority
     */
    Pair<Double, Integer> getProbability(List<List<String>> usageNGrams, boolean forgetUsages);

    void learnPsiFile(PsiFile file);

    void forgetPsiFile(PsiFile file);
}
