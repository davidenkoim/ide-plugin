package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;

public interface IdNamesContributor {
    ExtensionPointName<IdNamesContributor> EP_NAME =
            ExtensionPointName.create("org.jetbrains.id.names.suggesting.idNamesContributor");
    int GLOBAL_PREDICTION_CUTOFF = 10;

    /**
     * Contribute some variable names.
     * @param element: element which name we want to predict.
     * @return : set of names for variable.
     */
    LinkedHashSet<String> contribute(@NotNull PsiVariable element);
}
