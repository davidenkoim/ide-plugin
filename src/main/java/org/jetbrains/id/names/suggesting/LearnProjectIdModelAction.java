package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class LearnProjectIdModelAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = Objects.requireNonNull(e.getProject());
        NGramService service = NGramService.getInstance(project);
        service.learnProject(project);
    }
}
