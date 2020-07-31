package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class LearnProjectModelAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = Objects.requireNonNull(e.getProject());
        ModelService service = ModelService.getInstance(project);
        PsiFile file = Objects.requireNonNull(e.getData(LangDataKeys.PSI_FILE));
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Building project id suggesting models") {
            @Override
            public void run(@NotNull ProgressIndicator progressIndicator) {

                progressIndicator.setText("Preparing models for " + project.getName());
                ReadAction.nonBlocking(() -> {
                    service.learnFile(file, progressIndicator);
                    // later file will be changed to project as VirtualFile
                })
                        .inSmartMode(project)
                        .executeSynchronously();

            }
        });
    }
}
