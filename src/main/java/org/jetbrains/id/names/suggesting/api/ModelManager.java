package org.jetbrains.id.names.suggesting.api;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface ModelManager {
    static ModelManager getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, ModelManager.class);
    }

    IdNamesSuggestingModelRunner getModelRunner(String name);

    void putModelRunner(String name, IdNamesSuggestingModelRunner modelRunner);
}
