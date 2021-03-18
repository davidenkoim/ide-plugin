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
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Integer.max;
import static java.lang.Integer.min;
import static java.lang.Math.abs;

public class DatasetManager {
    public static final String TOKEN_DELIMITER = "\u2581";
    public static final String STRING_TOKEN = "<str>";
    public static final String NUMBER_TOKEN = "<num>";
    public static final String VARIABLE_TOKEN = "<var>";
    public static final List<String> NumberTypes = Arrays.asList("INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL");
    public static final List<String> IntegersToLeave = Arrays.asList("0", "1", "32", "64");
    public static int NGramLengthBeforeUsage = 20;
    public static int NGramLengthAfterUsage = 20;
    private static final Path DatasetDir = Paths.get(PathManager.getSystemPath(), "dataset");

    public static void build(@NotNull Project project) {
        build(project, null);
    }

    public static void build(@NotNull Project project, @Nullable ProgressIndicator progressIndicator) {
        Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project));
        HashMap<String, List<VariableFeatures>> dataset = new HashMap<>();
        HashMap<String, Object> fileStats = new HashMap<>();
        double progress = 0;
        final double total = files.size();
        Instant start = Instant.now();
        @NotNull PsiManager psiManager = PsiManager.getInstance(project);
        System.out.printf("Number of files to parse: %s\n", files.size());
        for (VirtualFile file : files) {
            @Nullable PsiFile psiFile = psiManager.findFile(file);
            if (psiFile != null) {
                @NotNull String filePath = file.getPath();
                dataset.put(filePath, DatasetManager.parsePsiFile(psiFile));
                fileStats.put(filePath, psiFile.getTextLength());
                double timeLeft = Duration.between(start, Instant.now()).toMillis() * (total / ++progress - 1) / 1000.;
                if (progressIndicator != null) {
                    progressIndicator.setIndeterminate(false);
                    progressIndicator.setText2(file.getPath());
                    progressIndicator.setFraction(progress / total);
                }
                System.out.printf("Status:\t%.2f%%; Time left:\t%.1f s.\r", progress / total * 100., timeLeft);
            } else {
                System.out.println("PSI isn't found");
            }
        }
        save(project, dataset, fileStats);
        Instant end = Instant.now();
        Duration timeSpent = Duration.between(start, end);
        long minutes = timeSpent.toMinutes();
        int seconds = (int) (timeSpent.toMillis() / 1000. - 60. * minutes);
        System.out.printf("Done in %d min. %d s.\n",
                minutes, seconds);
    }

    private static List<VariableFeatures> parsePsiFile(@NotNull PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .filter(element -> element instanceof PsiVariable)
                .toList()
                .stream()
                .map(e -> (PsiVariable) e)
                .map(v -> getVariableFeatures(v, file))
                .collect(Collectors.toList());
    }

    public static VariableFeatures getVariableFeatures(PsiVariable variable, PsiFile file) {
        Stream<PsiReference> elementUsages = findReferences(variable, file);
        return new VariableFeatures(variable,
                Stream.concat(Stream.of(variable), elementUsages)
                        .map(DatasetManager::getIdentifier)
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparing(PsiElement::getTextOffset))
                        .map(id -> getUsageFeatures(variable, id, file))
                        .collect(Collectors.toList()));
    }

    private static UsageFeatures getUsageFeatures(@NotNull PsiVariable variable, @NotNull PsiElement element, @NotNull PsiFile file) {
        List<String> tokens = new ArrayList<>();
//        Adding tokens before usage
        int order = NGramLengthBeforeUsage;
        for (PsiElement token : SyntaxTraverser
                .revPsiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, max(0, element.getTextOffset() - 1)))
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(IdNamesNGramModelRunner::shouldLex)) {
            tokens.add(processToken(token, variable));
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
            tokens.add(processToken(token, variable));
            if (--order < 1) {
                break;
            }
        }
        return new UsageFeatures(
                String.join(DatasetManager.TOKEN_DELIMITER, tokens),
                abs(variable.getTextOffset() - element.getTextOffset())
        );
    }

    private static String processToken(@NotNull PsiElement token, @NotNull PsiVariable variable) {
        String text = token.getText();
        if (token.getParent() instanceof PsiLiteral) {
            String literalType = ((PsiJavaToken) token).getTokenType().toString();
            if (literalType.equals("STRING_LITERAL")) {
                return text.length() > 10 ? STRING_TOKEN : text;
            }
            if (NumberTypes.contains(literalType)) {
                return IntegersToLeave.contains(text) ? text : NUMBER_TOKEN;
            }
        } else if (isVariableOrReference(variable, token)) {
            return VARIABLE_TOKEN;
        }
        return text;
    }


    public static Stream<PsiReference> findReferences(@NotNull PsiNameIdentifierOwner identifierOwner, @NotNull PsiFile file) {
//        Unknown problems when using GlobalSearchScope.projectScope. Most likely there are too many fields and searching breaks.
        return ReferencesSearch.search(identifierOwner, GlobalSearchScope.fileScope(file))
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
            if (fileStats != null) {
                statsFile.createNewFile();
                mapper.writeValue(statsFile, fileStats);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
