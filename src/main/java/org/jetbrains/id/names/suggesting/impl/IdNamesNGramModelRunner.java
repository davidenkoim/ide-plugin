package org.jetbrains.id.names.suggesting.impl;

import com.google.common.collect.Lists;
import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.ObjectUtils;
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;

public class IdNamesNGramModelRunner implements IdNamesSuggestingModelRunner {
    private static final int PREDICTION_CUTOFF = 10;
    private static final List<Class<? extends PsiNameIdentifierOwner>> SUPPORTED_TYPES = new ArrayList<>();

    static {
        SUPPORTED_TYPES.add(PsiVariable.class);
    }

    private final NGramModel myModel;
    private final Vocabulary myVocabulary = new Vocabulary();
    private final HashSet<Integer> myRememberedVariables = new HashSet<>();

    public IdNamesNGramModelRunner(boolean isLargeCorpora) {
        myModel = new JMModel(6, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter());
    }

    private static boolean isSupported(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return SUPPORTED_TYPES.stream().anyMatch(type -> type.isInstance(identifierOwner));
    }

    @Override
    public LinkedHashSet<String> suggestNames(@NotNull PsiNameIdentifierOwner identifierOwner) {
        if (!isSupported(identifierOwner)) {
            return new LinkedHashSet<>();
        }
        Stream<PsiReference> elementUsages = ReferencesSearch.search(identifierOwner)
                                                             .findAll()
                                                             .stream();
        List<List<Integer>> allUsageNGramIndicies = Stream.concat(Stream.of(identifierOwner), elementUsages)
                                                          .map(IdNamesNGramModelRunner::getIdentifier)
                                                          .filter(Objects::nonNull)
                                                          .map(this::getNGramIndicies)
                                                          .collect(Collectors.toList());
        allUsageNGramIndicies.forEach(this::forgetUsage);
        LinkedHashSet<String> resultSet = allUsageNGramIndicies
                .stream()
                .flatMap(usage -> predictUsageName(usage, getIdTypeFilter(identifierOwner)))
                .sorted((p1, p2) -> -Double.compare(p1.getSecond(), p2.getSecond()))
                .map(Pair::getFirst)
                .distinct()
                .limit(PREDICTION_CUTOFF)
                .map(myVocabulary::toWord)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        allUsageNGramIndicies.forEach(this::learnUsage);
        return resultSet;
    }

    private Stream<Pair<Integer, Double>> predictUsageName(@NotNull List<Integer> usageNGramIndicies,
                                                           @NotNull Predicate<Map.Entry<Integer, ?>> idTypeFilter) {
        return myModel.predictToken(usageNGramIndicies, myModel.getOrder() - 1)
                      .entrySet()
                      .stream()
                      .filter(idTypeFilter)
                      .map(e -> new Pair<>(e.getKey(), toProb(e.getValue())));
    }

    private void forgetUsage(@NotNull List<Integer> usageNGramIndicies) {
        myModel.forgetToken(usageNGramIndicies, myModel.getOrder() - 1);
    }

    private void learnUsage(@NotNull List<Integer> usageNGramIndicies) {
        myModel.learnToken(usageNGramIndicies, myModel.getOrder() - 1);
    }

    private @NotNull Predicate<Map.Entry<Integer, ?>> getIdTypeFilter(@NotNull PsiNameIdentifierOwner identifierOwner) {
        if (identifierOwner instanceof PsiVariable) {
            return entry -> myRememberedVariables.contains(entry.getKey());
        }
        return entry -> false;
    }

    private static @Nullable PsiIdentifier getIdentifier(Object element) {
        if (element instanceof PsiNameIdentifierOwner) {
            element = ((PsiNameIdentifierOwner) element).getNameIdentifier();
        } else if (element instanceof PsiReferenceExpression) {
            element = ((PsiReferenceExpression) element).getReferenceNameElement();
        }
        return ObjectUtils.tryCast(element, PsiIdentifier.class);
    }

    private List<Integer> getNGramIndicies(@NotNull PsiElement element) {
        return myVocabulary.toIndices(getNGram(element));
    }

    private List<String> getNGram(@NotNull PsiElement element) {
        int order = myModel.getOrder();
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

    public void learnProject(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
        progressIndicator.setIndeterminate(false);
        Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                                                               GlobalSearchScope.projectScope(project));
        double progress = 0;
        final double total = files.size();
        for (VirtualFile file : files) {
            ObjectUtils.consumeIfNotNull(PsiManager.getInstance(project).findFile(file), this::learnPsiFile);
            progressIndicator.setFraction(++progress / total);
        }
    }

    public void learnPsiFile(@NotNull PsiFile file) {
        myModel.learn(myVocabulary.toIndices(lexPsiFile(file)));
    }

    private List<String> lexPsiFile(@NotNull PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                              .withRoot(file)
                              .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                              .filter(IdNamesNGramModelRunner::shouldLex)
                              .toList()
                              .stream()
                              .peek(this::rememberIdName)
                              .map(PsiElement::getText)
                              .collect(Collectors.toList());
    }

    private void rememberIdName(PsiElement element) {
        if (element instanceof PsiIdentifier && element.getParent() instanceof PsiVariable) {
            myRememberedVariables.add(myVocabulary.toIndex(element.getText()));
        }
    }

    private static boolean shouldLex(@NotNull PsiElement element) {
        return element.getFirstChild() == null // is leaf
               && !StringUtils.isBlank(element.getText())
               && !(element instanceof PsiComment);
    }

    private double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / myVocabulary.size();
    }
}
