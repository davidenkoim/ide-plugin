package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.IdNamesContributor;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.api.ModelManager;

import java.util.LinkedHashSet;

public class ProjectIdNamesContributor implements IdNamesContributor {
    @Override
    public void contribute(@NotNull PsiVariable variable, @NotNull LinkedHashSet<String> resultSet) {
        IdNamesSuggestingModelRunner modelRunner = ModelManager.getInstance(variable.getProject())
                .getModelRunner(this.getClass().getName());
        if (modelRunner == null) return;
        modelRunner.forgetVariableUsages(variable);
        resultSet.addAll(modelRunner.predictVariableName(variable));
        modelRunner.learnVariableUsages(variable);
    }
}
