package org.jetbrains.id.names.suggesting.api;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

public interface IdNamesSuggestingService {
    static IdNamesSuggestingService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, IdNamesSuggestingService.class);
    }

    void learnProject(@NotNull Project file, @NotNull ProgressIndicator progressIndicator);

    LinkedHashSet<String> predictVariableName(@NotNull PsiVariable variable);
}
