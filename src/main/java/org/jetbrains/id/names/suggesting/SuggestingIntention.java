package org.jetbrains.id.names.suggesting;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;

public class SuggestingIntention implements IntentionAction {
    @Override
    public @NotNull String getText() {
        return IdNamesSuggestingBundle.message("intention.text");
    }

    @Override
    public @NotNull String getFamilyName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return NGramService.getInstance(project).isLearntProject() &&
                findMatchingElement(file, editor) != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        final PsiElement element = findMatchingElement(file, editor);
        assert element != null;
        assert element.isValid() : "Invalid element:" + element;
        processIntention(element, project, editor);
    }

    private void processIntention(@NotNull PsiElement elementToRename, Project project, @NotNull Editor editor) {
        InplaceRefactoring inplaceRefactoring = new VariableInplaceRenamer((PsiNamedElement) elementToRename, editor);
        LinkedHashSet<String> nameSuggestions = new LinkedHashSet<>();
        nameSuggestions.add("Plugin");
        inplaceRefactoring.performInplaceRefactoring(nameSuggestions);
    }

    private @Nullable PsiElement findMatchingElement(@NotNull PsiFile file, @NotNull Editor editor) {
        if (!file.getViewProvider().getLanguages().contains(JavaLanguage.INSTANCE)) {
            return null;
        }

        final int offset = editor.getCaretModel().getOffset();
        final PsiElement declaration = getDeclaration(file.findElementAt(offset));
        if (declaration != null) {
            return declaration;
        }
        return getDeclaration(file.findElementAt(offset - 1));
    }

    private @Nullable PsiElement getDeclaration(@Nullable PsiElement element) {
        if (element instanceof PsiIdentifier) {
            element = element.getParent();
            if (element instanceof PsiVariable) {
                return element;
            }
            if (element instanceof PsiReferenceExpression) {
                element = ((PsiReferenceExpression) element).resolve();
                if (element instanceof PsiVariable) {
                    return element;
                }
            }
        }
        return null;
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
