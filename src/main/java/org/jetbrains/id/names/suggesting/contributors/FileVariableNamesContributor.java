package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.Prediction;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;

import java.util.List;

public class FileVariableNamesContributor implements VariableNamesContributor {
    @Override
    public void contribute(@NotNull PsiVariable variable, @NotNull List<Prediction> predictionList) {
        IdNamesNGramModelRunner modelRunner = new IdNamesNGramModelRunner(false);
        modelRunner.learnPsiFile(variable.getContainingFile());
        predictionList.addAll(modelRunner.suggestNames(variable));
    }
}
