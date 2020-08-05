package org.jetbrains.id.names.suggesting.api;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

public interface IdNamesSuggestingModelRunner {
    /**
     * Train model on a file.
     *
     * @param file:
     */
    void learnPsiFile(@NotNull PsiFile file);

    /**
     * Train model on a project.
     *
     * @param project :
     * @param progressIndicator:
     */
    void learnProject(@NotNull Project project, @NotNull ProgressIndicator progressIndicator);

    /**
     * Makes name suggestions for variable.
     *
     * @param variable:
     * @return LinkedHashSet of names for variable.
     */
    LinkedHashSet<String> predictVariableName(@NotNull PsiVariable variable);

    /**
     * Forget variable usages.
     *
     * @param variable:
     */
    void forgetVariableUsages(@NotNull PsiVariable variable);

    /**
     * Learn variable usages.
     *
     * @param variable:
     */
    void learnVariableUsages(@NotNull PsiVariable variable);
}
