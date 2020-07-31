package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface ModelService {
    /**
     * Tells if the model is already trained.
     */
    boolean isLearntProject();

    static ModelService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, ModelService.class);
    }

    /**
     * Initialize training process on file|project.
     *  @param file : current file|project
     * @param progressIndicator :
     */
    void learnFile(@NotNull PsiFile file, @NotNull ProgressIndicator progressIndicator);
}
