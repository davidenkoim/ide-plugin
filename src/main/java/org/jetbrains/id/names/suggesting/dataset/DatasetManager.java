package org.jetbrains.id.names.suggesting.dataset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;
import static java.lang.Integer.min;
import static java.lang.Math.abs;

public class DatasetManager {
    public static int NGramLengthBeforeUsage = 6;
    public static int NGramLengthAfterUsage = 6;
    public static String VariableToken = "<var>";
    private static final Path DatasetDir = Paths.get(PathManager.getSystemPath(), "dataset");

    public static void build(@NotNull Project project, @NotNull ProgressIndicator progressIndicator) {
//        TODO: Something strange happens with progressIndicator. It might be some leaks from the references' search.
        Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project));
        HashMap<String, List<VariableFeatures>> dataset = new HashMap<>();
        HashMap<String, Object> fileStats = new HashMap<>();
        double progress = 0;
        final double total = files.size();
        progressIndicator.setIndeterminate(false);
        for (VirtualFile file : files) {
            progressIndicator.setText2(file.getPath());
            progressIndicator.setFraction(++progress / total);
            @Nullable PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
            if(psiFile != null) {
                dataset.put(file.getPath(), DatasetManager.parsePsiFile(psiFile));
                fileStats.put(file.getPath(), psiFile.getTextLength());
            }
        }
        save(project, dataset, fileStats);
    }

    private static List<VariableFeatures> parsePsiFile(@NotNull PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .filter(element -> element instanceof PsiVariable)
                .toList()
                .stream()
                .map(e -> (PsiVariable) e)
                .map(DatasetManager::getVariableFeatures)
                .collect(Collectors.toList());
    }

    private static VariableFeatures getVariableFeatures(PsiVariable variable) {
        Stream<PsiReference> elementUsages = findReferences(variable);
        return new VariableFeatures(variable,
                Stream.concat(Stream.of(variable), elementUsages)
                        .map(DatasetManager::getIdentifier)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(PsiElement::getTextOffset))
                        .map(id -> getUsageFeatures(variable, id))
                        .collect(Collectors.toList()));
    }

    private static UsageFeatures getUsageFeatures(@NotNull PsiVariable variable, @NotNull PsiElement element) {
        PsiFile file = element.getContainingFile();
        List<String> tokens = new ArrayList<>();
//        Adding tokens before usage
        int order = NGramLengthBeforeUsage;
        for (PsiElement token : SyntaxTraverser
                .revPsiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, max(0, element.getTextOffset() - 1)))
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(IdNamesNGramModelRunner::shouldLex)) {
            if (isVariableOrReference(variable, token)) {
                tokens.add(VariableToken);
            } else {
                tokens.add(token.getText());
            }
            if (--order < 1) {
                break;
            }
        }
        tokens = Lists.reverse(tokens);
//        Adding tokens after usage
        order = NGramLengthAfterUsage + 1;
        for (PsiElement token : SyntaxTraverser
                .psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(min(element.getTextOffset(), file.getTextLength()), file.getTextLength()))
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(IdNamesNGramModelRunner::shouldLex)) {
            if (isVariableOrReference(variable, token)) {
                tokens.add(VariableToken);
            } else {
                tokens.add(token.getText());
            }
            if (--order < 1) {
                break;
            }
        }
        return new UsageFeatures(
                String.join(" ", tokens),
                abs(variable.getTextOffset() - element.getTextOffset())
        );
    }

    public static Stream<PsiReference> findReferences(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return ReferencesSearch.search(identifierOwner)
                .findAll()
                .stream();
    }

    private static @Nullable PsiIdentifier getIdentifier(Object element) {
        if (element instanceof PsiNameIdentifierOwner) {
            element = ((PsiNameIdentifierOwner) element).getNameIdentifier();
        } else if (element instanceof PsiReferenceExpression) {
            element = ((PsiReferenceExpression) element).getReferenceNameElement();
        }
        return ObjectUtils.tryCast(element, PsiIdentifier.class);
    }

    private static boolean isVariableOrReference(@NotNull PsiVariable variable, @Nullable PsiElement token) {
        if (token instanceof PsiIdentifier) {
            PsiElement parent = token.getParent();
            if (parent instanceof PsiReference) {
                return PsiManager.getInstance(variable.getProject()).areElementsEquivalent(variable, ((PsiReference) parent).resolve());
            } else {
                return PsiManager.getInstance(variable.getProject()).areElementsEquivalent(variable, parent);
            }
        }
        return false;
    }

    private static void save(@NotNull Project project, @NotNull HashMap<String, List<VariableFeatures>> dataset) {
        save(project, dataset, null);
    }

    private static void save(@NotNull Project project, @NotNull HashMap<String, List<VariableFeatures>> dataset, @Nullable HashMap<String, Object> fileStats) {
        File datasetFile = DatasetDir.resolve(project.getName() + "_dataset.json").toFile();
        File statsFile = DatasetDir.resolve(project.getName() + "_stats.json").toFile();
        try {
            datasetFile.getParentFile().mkdir();
            datasetFile.createNewFile();
            ObjectMapper mapper = new ObjectMapper();
            mapper.writeValue(datasetFile, dataset);
            if(fileStats != null){
                statsFile.createNewFile();
                mapper.writeValue(statsFile, fileStats);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
