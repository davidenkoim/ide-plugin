package org.jetbrains.id.names.suggesting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.PsiElementEditorPredicate;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuggestingIntention implements IntentionAction {
    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getText() {
        return IdNamesSuggestingBundle.message("intention.text");
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getFamilyName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return findMatchingElement(file, editor) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final PsiElement element = findMatchingElement(file, editor);
        if (element == null) {
            return;
        }
        assert element.isValid() : element;
        processIntention(element, project, editor);
    }

    private void processIntention(PsiElement element, Project project, Editor editor) {
    }

    protected boolean isStopElement(PsiElement element) {
        return element instanceof PsiFile;
    }

    private final PsiElementPredicate predicate = new PsiElementPredicate() {
        @Override
        public boolean satisfiedBy(PsiElement element) {
            return element instanceof PsiVariable;
        }
    };

    @Nullable
    PsiElement findMatchingElement(PsiFile file, Editor editor) {
        if (!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) {
            return null;
        }

        final int position = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(position);
        while (element != null) {
            if (predicate.satisfiedBy(element)) return element;
            if (isStopElement(element)) break;
            element = element.getParent();
        }

        element = file.findElementAt(position - 1);
        while (element != null) {
            if (predicate.satisfiedBy(element)) return element;
            if (isStopElement(element)) return null;
            element = element.getParent();
        }

        return null;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
