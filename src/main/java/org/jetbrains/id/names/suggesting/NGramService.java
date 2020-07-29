package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface NGramService {
    /**
     * Tells if the project is already learnt.
     */
    boolean isLearntProject();

    static NGramService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, NGramService.class);
    }

    /**
     * Method which initialize learning process.
     *
     * @param project: current project
     */
    void learnProject(@NotNull Project project);
}
