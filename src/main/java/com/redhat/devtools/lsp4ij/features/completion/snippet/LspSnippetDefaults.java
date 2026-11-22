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
 * (#2073). As in VS Code, the default of an index is the text of the first placeholder with that index in the order
 * that the placeholders open, or the first value of the first choice with that index: in {@code ${1:a${1:b}}}, the
 * outer placeholder gives it. A placeholder or a choice that doesn't give the default of its index repeats it, as a
 * tab stop does. The text of a placeholder can have tab stops, which repeat the defaults of their indices in turn.
 * <p>
 * The defaults are read once they are all given. A tab stop that repeats a default being resolved, as in
 * {@code ${1:x$1}} or {@code ${1:a$2} ${2:b$1}}, repeats nothing. So in a cycle of defaults that repeat each other, the
 * text of each depends on where the cycle is entered: there, 1 gives {@code ab} and 2 gives {@code ba}. Any other
 * default is resolved once, and resolving those in cycles again stops once it has gone through
 * {@value #MAX_CYCLE_WORK} parts and characters in all. The text of an index, which {@link #getText(int)} or a tab
 * stop of the snippet gives, is the same each time, also once that bound is reached. Defaults that repeat each other
 * can still double their text at each index, as in {@code ${1:$2$2} ${2:$3$3} ... ${30:x}}, so the text that a
 * default repeats from others stops at {@value #MAX_REPEATED_LENGTH} characters.
 */
@ApiStatus.Internal
public final class LspSnippetDefaults {

    static final int MAX_REPEATED_LENGTH = 10_000;

    /**
     * The work that resolving defaults in cycles again can take in all, as the parts of their texts that it goes
     * through and the characters that it gives, after which a tab stop in a default repeats nothing more. The text of
     * a default in a cycle isn't kept, as it depends on where the cycle is entered, so each tab stop that repeats it
     * resolves it again. Defaults that repeat each other twice in a cycle, as in
     * {@code ${1:$2$2$1} ${2:$3$3$1} ... ${30:$1}}, would then be resolved about 2^30 times, and the limit of the text
     * that they repeat doesn't bound that, as their text is empty. Two defaults that repeat each other K times, as in
     * {@code ${1:$2$2...} ${2:$1$1...}}, are each resolved again K times, each going through its K parts again: so the
     * bound counts the work, as a bound on the resolutions would still let K^2 parts through. A million parts and
     * characters take tens of milliseconds. Only such cycles reach the bound: any other default is resolved once, and
     * doesn't count. A text that the bound stops depends on it, so it isn't kept.
     */
    static final int MAX_CYCLE_WORK = 1_000_000;

    // The depth of a cut, for a text that repeats no default being resolved
    private static final int NO_CUT = Integer.MAX_VALUE;
    // The depth of a cut below that of any default, for a tab stop that MAX_CYCLE_WORK stopped: no default that
    // repeats it is kept
    private static final int BOUND_CUT = -1;
    // The index of the default whose text is resolved, for the text of a snippet
    private static final int NO_INDEX = -1;

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

    /**
     * A resolved text, with its cut: the lowest depth of a default being resolved that it repeats, and so repeats
     * nothing of, or {@link #NO_CUT}. A text with a cut depends on where its cycle is entered.
     */
    private record Resolved(@NotNull String text, int cut) {
    }

    private static final Resolved NOTHING = new Resolved("", NO_CUT);
    private static final Resolved BOUND_REACHED = new Resolved("", BOUND_CUT);

    private final Map<Integer, Default> defaults = new HashMap<>();
    // The indices whose default an open placeholder will give
    private final Set<Integer> reserved = new HashSet<>();
    // The text of each default outside a cycle, once resolved, which is the same wherever it is repeated
    private final Map<Integer, String> texts = new HashMap<>();
    // The text of each index that getText(int) or a tab stop of the snippet gave, which they give again
    private final Map<Integer, String> rootTexts = new HashMap<>();
    // The defaults being resolved, with their depths, to stop at those that repeat themselves
    private final Map<Integer, Integer> resolving = new HashMap<>();
    // The defaults resolved at least once
    private final Set<Integer> resolved = new HashSet<>();
    // The work that resolving defaults in cycles again took, in parts and characters
    private long cycleWork;

    /**
     * Opens a placeholder with the given index, which gives the default of its index when it closes, unless a
     * placeholder or a choice before it holds or gave that default. A placeholder that doesn't give it repeats it,
     * as a tab stop does.
     *
     * @param index the index of the placeholder.
     * @return true if the placeholder gives the default of its index, with {@link #close(int, List)}, and false if a
     * placeholder or a choice before it holds or gave it.
     */
    public boolean open(int index) {
        return !defaults.containsKey(index) && reserved.add(index);
    }

    /**
     * Closes a placeholder whose {@link #open(int)} returned true, which makes its text the default of its index. As
     * in VS Code, a placeholder without a text, as {@code ${1:}}, gives none: a placeholder or a choice after it can.
     *
     * @param index the index of the placeholder.
     * @param parts the text of the placeholder.
     * @return true if the text is the default of the index, and false if the placeholder has no text, or didn't hold
     * the default of its index.
     */
    public boolean close(int index, @NotNull List<Part> parts) {
        if (!reserved.remove(index) || parts.isEmpty()) {
            return false;
        }
        defaults.put(index, new Default(List.copyOf(parts), null));
        return true;
    }

    /**
     * Makes the first value of the given choice the default of its index, unless a placeholder or a choice before it
     * holds or gave one. A choice that doesn't give it repeats it, as a tab stop does.
     *
     * @param index   the index of the choice.
     * @param choices the values of the choice.
     * @return true if the first value of the choice is the default of the index, and false if a placeholder or a
     * choice before it holds or gave it.
     */
    public boolean choice(int index, @NotNull List<String> choices) {
        if (defaults.containsKey(index) || reserved.contains(index)) {
            return false;
        }
        String value = choices.isEmpty() ? "" : choices.get(0);
        defaults.put(index, new Default(List.of(new Text(value)), List.copyOf(choices)));
        return true;
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
     * empty for an index without a default. It's the same each time, as the text of a tab stop of the snippet with
     * that index.
     *
     * @param index the index.
     * @return the text of its default.
     */
    public @NotNull String getText(int index) {
        String text = rootTexts.get(index);
        if (text == null) {
            text = resolve(index).text();
            rootTexts.put(index, text);
        }
        return text;
    }

    /**
     * Returns the given text of a snippet, in which a tab stop repeats the default of its index.
     *
     * @param parts the text of the snippet.
     * @return the text, with the defaults that its tab stops repeat.
     */
    public @NotNull String getText(@NotNull List<Part> parts) {
        return textOf(parts, Integer.MAX_VALUE, NO_INDEX).text();
    }

    private @NotNull Resolved resolve(int index) {
        String text = texts.get(index);
        if (text != null) {
            return new Resolved(text, NO_CUT);
        }
        Integer depth = resolving.get(index);
        if (depth != null) {
            // A default being resolved, which repeats nothing here
            return new Resolved("", depth);
        }
        Default value = defaults.get(index);
        if (value == null) {
            return NOTHING;
        }
        // Resolved before but not kept, so in a cycle, which a tab stop in a default repeats again
        boolean again = !resolved.add(index) && !resolving.isEmpty();
        if (again && cycleWork >= MAX_CYCLE_WORK) {
            return BOUND_REACHED;
        }
        int ownDepth = resolving.size();
        resolving.put(index, ownDepth);
        Resolved result;
        try {
            result = textOf(value.parts(), MAX_REPEATED_LENGTH, index);
        } finally {
            resolving.remove(index);
        }
        if (again) {
            // The parts that it went through, and the characters that it gave
            cycleWork += value.parts().size() + result.text().length();
        }
        if (result.cut() <= ownDepth) {
            // In a cycle: a default that it repeats repeats it, or it repeats a default being resolved
            return result;
        }
        texts.put(index, result.text());
        return new Resolved(result.text(), NO_CUT);
    }

    /**
     * Returns the given text of the default of the given index, or of a snippet, in which a tab stop repeats the
     * default of its index, and nothing for a tab stop of that same index.
     */
    private @NotNull Resolved textOf(@NotNull List<Part> parts, int maxRepeatedLength, int ownIndex) {
        var text = new StringBuilder();
        int repeated = 0;
        int cut = NO_CUT;
        for (Part part : parts) {
            if (part instanceof Text value) {
                text.append(value.value());
            } else if (part instanceof TabStop tabStop && tabStop.index() != ownIndex && repeated < maxRepeatedLength) {
                // A tab stop of the snippet has the text of its index, the same each time
                Resolved value = ownIndex == NO_INDEX
                        ? new Resolved(getText(tabStop.index()), NO_CUT)
                        : resolve(tabStop.index());
                int length = Math.min(value.text().length(), maxRepeatedLength - repeated);
                text.append(value.text(), 0, length);
                repeated += length;
                cut = Math.min(cut, value.cut());
            }
        }
        return new Resolved(text.toString(), cut);
    }
}
