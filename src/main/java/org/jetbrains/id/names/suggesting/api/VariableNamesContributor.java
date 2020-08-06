package org.jetbrains.id.names.suggesting.api;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

public interface VariableNamesContributor {
    ExtensionPointName<VariableNamesContributor> EP_NAME =
            ExtensionPointName.create("org.jetbrains.id.names.suggesting.variableNamesContributor");

    /**
     * Contribute some variable names.
     *
     * @param variable  : variable which name we want to predict.
     * @param resultSet : set of strings in which contributor add name suggestions.
     */
    void contribute(@NotNull PsiVariable variable, @NotNull LinkedHashSet<String> resultSet);
}
