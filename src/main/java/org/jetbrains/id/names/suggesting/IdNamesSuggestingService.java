package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;

import java.util.LinkedHashSet;

public class IdNamesSuggestingService {
    public static IdNamesSuggestingService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, IdNamesSuggestingService.class);
    }

    public LinkedHashSet<String> suggestVariableName(@NotNull PsiVariable variable) {
        LinkedHashSet<String> nameSuggestions = new LinkedHashSet<>();
        for (final VariableNamesContributor modelContributor : VariableNamesContributor.EP_NAME.getExtensions()) {
            nameSuggestions.add(modelContributor.getClass().getSimpleName());
            modelContributor.contribute(variable, nameSuggestions);
            //TODO: solve problem of ranking suggestions from different contributors.
        }
        return nameSuggestions;
    }
}
