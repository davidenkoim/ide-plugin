package org.jetbrains.id.names.suggesting.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.IdNamesContributor;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingService;
import com.intellij.openapi.project.Project;
import org.jetbrains.id.names.suggesting.contributors.ProjectIdNamesContributor;

import java.util.LinkedHashSet;

public class IdNamesSuggestingServiceImpl implements IdNamesSuggestingService {
    public IdNamesSuggestingServiceImpl(Project project) {
    }

    @Override
    public void learnProject(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
        IdNamesSuggestingModelRunner modelRunner = new IdNamesSuggestingNGramModelRunner(true);
        modelRunner.learnProject(project, progressIndicator);
        IdNamesSuggestingModelManager.getInstance(project).putModelRunner(ProjectIdNamesContributor.class.getName(),
                modelRunner);
    }

    @Override
    public LinkedHashSet<String> predictVariableName(@NotNull PsiVariable variable) {
        LinkedHashSet<String> nameSuggestions = new LinkedHashSet<>();
        for (final IdNamesContributor modelContributor : IdNamesContributor.EP_NAME.getExtensions()) {
            nameSuggestions.add(modelContributor.getClass().getSimpleName());
            modelContributor.contribute(variable, nameSuggestions);
            //TODO: solve problem of ranking suggestions from different contributors.
        }
        return nameSuggestions;
    }
}
