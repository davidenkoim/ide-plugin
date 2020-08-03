package org.jetbrains.id.names.suggesting;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.impl.NameModelContributorImpl;

import java.util.LinkedHashSet;

public interface NameModelContributor {
    static NameModelContributor createInstance() {
        return new NameModelContributorImpl();
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
    LinkedHashSet<String> contribute(@NotNull PsiVariable element);

    /**
     * Forget element usages.
     * @param element:
     */
    void forgetVariableUsages(@NotNull PsiVariable element);
}
