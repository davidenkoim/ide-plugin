package org.jetbrains.id.names.suggesting.impl;

import com.google.common.collect.Lists;
import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.completion.ngram.slp.translating.VocabularyRunner;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PathManager;
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
import org.jetbrains.id.names.suggesting.IdNamesSuggestingBundle;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingService;
import org.jetbrains.id.names.suggesting.Prediction;
import org.jetbrains.id.names.suggesting.VocabularyManager;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;

public class IdNamesNGramModelRunner implements IdNamesSuggestingModelRunner {
    private static final List<Class<? extends PsiNameIdentifierOwner>> SUPPORTED_TYPES = new ArrayList<>();

    static {
        SUPPORTED_TYPES.add(PsiVariable.class);
    }

    private final NGramModel myModel;
    private Vocabulary myVocabulary = new Vocabulary();
    private HashSet<Integer> myRememberedVariables = new HashSet<>();

    public IdNamesNGramModelRunner(boolean isLargeCorpora) {
        myModel = new JMModel(6, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter());
    }

    private static boolean isSupported(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return SUPPORTED_TYPES.stream().anyMatch(type -> type.isInstance(identifierOwner));
    }

    public int getModelPriority(){
        return this.myVocabulary.size();
    }

    @Override
    public List<Prediction> suggestNames(@NotNull PsiNameIdentifierOwner identifierOwner) {
        if (!isSupported(identifierOwner)) {
            return new ArrayList<>();
        }
        List<List<Integer>> allUsageNGramIndices = findUsageNGramIndices(identifierOwner);
        allUsageNGramIndices.forEach(this::forgetUsage);
        List<Prediction> predictionList = allUsageNGramIndices
                .stream()
                .flatMap(usage -> predictUsageName(usage, getIdTypeFilter(identifierOwner)))
                .sorted((p1, p2) -> -Double.compare(p1.getProbability(), p2.getProbability()))
                .distinct()
                .limit(IdNamesSuggestingService.PREDICTION_CUTOFF)
                .collect(Collectors.toList());
        allUsageNGramIndices.forEach(this::learnUsage);
        return predictionList;
    }

    private List<List<Integer>> findUsageNGramIndices(PsiNameIdentifierOwner identifierOwner) {
        Stream<PsiReference> elementUsages = findReferences(identifierOwner);
        return Stream.concat(Stream.of(identifierOwner), elementUsages)
                .map(IdNamesNGramModelRunner::getIdentifier)
                .filter(Objects::nonNull)
                .map(this::getNGramIndices)
                .collect(Collectors.toList());
    }

    public static Stream<PsiReference> findReferences(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return ReferencesSearch.search(identifierOwner)
                .findAll()
                .stream();
    }

    private Stream<Prediction> predictUsageName(@NotNull List<Integer> usageNGramIndices,
                                                @NotNull Predicate<Map.Entry<Integer, ?>> idTypeFilter) {
        return myModel.predictToken(usageNGramIndices, myModel.getOrder() - 1)
                .entrySet()
                .stream()
                .filter(idTypeFilter)
                .map(e -> new Prediction(myVocabulary.toWord(e.getKey()), toProb(e.getValue()), getModelPriority()));
    }

    private void forgetUsage(@NotNull List<Integer> usageNGramIndices) {
        myModel.forgetToken(usageNGramIndices, myModel.getOrder() - 1);
    }

    private void learnUsage(@NotNull List<Integer> usageNGramIndices) {
        myModel.learnToken(usageNGramIndices, myModel.getOrder() - 1);
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

    private List<Integer> getNGramIndices(@NotNull PsiElement element) {
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
            progressIndicator.setText2(file.getPath());
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

    private static final String MODEL_DIRECTORY = PathManager.getSystemPath() + "\\model";

    public double save(@Nullable ProgressIndicator progressIndicator) {
        return save(MODEL_DIRECTORY, progressIndicator);
    }

    public double save(@NotNull String model_directory, @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            progressIndicator.setText(IdNamesSuggestingBundle.message("saving.global.model"));
            progressIndicator.setText2("");
            progressIndicator.setIndeterminate(true);
        }
        File counterFile = new File(model_directory + "\\counter.ser");
        File rememberedVariablesFile = new File(model_directory + "\\rememberedVariables.ser");
        File vocabularyFile = new File(model_directory + "\\vocabulary.ser");
        try {
            counterFile.getParentFile().mkdir();
            counterFile.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(counterFile);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            myModel.getCounter().writeExternal(objectOutputStream);
            objectOutputStream.close();
            fileOutputStream.close();

            rememberedVariablesFile.createNewFile();
            fileOutputStream = new FileOutputStream(rememberedVariablesFile);
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(myRememberedVariables);
            objectOutputStream.close();
            fileOutputStream.close();

            vocabularyFile.createNewFile();
            VocabularyRunner.INSTANCE.write(myVocabulary, vocabularyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (counterFile.length() + vocabularyFile.length() + rememberedVariablesFile.length()) / (1024. * 1024);
    }

    public void load(@Nullable ProgressIndicator progressIndicator) {
        load(MODEL_DIRECTORY, progressIndicator);
    }

    public void load(@NotNull String model_directory, @Nullable ProgressIndicator progressIndicator) {
        File counterFile = new File(model_directory + "\\counter.ser");
        File rememberedVariablesFile = new File(model_directory + "\\rememberedVariables.ser");
        File vocabularyFile = new File(model_directory + "\\vocabulary.ser");
        if (counterFile.exists() && rememberedVariablesFile.exists() && vocabularyFile.exists()) {
            try {
                if (progressIndicator != null) {
                    progressIndicator.setIndeterminate(true);
                    progressIndicator.setText(IdNamesSuggestingBundle.message("loading.file", counterFile.getName()));
                }
                FileInputStream fileInputStream = new FileInputStream(counterFile);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                myModel.getCounter().readExternal(objectInputStream);
                objectInputStream.close();
                fileInputStream.close();

                if (progressIndicator != null) {
                    progressIndicator.setText(IdNamesSuggestingBundle.message("loading.file", rememberedVariablesFile.getName()));
                }
                fileInputStream = new FileInputStream(rememberedVariablesFile);
                objectInputStream = new ObjectInputStream(fileInputStream);
                myRememberedVariables = (HashSet) objectInputStream.readObject();
                objectInputStream.close();
                fileInputStream.close();

                if (progressIndicator != null) {
                    progressIndicator.setText(IdNamesSuggestingBundle.message("loading.file", vocabularyFile.getName()));
                }
                myVocabulary = VocabularyManager.read(vocabularyFile);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
