package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;

import java.util.LinkedHashSet;

public class FileVariableNamesContributor implements VariableNamesContributor {
    @Override
    public void contribute(@NotNull PsiVariable variable, @NotNull LinkedHashSet<String> resultSet) {
        IdNamesNGramModelRunner modelRunner = new IdNamesNGramModelRunner(false);
        modelRunner.learnPsiFile(variable.getContainingFile());
        resultSet.addAll(modelRunner.suggestNames(variable));
    }
}
