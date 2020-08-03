package org.jetbrains.id.names.suggesting;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.impl.NGramModelContributorImpl;

import java.util.LinkedHashSet;

public interface NGramModelContributor {
    static NGramModelContributor createInstance() {
        return new NGramModelContributorImpl();
    }

    /**
     * Train on a file.
     * @param file:
     */
    void learnPsiFile(@NotNull PsiFile file);

    /**
     * Contribute some variable names.
     * @param element: element which name we want to predict.
     * @return : set of names for variable.
     */
    LinkedHashSet<String> contribute(@NotNull PsiElement element);
}
