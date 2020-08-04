package org.jetbrains.id.names.suggesting;

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
     * Makes name suggestions for element.
     *
     * @param element:
     * @return LinkedHashSet of names for element.
     */
    LinkedHashSet<String> predictVariableName(@NotNull PsiVariable element);

    /**
     * Forget element usages.
     *
     * @param element:
     */
    void forgetVariableUsages(@NotNull PsiVariable element);
}
