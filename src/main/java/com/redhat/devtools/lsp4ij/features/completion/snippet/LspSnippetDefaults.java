/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion.snippet;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The defaults of the indices of a snippet, which its tab stops, placeholders and choices with those indices repeat
 * (#2073). The default of an index is the text of the first placeholder with that index, or the first value of the
 * first choice with that index, as in VS Code. The text of a placeholder can have tab stops, which repeat the defaults
 * of their indices in turn.
 * <p>
 * The defaults are read once they are all put. Each is resolved once, and a tab stop that repeats a default being
 * resolved, as in {@code ${1:x$1}} or {@code ${1:$2} ${2:$1}}, repeats nothing. Defaults that repeat each other can
 * still double their text at each index, as in {@code ${1:$2$2} ${2:$3$3} ... ${30:x}}, so the text that a default
 * repeats from others stops at {@value #MAX_REPEATED_LENGTH} characters.
 */
@ApiStatus.Internal
public final class LspSnippetDefaults {

    static final int MAX_REPEATED_LENGTH = 10_000;

    /**
     * A part of the text of a placeholder, or of a snippet: a text, or a tab stop, which repeats the default of its
     * index.
     */
    public sealed interface Part permits Text, TabStop {
    }

    public record Text(@NotNull String value) implements Part {
    }

    public record TabStop(int index) implements Part {
    }

    /**
     * The default of an index: the text of its first placeholder, or the first value of its first choice, with the
     * values of that choice.
     */
    private record Default(@NotNull List<Part> parts, @Nullable List<String> choices) {
    }

    private final Map<Integer, Default> defaults = new HashMap<>();
    // The text of each default, once resolved
    private final Map<Integer, String> texts = new HashMap<>();
    // The defaults being resolved, to stop at those that repeat themselves
    private final Set<Integer> resolving = new HashSet<>();

    /**
     * Makes the given text of a placeholder the default of its index, unless a placeholder or a choice before it gave
     * one. As in VS Code, a placeholder without a text, as {@code ${1:}}, gives none.
     *
     * @param index the index of the placeholder.
     * @param parts the text of the placeholder.
     */
    public void putPlaceholder(int index, @NotNull List<Part> parts) {
        if (!parts.isEmpty()) {
            defaults.putIfAbsent(index, new Default(List.copyOf(parts), null));
        }
    }

    /**
     * Makes the first value of the given choice the default of its index, unless a placeholder or a choice before it
     * gave one.
     *
     * @param index   the index of the choice.
     * @param choices the values of the choice.
     */
    public void putChoice(int index, @NotNull List<String> choices) {
        String value = choices.isEmpty() ? "" : choices.get(0);
        defaults.putIfAbsent(index, new Default(List.of(new Text(value)), List.copyOf(choices)));
    }

    /**
     * Returns the values of the choice that gave the default of the given index, or null if none did.
     *
     * @param index the index.
     * @return the values of the choice that gave the default of the index, or null if none did.
     */
    public @Nullable List<String> getChoices(int index) {
        Default value = defaults.get(index);
        return value != null ? value.choices() : null;
    }

    /**
     * Returns the text of the default of the given index, in which a tab stop repeats the default of its index. It's
     * empty for an index without a default.
     *
     * @param index the index.
     * @return the text of its default.
     */
    public @NotNull String getText(int index) {
        String text = texts.get(index);
        if (text != null) {
            return text;
        }
        Default value = defaults.get(index);
        if (value == null || !resolving.add(index)) {
            // No default, or one that repeats itself
            return "";
        }
        try {
            text = textOf(value.parts(), MAX_REPEATED_LENGTH);
        } finally {
            resolving.remove(index);
        }
        texts.put(index, text);
        return text;
    }

    /**
     * Returns the given text of a snippet, in which a tab stop repeats the default of its index.
     *
     * @param parts the text of the snippet.
     * @return the text, with the defaults that its tab stops repeat.
     */
    public @NotNull String getText(@NotNull List<Part> parts) {
        return textOf(parts, Integer.MAX_VALUE);
    }

    private @NotNull String textOf(@NotNull List<Part> parts, int maxRepeatedLength) {
        var text = new StringBuilder();
        int repeated = 0;
        for (Part part : parts) {
            if (part instanceof Text value) {
                text.append(value.value());
            } else if (part instanceof TabStop tabStop && repeated < maxRepeatedLength) {
                String value = getText(tabStop.index());
                int length = Math.min(value.length(), maxRepeatedLength - repeated);
                text.append(value, 0, length);
                repeated += length;
            }
        }
        return text.toString();
    }
}
