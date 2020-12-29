package org.jetbrains.id.names.suggesting.dataset;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.AnAction;
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
import org.jetbrains.id.names.suggesting.NotificationsUtil;

import java.time.Duration;
import java.time.Instant;

public class DatasetBuildAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getProject() != null &&
                FileTypeIndex.containsFileOfType(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(e.getProject()))) {
            Project project = e.getProject();
            ProgressManager.getInstance().run(new Task.Backgroundable(project, IdNamesSuggestingBundle.message("building.dataset.title")) {
                @Override
                public void run(@NotNull ProgressIndicator progressIndicator) {
                    progressIndicator.setText(IdNamesSuggestingBundle.message("building.dataset.for.project", project.getName()));
                    ReadAction.nonBlocking(() -> {
                        Instant start = Instant.now();
                        DatasetManager.build(project, progressIndicator);
                        Instant end = Instant.now();
                        NotificationsUtil.notify(project,
                                "Building dataset is completed.",
                                String.format("Time of building on %s: %dms.",
                                        project.getName(),
                                        Duration.between(start, end).toMillis()));
                    })
                            .inSmartMode(project)
                            .executeSynchronously();
                }
            });
        }
    }
}
