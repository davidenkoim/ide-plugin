package org.jetbrains.id.names.suggesting.api;

import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.id.names.suggesting.Prediction;

import java.util.List;

public interface IdNamesSuggestingModelRunner {
    /**
     * Makes identifier name suggestions.
     *
     * @param identifierOwner : element to rename
     * @return Names for identifier
     */
    List<Prediction> suggestNames(Class<? extends PsiNameIdentifierOwner> identifierClass, List<List<String>> identifierOwner);
}
