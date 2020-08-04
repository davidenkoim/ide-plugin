package org.jetbrains.id.names.suggesting.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesContributor;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.ModelService;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public class ModelServiceImpl implements ModelService {
    private final Map<String, IdNamesSuggestingModelRunner> myIdNamesSuggestingModelRunners = new HashMap<>();
    public ModelServiceImpl(Project project) {
    }

    public IdNamesSuggestingModelRunner getIdNamesSuggestingModelRunner(String name){
        return myIdNamesSuggestingModelRunners.getOrDefault(name, null);
    }

    @Override
    public void learnProject(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
        IdNamesSuggestingModelRunner modelRunner = new IdNamesSuggestingNGramModelRunner();
        modelRunner.learnProject(project, progressIndicator);
        myIdNamesSuggestingModelRunners.put("org.jetbrains.id.names.suggesting.contributors.ProjectIdNamesContributor",
                modelRunner);
    }

    @Override
    public void predictVariableName(@NotNull PsiVariable element, @NotNull LinkedHashSet<String> nameSuggestions) {
        for (final IdNamesContributor modelContributor : IdNamesContributor.EP_NAME.getExtensions()) {
            nameSuggestions.addAll(modelContributor.contribute(element));
            //TODO: solve problem of ranking suggestions from different contributors.
        }
    }
}
