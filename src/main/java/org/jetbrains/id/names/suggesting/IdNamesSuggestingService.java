package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class IdNamesSuggestingService {
    public static IdNamesSuggestingService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, IdNamesSuggestingService.class);
    }

    public LinkedHashMap<String, Double> suggestVariableName(@NotNull PsiVariable variable) {
        List<Prediction> nameSuggestions = new ArrayList<>();
        for (final VariableNamesContributor modelContributor : VariableNamesContributor.EP_NAME.getExtensions()) {
            nameSuggestions.add(new Prediction(modelContributor.getClass().getSimpleName(), 0.));
            modelContributor.contribute(variable, nameSuggestions);
            //TODO: solve problem of ranking suggestions from different contributors.
        }
        return rankSuggestions(nameSuggestions);
    }

    private LinkedHashMap<String, Double> rankSuggestions(List<Prediction> nameSuggestions) {
        LinkedHashMap<String, Double> rankedSuggestions = new LinkedHashMap<>();
        for (Prediction prediction: nameSuggestions){
            rankedSuggestions.put(prediction.getName(), prediction.getProbability());
        }
        return rankedSuggestions;
    }
}
