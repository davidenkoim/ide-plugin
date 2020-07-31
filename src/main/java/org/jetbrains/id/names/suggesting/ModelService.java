package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

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
     * Initialize training process on file|project.
     *  @param file : current file|project
     * @param progressIndicator :
     */
    void learnFile(@NotNull PsiFile file, @NotNull ProgressIndicator progressIndicator);

    public LinkedHashSet<String> predictVariableName(@NotNull PsiElement element);
}
