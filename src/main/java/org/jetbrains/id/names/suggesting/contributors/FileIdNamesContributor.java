package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesContributor;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.impl.IdNamesSuggestingNGramModelRunner;

import java.util.LinkedHashSet;

public class FileIdNamesContributor implements IdNamesContributor {
    @Override
    public LinkedHashSet<String> contribute(@NotNull PsiVariable element) {
        IdNamesSuggestingModelRunner modelRunner = new IdNamesSuggestingNGramModelRunner();
        modelRunner.learnPsiFile(element.getContainingFile());
        modelRunner.forgetVariableUsages(element);
        return modelRunner.predictVariableName(element);
    }
}
