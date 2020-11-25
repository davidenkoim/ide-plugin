package org.jetbrains.id.names.suggesting.api;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiVariable;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.Prediction;

import java.util.List;

public interface VariableNamesContributor {
    ExtensionPointName<VariableNamesContributor> EP_NAME =
            ExtensionPointName.create("org.jetbrains.id.names.suggesting.variableNamesContributor");

    /**
     * Contribute some variable names.
     *
     * @param variable        : variable which name we want to predict.
     * @param predictionList:
     * @return priority of contribution
     */
    int contribute(@NotNull PsiVariable variable, @NotNull List<Prediction> predictionList);

    Pair<Double, Integer> getProbability(PsiVariable variable);
}
