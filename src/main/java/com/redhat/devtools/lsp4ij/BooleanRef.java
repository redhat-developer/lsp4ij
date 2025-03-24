package com.redhat.devtools.lsp4ij;

import org.jetbrains.annotations.NotNull;

public class BooleanRef {
    private boolean value;

    @NotNull
    public static BooleanRef create(boolean value) {
        return new BooleanRef(value);
    }

    @NotNull
    public static BooleanRef create() {
        return new BooleanRef(false);
    }

    public BooleanRef(boolean value) {
        this.value = value;
    }

    public boolean getValue() {
        return value;
    }

    public void andEquals(boolean otherValue) {
        this.value = this.value && otherValue;
    }

    public void orEquals(boolean otherValue) {
        this.value = this.value || otherValue;
    }
}
