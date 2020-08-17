package org.jetbrains.id.names.suggesting;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class IdNamesSuggestingService {
    public static final int PREDICTION_CUTOFF = 10;
    public static IdNamesSuggestingService getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, IdNamesSuggestingService.class);
    }

    public LinkedHashMap<String, Double> suggestVariableName(@NotNull PsiVariable variable) {
        Instant timerStart = Instant.now();
        List<Prediction> nameSuggestions = new ArrayList<>();
        StringBuilder notifications = new StringBuilder();
        int prioritiesSum = 0;
        for (final VariableNamesContributor modelContributor : VariableNamesContributor.EP_NAME.getExtensions()) {
            Instant start = Instant.now();
            modelContributor.contribute(variable, nameSuggestions);
            prioritiesSum += nameSuggestions.get(nameSuggestions.size() - 1).getPriority();
            Instant end = Instant.now();
            notifications.append(String.format("%s : %.3fms.\n",
                    modelContributor.getClass().getSimpleName(),
                    Duration.between(start, end).toNanos() / 1_000_000.));
        }
        LinkedHashMap<String, Double> result = rankSuggestions(nameSuggestions, prioritiesSum);
        Instant timerEnd = Instant.now();
        notifications.append(String.format("Total time: %.3fms.\n",
                Duration.between(timerStart, timerEnd).toNanos() / 1_000_000.));
        Notifications.Bus.notify(
                new Notification(IdNamesSuggestingBundle.message("name"),
                        "Time of contribution:",
                        notifications.toString(),
                        NotificationType.INFORMATION),
                variable.getProject());
        return result;
    }

    private LinkedHashMap<String, Double> rankSuggestions(List<Prediction> nameSuggestions, int prioritiesSum) {
        Map<String, Double> rankedSuggestions = new HashMap<>();
        for (Prediction prediction : nameSuggestions) {
            Double prob = rankedSuggestions.get(prediction.getName());
            double addition = prediction.getProbability() * prediction.getPriority() / prioritiesSum;
            if (prob == null) {
                rankedSuggestions.put(prediction.getName(), addition);
            } else {
                rankedSuggestions.put(prediction.getName(), prob + addition);
            }
        }
        return rankedSuggestions.entrySet()
                .stream()
                .sorted((e1, e2) -> -Double.compare(e1.getValue(), e2.getValue()))
                .limit(PREDICTION_CUTOFF)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (v1, v2) -> {
                            throw new IllegalStateException();
                        },
                        LinkedHashMap::new));
    }
}
