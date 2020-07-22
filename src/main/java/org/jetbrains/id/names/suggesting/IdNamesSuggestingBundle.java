package org.jetbrains.id.names.suggesting;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class IdNamesSuggestingBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.IdNamesSuggestingBundle";
    private static final IdNamesSuggestingBundle INSTANCE = new IdNamesSuggestingBundle();

    private IdNamesSuggestingBundle() { super(BUNDLE); }

    @NotNull
    public static String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

    @NotNull
    public static Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object @NotNull ... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
