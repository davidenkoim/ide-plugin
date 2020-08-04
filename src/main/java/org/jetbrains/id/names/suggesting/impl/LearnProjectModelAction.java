package org.jetbrains.id.names.suggesting.impl;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.LearnModelActionBase;
import org.jetbrains.id.names.suggesting.ModelService;

public class LearnProjectModelAction extends LearnModelActionBase {
    @Override
    protected void doActionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;
        ModelService service = ModelService.getInstance(project);
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Building project id suggesting models") {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {

                progressIndicator.setText("Preparing models for " + project.getName());
                ReadAction.nonBlocking(() -> {
                    service.learnProject(project, progressIndicator);
                })
                        .inSmartMode(project)
                        .executeSynchronously();

            }
        });
    }

    @Override
    protected boolean canBePerformed(@NotNull AnActionEvent e) {
        return e.getProject() != null;
    }
}
