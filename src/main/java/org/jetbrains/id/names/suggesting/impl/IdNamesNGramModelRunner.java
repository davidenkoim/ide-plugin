package org.jetbrains.id.names.suggesting.impl;

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IdNamesNGramModelRunner implements IdNamesSuggestingModelRunner {
    /**
     * {@link HashMap} from {@link Class} of identifier to {@link HashSet} of remembered identifiers of this {@link Class}.
     */
    private static HashMap<Class<? extends PsiNameIdentifierOwner>, HashSet<Integer>> myRememberedIdentifiers = new HashMap<>();

    private final NGramModel myModel;
    private Vocabulary myVocabulary = new Vocabulary();

    public IdNamesNGramModelRunner(boolean isLargeCorpora) {
        myModel = new JMModel(6, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter());
    }

    public int getModelPriority() {
        return this.myVocabulary.size();
    }

    @Override
    public List<Prediction> suggestNames(Class<? extends PsiNameIdentifierOwner> identifierClass, List<List<String>> usageNGrams) {
        List<List<Integer>> allUsageNGramIndices = nGramToIndices(usageNGrams);
        allUsageNGramIndices.forEach(this::forgetUsage);
        List<Prediction> predictionList = allUsageNGramIndices
                .stream()
                .flatMap(usage -> predictUsageName(usage, getIdTypeFilter(identifierClass)))
                .sorted((p1, p2) -> -Double.compare(p1.getProbability(), p2.getProbability()))
                .distinct()
                .limit(IdNamesSuggestingService.PREDICTION_CUTOFF)
                .collect(Collectors.toList());
        allUsageNGramIndices.forEach(this::learnUsage);
        return predictionList;
    }

    private @NotNull List<List<Integer>> nGramToIndices(@NotNull List<List<String>> usageNGrams) {
        return usageNGrams.stream().map(myVocabulary::toIndices).collect(Collectors.toList());
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

    private @NotNull Predicate<Map.Entry<Integer, ?>> getIdTypeFilter(@NotNull Class<? extends PsiNameIdentifierOwner> identifierClass) {
        Class<? extends PsiNameIdentifierOwner> parentClass = getSupportedParentClass(identifierClass);
        return entry -> parentClass != null && myRememberedIdentifiers.get(parentClass).contains(entry.getKey());
    }

    private @Nullable Class<? extends PsiNameIdentifierOwner> getSupportedParentClass(@NotNull Class<? extends PsiNameIdentifierOwner> identifierClass) {
        for (Class<? extends PsiNameIdentifierOwner> c : myRememberedIdentifiers.keySet()) {
            if (identifierClass.isAssignableFrom(c)) {
                return c;
            }
        }
        return null;
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
        if (element instanceof PsiIdentifier) {
            PsiNameIdentifierOwner parent = (PsiNameIdentifierOwner) element.getParent();
            Class<? extends PsiNameIdentifierOwner> parentClass = getSupportedParentClass(parent.getClass());
            myRememberedIdentifiers.get(parentClass).add(myVocabulary.toIndex(element.getText()));
        }
    }

    public static boolean shouldLex(@NotNull PsiElement element) {
        return element.getFirstChild() == null // is leaf
                && !StringUtils.isBlank(element.getText())
                && !(element instanceof PsiComment);
    }

    private double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / myVocabulary.size();
    }

    private static final Path MODEL_DIRECTORY = Paths.get(PathManager.getSystemPath(), "model");

    public double save(@Nullable ProgressIndicator progressIndicator) {
        return save(MODEL_DIRECTORY, progressIndicator);
    }

    public double save(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            progressIndicator.setText(IdNamesSuggestingBundle.message("saving.global.model"));
            progressIndicator.setText2("");
            progressIndicator.setIndeterminate(true);
        }
        File counterFile = model_directory.resolve("counter.ser").toFile();
        File rememberedVariablesFile = model_directory.resolve("rememberedVariables.ser").toFile();
        File vocabularyFile = model_directory.resolve("vocabulary.ser").toFile();
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
            objectOutputStream.writeObject(myRememberedIdentifiers);
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

    public void load(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator) {
        File counterFile = model_directory.resolve("counter.ser").toFile();
        File rememberedVariablesFile = model_directory.resolve("rememberedVariables.ser").toFile();
        File vocabularyFile = model_directory.resolve("vocabulary.ser").toFile();
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
                myRememberedIdentifiers = (HashMap) objectInputStream.readObject();
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

    public int getOrder() {
        return myModel.getOrder();
    }
}
