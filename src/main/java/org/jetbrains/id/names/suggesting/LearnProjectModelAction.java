package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class LearnProjectModelAction extends LearnModelActionBase {
    @Override
    protected void doActionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        assert project != null;
        ModelService service = ModelService.getInstance(project);
        PsiFile file = e.getData(LangDataKeys.PSI_FILE);
        assert file != null;
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Building project id suggesting models") {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {

                progressIndicator.setText("Preparing models for " + project.getName());
                ReadAction.nonBlocking(() -> {
                    service.learnProject(file, progressIndicator);
                    // later file will be changed to project as VirtualFile
                })
                        .inSmartMode(project)
                        .executeSynchronously();

            }
        });
    }

    @Override
    protected boolean canBePerformed(@NotNull AnActionEvent e) {
        return (e.getProject() != null && e.getData(LangDataKeys.PSI_FILE) != null);
    }
}
