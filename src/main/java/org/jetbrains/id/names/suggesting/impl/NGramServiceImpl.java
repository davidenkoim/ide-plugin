package org.jetbrains.id.names.suggesting.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.NGramService;
import com.intellij.openapi.project.Project;

public class NGramServiceImpl implements NGramService {
    boolean isLearnt = false;

    public NGramServiceImpl(Project project) {
    }

    @Override
    public boolean isLearntProject() {
        return isLearnt;
    }

    @Override
    public void learnProject(@NotNull Project project) {
        isLearnt = true;
    }
}
