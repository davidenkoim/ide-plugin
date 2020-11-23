package org.jetbrains.id.names.suggesting.contributors;

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ObjectUtils;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.Prediction;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;

public abstract class NGramVariableNamesContributor implements VariableNamesContributor {
    public static final List<Class<? extends PsiNameIdentifierOwner>> SUPPORTED_TYPES = new ArrayList<>();

    static {
        SUPPORTED_TYPES.add(PsiVariable.class);
    }

    private int modelOrder;

    @Override
    public void contribute(@NotNull PsiVariable variable, @NotNull List<Prediction> predictionList) {
        IdNamesNGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return;
        }
        modelOrder = modelRunner.getOrder();
        predictionList.addAll(modelRunner.suggestNames(variable.getClass(), findUsageNGrams(variable)));
    }

    @Override
    public Pair<Double, Integer> getProbability(PsiVariable variable) {
        IdNamesNGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return new Pair<>(0.0, 0);
        }
        modelOrder = modelRunner.getOrder();
        return modelRunner.getProbability(variable.getClass(), findUsageNGrams(variable));
    }

    public abstract @Nullable IdNamesNGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable);

    private static boolean isSupported(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return SUPPORTED_TYPES.stream().anyMatch(type -> type.isInstance(identifierOwner));
    }

    private List<List<String>> findUsageNGrams(PsiNameIdentifierOwner identifierOwner) {
        Stream<PsiReference> elementUsages = findReferences(identifierOwner);
        return Stream.concat(Stream.of(identifierOwner), elementUsages)
                .map(NGramVariableNamesContributor::getIdentifier)
                .filter(Objects::nonNull)
                .map(this::getNGram)
                .collect(Collectors.toList());
    }

    public static Stream<PsiReference> findReferences(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return ReferencesSearch.search(identifierOwner)
                .findAll()
                .stream();
    }

    public static Boolean caretInsideVariable(@NotNull PsiVariable variable) {
//        TODO: Очень большой костыль. Если убрать nonBlocking, то intellij ругается, не может читать спокойно.
        AtomicReference<Editor> editor = new AtomicReference<>(null);
        ReadAction.nonBlocking(() -> {
            editor.set(FileEditorManager.getInstance(variable.getProject()).getSelectedTextEditor());
        });
        if (editor.get() != null) {
            int caret = editor.get().getCaretModel().getOffset();
            return findReferences(variable).anyMatch(reference ->
                    (caret >= reference.getRangeInElement().getStartOffset()) &&
                            (caret <= reference.getRangeInElement().getEndOffset()));
        } else {
            return false;
        }
    }

    private static @Nullable PsiIdentifier getIdentifier(Object element) {
        if (element instanceof PsiNameIdentifierOwner) {
            element = ((PsiNameIdentifierOwner) element).getNameIdentifier();
        } else if (element instanceof PsiReferenceExpression) {
            element = ((PsiReferenceExpression) element).getReferenceNameElement();
        }
        return ObjectUtils.tryCast(element, PsiIdentifier.class);
    }

    private List<String> getNGram(@NotNull PsiElement element) {
        int order = modelOrder;
        final List<String> tokens = new ArrayList<>();
        for (PsiElement token : SyntaxTraverser
                .revPsiTraverser()
                .withRoot(element.getContainingFile())
                .onRange(new TextRange(0, max(0, element.getTextOffset())))
                .filter(IdNamesNGramModelRunner::shouldLex)) {
            tokens.add(token.getText());
            if (--order < 1) {
                break;
            }
        }
        return Lists.reverse(tokens);
    }
}
