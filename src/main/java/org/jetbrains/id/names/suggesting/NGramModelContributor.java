package org.jetbrains.id.names.suggesting;

import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;

public class NGramModelContributor {
    private final Vocabulary vocabulary;
    private final NGramModel model;
    private final int GLOBAL_PREDICTION_CUTOFF = 10;

    public NGramModelContributor() {
        this.vocabulary = new Vocabulary();
        this.model = new JMModel(6, 0.5, new GigaCounter());
    }

    public void learnPsiFile(@NotNull PsiFile file) {
        model.learn(vocabulary.toIndices(lexPsiFile(file)));
    }

    private List<String> lexPsiFile(@NotNull PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .filter(this::shouldLex)
                .map(PsiElement::getText)
                .toList();
    }

    private boolean shouldLex(@NotNull PsiElement element) {
        return element.getFirstChild() == null // is leaf
                && !StringUtils.isBlank(element.getText())
                && !(element instanceof PsiComment);
    }

    public LinkedHashSet<String> predictVariableName(@NotNull PsiElement element){
        Stream<PsiElement> elementUsages = Arrays.stream(element.getReferences())
                        .map(PsiReference::getElement);
        return Stream.concat(Stream.of(element), elementUsages)
                .flatMap(this::predictUsageName)
                .sorted((p1, p2) -> -Double.compare(p1.getSecond(), p2.getSecond()))
                .map(Pair<Integer, Double>::getFirst)
                .distinct()
                .limit(GLOBAL_PREDICTION_CUTOFF)
                .map(vocabulary::toWord)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Stream<Pair<Integer, Double>> predictUsageName(@NotNull PsiElement element){
        return model.predictAtIndex(vocabulary.toIndices(getNGramPrefix(element, model.getOrder())), model.getOrder())
                .entrySet()
                .stream()
                .map(e -> new Pair<Integer, Double>(e.getKey(), toProb(e.getValue())));
    }

    public double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / vocabulary.size();
    }

    private List<String> getNGramPrefix(@NotNull PsiElement element, @NotNull Integer order){
        return SyntaxTraverser.revPsiTraverser()
                .withRoot(element.getContainingFile())
                .onRange(new TextRange(0, max(0, element.getTextOffset() + element.getTextLength())))
                .filter(this::shouldLex)
                .map(PsiElement::getText)
                .toList()
                .stream()
                .limit(order)
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
    }
}
