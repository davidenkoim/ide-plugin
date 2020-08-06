package org.jetbrains.id.names.suggesting.api;

import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

public interface IdNamesSuggestingModelRunner {
    /**
     * Makes identifier name suggestions.
     *
     * @param identifierOwner: element to rename
     * @return Names for identifier
     */
    LinkedHashSet<String> suggestNames(@NotNull PsiNameIdentifierOwner identifierOwner);
}
