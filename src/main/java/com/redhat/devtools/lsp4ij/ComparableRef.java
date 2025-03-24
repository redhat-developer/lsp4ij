package com.redhat.devtools.lsp4ij;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ComparableRef<T extends Comparable<T>> {
    private T value;

    @NotNull
    public static <T extends Comparable<T>> ComparableRef<T> create(@Nullable T value) {
        return new ComparableRef<>(value);
    }

    @NotNull
    public static <T extends Comparable<T>> ComparableRef<T> create() {
        return new ComparableRef<>(null);
    }

    public ComparableRef(@Nullable T value) {
        this.value = value;
    }

    @Nullable
    public T getValue() {
        return value;
    }

    public void highestOf(@Nullable T otherValue) {
        T value = getValue();
        if ((value == null) || ((otherValue != null) && (value.compareTo(otherValue) < 0))) {
            this.value = otherValue;
        }
    }

    public void lowestOf(@Nullable T otherValue) {
        T value = getValue();
        if ((value == null) || ((otherValue != null) && (value.compareTo(otherValue) > 0))) {
            this.value = otherValue;
        }
    }
}
