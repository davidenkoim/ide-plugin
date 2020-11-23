package org.jetbrains.id.names.suggesting.api;

import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiVariable;
import kotlin.Pair;
import org.jetbrains.id.names.suggesting.Prediction;

import java.util.List;

public interface IdNamesSuggestingModelRunner {
    /**
     * Makes identifier name suggestions.
     *
     * @param usageNGrams : n-grams from which model should get suggestions.
     * @return Names for identifier
     */
    List<Prediction> suggestNames(Class<? extends PsiNameIdentifierOwner> identifierClass, List<List<String>> usageNGrams);

    /**
     * Predict probability of last token in a series of n-grams.
     *
     * @param usageNGrams : n-grams from which model should get probability of the last token.
     * @return probability, modelPriority
     */
    Pair<Double, Integer> getProbability(Class<? extends PsiNameIdentifierOwner> identifierClass, List<List<String>> usageNGrams);
}
