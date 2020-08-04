package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

public interface ModelService {
    static ModelService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, ModelService.class);
    }

    IdNamesSuggestingModelRunner getIdNamesSuggestingModelRunner(String name);

    void learnProject(@NotNull Project file, @NotNull ProgressIndicator progressIndicator);

    void predictVariableName(@NotNull PsiVariable element, @NotNull LinkedHashSet<String> nameSuggestions);
}
