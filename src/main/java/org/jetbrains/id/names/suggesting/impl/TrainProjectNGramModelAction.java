package org.jetbrains.id.names.suggesting.impl;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingBundle;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.api.AbstractTrainModelAction;

import java.time.Duration;
import java.time.Instant;

public class TrainProjectNGramModelAction extends AbstractTrainModelAction {
    @Override
    protected void doActionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;
        IdNamesSuggestingModelManager modelManager = IdNamesSuggestingModelManager.getInstance(project);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("training.task.title")) {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {
                progressIndicator.setText(IdNamesSuggestingBundle.message("training.progress.indicator.text", project.getName()));
                ReadAction.nonBlocking(() -> {
                    Instant start = Instant.now();
                    modelManager.trainProjectNGramModel(project, progressIndicator);
                    Instant end = Instant.now();
                    Notifications.Bus.notify(
                            new Notification(IdNamesSuggestingBundle.message("name"),
                                    "Training of project model is completed.",
                                    String.format("Time of training on %s: %dms.",
                                            project.getName(),
                                            Duration.between(start, end).toMillis()),
                                    NotificationType.INFORMATION),
                            project);
                })
                        .inSmartMode(project)
                        .executeSynchronously();
            }
        });
    }

    @Override
    protected boolean canBePerformed(@NotNull AnActionEvent e) {
        return e.getProject() != null &&
                FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(e.getProject()));
    }
}
