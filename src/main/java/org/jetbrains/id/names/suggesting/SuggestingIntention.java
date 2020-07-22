package org.jetbrains.id.names.suggesting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class SuggestingIntention implements IntentionAction {
    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
        return IdNamesSuggestingBundle.message("intention.text");
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getFamilyName() {
        return IdNamesSuggestingBundle.message("intention.text");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {

    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
