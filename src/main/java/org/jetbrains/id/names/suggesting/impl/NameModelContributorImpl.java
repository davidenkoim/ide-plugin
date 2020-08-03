package org.jetbrains.id.names.suggesting.impl;

import com.google.common.collect.Lists;
import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.NameModelContributor;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;

public class NameModelContributorImpl implements NameModelContributor {
    private final Vocabulary vocabulary;
    private final NGramModel model;
    private final int GLOBAL_PREDICTION_CUTOFF = 10;
    private final HashSet<Integer> identifiers = new HashSet<>();

    public NameModelContributorImpl() {
        this.vocabulary = new Vocabulary();
        this.model = new JMModel(6, 0.5, new GigaCounter());
    }

    @Override
    public void learnPsiFile(@NotNull PsiFile file) {
        model.learn(vocabulary.toIndices(lexPsiFile(file)));
    }

    private List<String> lexPsiFile(@NotNull PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .filter(this::shouldLex)
                .toList()
                .stream()
                .peek(this::addToIdentifiers)
                .map(PsiElement::getText)
                .collect(Collectors.toList());
    }

    private void addToIdentifiers(PsiElement element) {
        if (element instanceof PsiIdentifier) {
            if (element.getParent() instanceof PsiVariable) {
                identifiers.add(vocabulary.toIndex(element.getText()));
            }
        }
    }

    private boolean shouldLex(@NotNull PsiElement element) {
        return element.getFirstChild() == null // is leaf
                && !StringUtils.isBlank(element.getText())
                && !(element instanceof PsiComment);
    }

    @Override
    public LinkedHashSet<String> contribute(@NotNull PsiVariable element) {
        Stream<PsiReference> elementUsages = ReferencesSearch
                .search(element)
                .findAll()
                .stream();
        return Stream.concat(Stream.of(element), elementUsages)
                .map(this::getIdentifier)
                .flatMap(this::contributeUsageName)
                .sorted((p1, p2) -> -Double.compare(p1.getSecond(), p2.getSecond()))
                .map(Pair::getFirst)
                .distinct()
                .limit(GLOBAL_PREDICTION_CUTOFF)
                .map(vocabulary::toWord)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private PsiIdentifier getIdentifier(Object element) {
        if (element instanceof PsiVariable) return ((PsiVariable) element).getNameIdentifier();
        if (element instanceof PsiReferenceExpression)
            return (PsiIdentifier) ((PsiReferenceExpression) element).getReferenceNameElement();
        return null;
    }

    private Stream<Pair<Integer, Double>> contributeUsageName(@NotNull PsiIdentifier identifier) {
        return model.predictToken(vocabulary.toIndices(getNGramPrefix(identifier, model.getOrder())), model.getOrder() - 1)
                .entrySet()
                .stream()
                .filter(e -> identifiers.contains(e.getKey()))
                .map(e -> new Pair<Integer, Double>(e.getKey(), toProb(e.getValue())));
    }

    public double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / vocabulary.size();
    }

    @Override
    public void forgetVariableUsages(@NotNull PsiVariable element) {
        Stream<PsiReference> elementUsages = ReferencesSearch
                .search(element)
                .findAll()
                .stream();
        Stream.concat(Stream.of(element), elementUsages)
                .map(this::getIdentifier)
                .forEach(this::forgetVariableUsage);
    }

    private void forgetVariableUsage(PsiIdentifier identifier) {
        model.forgetToken(vocabulary.toIndices(getNGramPrefix(identifier, model.getOrder())), model.getOrder() - 1);
    }

    private List<String> getNGramPrefix(@NotNull PsiElement element, int order) {
        final List<String> prefix = new ArrayList<>();
        for (final PsiElement token : SyntaxTraverser
                .revPsiTraverser()
                .withRoot(element.getContainingFile())
                .onRange(new TextRange(0, max(0, element.getTextOffset())))
                .filter(this::shouldLex)) {
            prefix.add(token.getText());
            if (--order < 1) {
                break;
            }
        }
        return Lists.reverse(prefix);
    }

}
