package org.jetbrains.id.names.suggesting.impl;

import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.NGramModelContributor;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;

public class NGramModelContributorImpl implements NGramModelContributor {
    private final Vocabulary vocabulary;
    private final NGramModel model;
    private final int GLOBAL_PREDICTION_CUTOFF = 10;
    private final HashSet<Integer> identifiers = new HashSet<>();

    public NGramModelContributorImpl() {
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
                .map(this::addToIdentifiers)
                .map(PsiElement::getText)
                .toList();
    }

    private PsiElement addToIdentifiers(PsiElement element) {
        if (element instanceof PsiIdentifier) {
            if (element.getParent() instanceof PsiVariable) {
                identifiers.add(vocabulary.toIndex(element.getText()));
            }
        }
        return element;
    }

    private boolean shouldLex(@NotNull PsiElement element) {
        return element.getFirstChild() == null // is leaf
                && !StringUtils.isBlank(element.getText())
                && !(element instanceof PsiComment);
    }

    @Override
    public LinkedHashSet<String> contribute(@NotNull PsiElement element) {
        Stream<PsiElement> elementUsages = Arrays.stream(element.getReferences())
                .map(PsiReference::getElement);
        return Stream.concat(Stream.of(element), elementUsages)
                .flatMap(this::contributeUsageName)
                .sorted((p1, p2) -> -Double.compare(p1.getSecond(), p2.getSecond()))
                .map(Pair<Integer, Double>::getFirst)
                .distinct()
                .limit(GLOBAL_PREDICTION_CUTOFF)
                .map(vocabulary::toWord)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Stream<Pair<Integer, Double>> contributeUsageName(@NotNull PsiElement element) {
        return model.predictToken(vocabulary.toIndices(getNGramPrefix(element, model.getOrder())), model.getOrder() - 1)
                .entrySet()
                .stream()
                .filter(e -> identifiers.contains(e.getKey()))
                .map(e -> new Pair<Integer, Double>(e.getKey(), toProb(e.getValue())))
                .sorted((p1, p2) -> -Double.compare(p1.getSecond(), p2.getSecond()))
                .limit(20);
    }

    public double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / vocabulary.size();
    }

    private List<String> getNGramPrefix(@NotNull PsiElement element, @NotNull Integer order) {
        return SyntaxTraverser.revPsiTraverser()
                .withRoot(element.getContainingFile())
                .onRange(new TextRange(0, max(0, element.getTextOffset())))
                .filter(this::shouldLex)
                .map(PsiElement::getText)
                .toList()
                .stream()
                .limit(order)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
    }
}
