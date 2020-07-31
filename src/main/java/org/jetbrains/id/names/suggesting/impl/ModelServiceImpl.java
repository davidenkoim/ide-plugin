package org.jetbrains.id.names.suggesting.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.ModelService;
import org.jetbrains.id.names.suggesting.NGramModelContributor;

public class ModelServiceImpl implements ModelService {
    boolean isLearnt = false;
    NGramModelContributor modelContributor;

    public ModelServiceImpl(Project project) {
        this.modelContributor = new NGramModelContributor();
    }

    @Override
    public boolean isLearntProject() {
        return isLearnt;
    }

    @Override
    public void learnFile(@NotNull PsiFile file, @NotNull ProgressIndicator progressIndicator) {
        this.modelContributor.learnPsiFile(file);
        isLearnt = true;
    }
}
