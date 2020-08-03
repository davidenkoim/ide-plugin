package org.jetbrains.id.names.suggesting.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.ModelService;
import org.jetbrains.id.names.suggesting.NameModelContributor;

import java.util.LinkedHashSet;

public class ModelServiceImpl implements ModelService {
    boolean isLearnt = false;
    NameModelContributor modelContributor;

    public ModelServiceImpl(Project project) {
    }

    @Override
    public boolean isLearntProject() {
        return isLearnt;
    }

    @Override
    public void learnProject(@NotNull PsiFile file, @Nullable ProgressIndicator progressIndicator) {
        // Not implemented yet.
    }

    @Override
    public void learnFile(@NotNull PsiFile file) {
        modelContributor = NameModelContributor.createInstance();
        modelContributor.learnPsiFile(file);
        isLearnt = true;
    }

    @Override
    public void forgetVariableUsages(@NotNull PsiVariable elementToRename) {
        modelContributor.forgetVariableUsages(elementToRename);
    }

    @Override
    public LinkedHashSet<String> predictVariableName(@NotNull PsiVariable element) {
        return modelContributor.contribute(element);
    }
}
