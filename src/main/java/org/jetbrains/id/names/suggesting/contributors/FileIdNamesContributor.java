package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.IdNamesContributor;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.impl.IdNamesSuggestingNGramModelRunner;

import java.util.LinkedHashSet;

public class FileIdNamesContributor implements IdNamesContributor {
    @Override
    public void contribute(@NotNull PsiVariable variable, @NotNull LinkedHashSet<String> resultSet) {
        IdNamesSuggestingModelRunner modelRunner = new IdNamesSuggestingNGramModelRunner(false);
        modelRunner.learnPsiFile(variable.getContainingFile());
        modelRunner.forgetVariableUsages(variable);
        resultSet.addAll(modelRunner.predictVariableName(variable));
    }
}
