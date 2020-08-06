package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;

import java.util.LinkedHashSet;

public class ProjectVariableNamesContributor implements VariableNamesContributor {
    @Override
    public void contribute(@NotNull PsiVariable variable, @NotNull LinkedHashSet<String> resultSet) {
        IdNamesSuggestingModelRunner modelRunner = IdNamesSuggestingModelManager.getInstance(variable.getProject())
                                                                                .getModelRunner(this.getClass().getName());
        if (modelRunner == null) {
            return;
        }
        resultSet.addAll(modelRunner.suggestNames(variable));
    }
}
