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
import kotlin.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;
import static org.jetbrains.id.names.suggesting.api.IdNamesContributor.GLOBAL_PREDICTION_CUTOFF;

public class IdNamesSuggestingNGramModelRunner implements IdNamesSuggestingModelRunner {
    private final Vocabulary myVocabulary;
    private final NGramModel myModel;
    private final HashSet<Integer> myIdentifiers = new HashSet<>();

    public IdNamesSuggestingNGramModelRunner(boolean isLargeCorpora) {
        this.myVocabulary = new Vocabulary();
        if (isLargeCorpora) {
            this.myModel = new JMModel(6, 0.5, new GigaCounter());
        } else {
            this.myModel = new JMModel(6, 0.5, new ArrayTrieCounter());
        }
    }

    @Override
    public void learnPsiFile(@NotNull PsiFile file) {
        myModel.learn(myVocabulary.toIndices(lexPsiFile(file)));
    }

    @Override
    public void learnProject(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
        progressIndicator.setIndeterminate(false);
        Collection<VirtualFile> files = getTrainingFiles(project);
        double[] done = new double[2];
        done[0] = 1;
        done[1] = files.size();
        files.forEach(file -> {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if (psiFile != null) {
                learnPsiFile(psiFile);
            }
            progressIndicator.setFraction(++done[0] / done[1]);
        });
    }

    private static Collection<VirtualFile> getTrainingFiles(@NotNull Project project) {
        return FileTypeIndex.getFiles(JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project));
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
        if (element instanceof PsiIdentifier && element.getParent() instanceof PsiVariable) {
            myIdentifiers.add(myVocabulary.toIndex(element.getText()));
        }
    }

    private boolean shouldLex(@NotNull PsiElement element) {
        return element.getFirstChild() == null // is leaf
                && !StringUtils.isBlank(element.getText())
                && !(element instanceof PsiComment);
    }

    @Override
    public LinkedHashSet<String> predictVariableName(@NotNull PsiVariable variable) {
        Stream<PsiReference> elementUsages = ReferencesSearch
                .search(variable)
                .findAll()
                .stream();
        return Stream.concat(Stream.of(variable), elementUsages)
                .map(this::getIdentifier)
                .flatMap(this::predictUsageName)
                .sorted((p1, p2) -> -Double.compare(p1.getSecond(), p2.getSecond()))
                .map(Pair::getFirst)
                .distinct()
                .limit(GLOBAL_PREDICTION_CUTOFF)
                .map(myVocabulary::toWord)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private @Nullable PsiIdentifier getIdentifier(Object element) {
        if (element instanceof PsiVariable) return ((PsiVariable) element).getNameIdentifier();
        if (element instanceof PsiReferenceExpression)
            return (PsiIdentifier) ((PsiReferenceExpression) element).getReferenceNameElement();
        return null;
    }

    private Stream<Pair<Integer, Double>> predictUsageName(@Nullable PsiIdentifier identifier) {
        if (identifier == null) {
            return Stream.empty();
        }
        return myModel.predictToken(myVocabulary.toIndices(getNGramPrefix(identifier, myModel.getOrder())), myModel.getOrder() - 1)
                .entrySet()
                .stream()
                .filter(e -> myIdentifiers.contains(e.getKey()))
                .map(e -> new Pair<>(e.getKey(), toProb(e.getValue())));
    }

    private double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / myVocabulary.size();
    }

    @Override
    public void forgetVariableUsages(@NotNull PsiVariable variable) {
        Stream<PsiReference> elementUsages = ReferencesSearch
                .search(variable)
                .findAll()
                .stream();
        Stream.concat(Stream.of(variable), elementUsages)
                .map(this::getIdentifier)
                .forEach(this::forgetVariableUsage);
    }

    private void forgetVariableUsage(PsiIdentifier identifier) {
        myModel.forgetToken(myVocabulary.toIndices(getNGramPrefix(identifier, myModel.getOrder())), myModel.getOrder() - 1);
    }

    @Override
    public void learnVariableUsages(@NotNull PsiVariable variable) {
        Stream<PsiReference> elementUsages = ReferencesSearch
                .search(variable)
                .findAll()
                .stream();
        Stream.concat(Stream.of(variable), elementUsages)
                .map(this::getIdentifier)
                .forEach(this::learnVariableUsage);
    }

    private void learnVariableUsage(PsiIdentifier identifier) {
        myModel.learnToken(myVocabulary.toIndices(getNGramPrefix(identifier, myModel.getOrder())), myModel.getOrder() - 1);
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
