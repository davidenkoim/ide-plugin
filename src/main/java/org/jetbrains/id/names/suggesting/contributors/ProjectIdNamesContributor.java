package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesContributor;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.ModelService;

import java.util.LinkedHashSet;

public class ProjectIdNamesContributor implements IdNamesContributor {
    @Override
    public LinkedHashSet<String> contribute(@NotNull PsiVariable element) {
        IdNamesSuggestingModelRunner modelRunner = ModelService.getInstance(element.getProject())
                .getIdNamesSuggestingModelRunner("org.jetbrains.id.names.suggesting.contributors.ProjectIdNamesContributor");
        if (modelRunner == null) return new LinkedHashSet<>();
        return modelRunner.predictVariableName(element);
    }
}
