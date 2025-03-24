package com.redhat.devtools.lsp4ij;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringRef {
    private String value = null;

    @NotNull
    public static StringRef create(@NotNull String value) {
        return new StringRef(value);
    }

    @NotNull
    public static StringRef create() {
        return create("");
    }

    public StringRef(@NotNull String value) {
        this.value = value;
    }

    @NotNull
    public String getValue() {
        return value;
    }

    public void append(@Nullable String otherValue) {
        if (otherValue != null) {
            this.value += otherValue;
        }
    }
}
