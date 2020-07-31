package org.jetbrains.id.names.suggesting;

import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class NGramModelContributor {
    private final Vocabulary vocabulary;
    private final NGramModel model;

    public NGramModelContributor() {
        this.vocabulary = new Vocabulary();
        this.model = new JMModel(6, 0.5, new GigaCounter());
    }

    public void learnPsiFile(PsiFile file) {
        model.learn(vocabulary.toIndices(lexPsiFile(file)));
    }

    private List<String> lexPsiFile(PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .filter(this::shouldLex)
                .map(PsiElement::getText)
                .toList();
    }

    private boolean shouldLex(PsiElement element) {
        return element.getFirstChild() == null // is leaf
                && !StringUtils.isBlank(element.getText())
                && !(element instanceof PsiComment);
    }


}
