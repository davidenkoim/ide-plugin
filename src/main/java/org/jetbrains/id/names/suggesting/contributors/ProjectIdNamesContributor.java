package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.IdNamesContributor;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;

import java.util.LinkedHashSet;

public class ProjectIdNamesContributor implements IdNamesContributor {
    @Override
    public void contribute(@NotNull PsiVariable variable, @NotNull LinkedHashSet<String> resultSet) {
        IdNamesSuggestingModelRunner modelRunner = IdNamesSuggestingModelManager.getInstance(variable.getProject())
                .getModelRunner(this.getClass().getName());
        if (modelRunner == null) return;
        modelRunner.forgetVariableUsages(variable);
        resultSet.addAll(modelRunner.predictVariableName(variable));
        modelRunner.learnVariableUsages(variable);
    }
}
