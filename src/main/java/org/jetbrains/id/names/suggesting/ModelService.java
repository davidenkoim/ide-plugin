package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

public interface ModelService {
    static ModelService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, ModelService.class);
    }

    /**
     * Tells if the model is already trained.
     */
    boolean isLearntProject();

    /**
     * Initialize training process on file.
     *
     * @param file              : current file|project
     */
    void learnFile(@NotNull PsiFile file);

    void learnProject(@NotNull PsiFile file, @Nullable ProgressIndicator progressIndicator);

    LinkedHashSet<String> predictVariableName(@NotNull PsiVariable element);

    void forgetVariableUsages(@NotNull PsiVariable elementToRename);
}
