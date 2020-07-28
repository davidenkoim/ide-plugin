package org.jetbrains.id.names.suggesting.test;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingBundle;

public class SuggestingIntentionTest extends IdNamesSuggestingTestCase {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "intention";
    }

    public void testIntentionIsAvailableOnDeclarationLeft() { doTestIntentionIsAvailable(); }

    public void testIntentionIsAvailableOnDeclarationRight() { doTestIntentionIsAvailable(); }

    public void testIntentionIsAvailableOnVariableLeft() { doTestIntentionIsAvailable(); }

    public void testIntentionIsAvailableOnVariableRight() { doTestIntentionIsAvailable(); }

    public void testIntentionIsAvailableOnParameter() { doTestIntentionIsAvailable(); }

    public void testIntentionIsNotAvailable1() { doTestIntentionIsNotAvailable(); }

    public void testIntentionIsNotAvailable2() { doTestIntentionIsNotAvailable(); }

    public void testIntentionIsNotAvailable3() { doTestIntentionIsNotAvailable(); }

    private void doTestIntentionIsAvailable() {
        configureByFile();
        assertContainsElements(ContainerUtil.map(myFixture.getAvailableIntentions(), IntentionAction::getText),
                IdNamesSuggestingBundle.message("intention.text"));
    }

    private void doTestIntentionIsNotAvailable() {
        configureByFile();
        assertDoesntContain(ContainerUtil.map(myFixture.getAvailableIntentions(), IntentionAction::getText),
                IdNamesSuggestingBundle.message("intention.text"));
    }
}
