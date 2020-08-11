package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.Prediction;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;

import java.util.List;

public class ProjectVariableNamesContributor implements VariableNamesContributor {
    @Override
    public void contribute(@NotNull PsiVariable variable, @NotNull List<Prediction> predictionList) {
        IdNamesSuggestingModelRunner modelRunner = IdNamesSuggestingModelManager.getInstance(variable.getProject())
                                                                                .getModelRunner(this.getClass().getName());
        if (modelRunner == null) {
            return;
        }
        predictionList.addAll(modelRunner.suggestNames(variable));
    }
}
