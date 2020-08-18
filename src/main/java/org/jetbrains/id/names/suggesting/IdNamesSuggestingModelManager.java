package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor;
import org.jetbrains.id.names.suggesting.contributors.ProjectVariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;

import java.util.HashMap;
import java.util.Map;

public class IdNamesSuggestingModelManager {
    private final Map<String, IdNamesSuggestingModelRunner> myModelRunners = new HashMap<>();

    public IdNamesSuggestingModelManager() {
        IdNamesNGramModelRunner modelRunner = new IdNamesNGramModelRunner(true);
        putModelRunner(GlobalVariableNamesContributor.class.getName(), modelRunner);
    }

    public static IdNamesSuggestingModelManager getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, IdNamesSuggestingModelManager.class);
    }

    public IdNamesSuggestingModelRunner getModelRunner(String name) {
        return myModelRunners.get(name);
    }

    public void putModelRunner(String name, IdNamesSuggestingModelRunner modelRunner) {
        myModelRunners.put(name, modelRunner);
    }

    public void trainProjectNGramModel(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
        IdNamesNGramModelRunner modelRunner = new IdNamesNGramModelRunner(true);
        modelRunner.learnProject(project, progressIndicator);
        putModelRunner(ProjectVariableNamesContributor.class.getName(), modelRunner);
    }

    public double trainGlobalNGramModel(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
        IdNamesNGramModelRunner modelRunner = (IdNamesNGramModelRunner) IdNamesSuggestingModelManager.getInstance(project)
                .getModelRunner(GlobalVariableNamesContributor.class.getName());
        modelRunner.learnProject(project, progressIndicator);
        return modelRunner.save(progressIndicator);
    }
}
