/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.console;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decodes the ANSI escape sequences, such as colours and bold, in the output of the DAP 'output' events of a debug
 * session, with {@link AnsiEscapeDecoder}, as a process console decodes those of a process's output. Text which has
 * no ANSI style keeps the console content type of its category, such as red for stderr, and text which has one gets
 * the style's. A decoder is kept per category, as a process console keeps one per stream: a style which starts in an
 * event, and an escape sequence which an event splits, continue in the next event of the same category.
 */
public final class DAPOutputDecoder {

    /**
     * A piece of output, without escape sequences, and the console content type to print it with.
     *
     * @param text        the text.
     * @param contentType the console content type.
     */
    public record Chunk(@NotNull String text, @NotNull ConsoleViewContentType contentType) {
    }

    // The decoder of each category, by getCategory()
    private final Map<String, AnsiEscapeDecoder> decoders = new ConcurrentHashMap<>();

    /**
     * Returns the output of a DAP 'output' event as the debug console prints it: its text without the ANSI escape
     * sequences, in chunks with the console content type of each.
     *
     * @param output   the output of the event.
     * @param category the category of the event.
     * @return the chunks to print, none for an empty output or one which has escape sequences only.
     */
    public @NotNull List<Chunk> decode(@Nullable String output, @Nullable String category) {
        if (StringUtils.isEmpty(output)) {
            return List.of();
        }
        String outputCategory = getCategory(category);
        ConsoleViewContentType contentType = getContentType(outputCategory);
        // AnsiEscapeDecoder decodes stdout and stderr only, so the other categories are decoded as stdout
        ProcessOutputType outputType = OutputEventArgumentsCategory.STDERR.equals(outputCategory) ?
                ProcessOutputType.STDERR : ProcessOutputType.STDOUT;
        var chunks = new ArrayList<Chunk>();
        decoders.computeIfAbsent(outputCategory, ignored -> new AnsiEscapeDecoder())
                .escapeText(output, outputType, (text, attributes) -> chunks.add(new Chunk(text,
                        // The decoder gives the output type itself to text which has no ANSI style
                        attributes == outputType ? contentType : ConsoleViewContentType.getConsoleViewType(attributes))));
        return chunks;
    }

    /**
     * Returns the category of an 'output' event: console for none, or for one that the client doesn't know, as the
     * DAP specification says.
     */
    private static @NotNull String getCategory(@Nullable String category) {
        if (category == null) {
            return OutputEventArgumentsCategory.CONSOLE;
        }
        return switch (category) {
            case OutputEventArgumentsCategory.STDOUT, OutputEventArgumentsCategory.STDERR,
                 OutputEventArgumentsCategory.IMPORTANT -> category;
            default -> OutputEventArgumentsCategory.CONSOLE;
        };
    }

    private static @NotNull ConsoleViewContentType getContentType(@NotNull String category) {
        return switch (category) {
            case OutputEventArgumentsCategory.STDERR -> ConsoleViewContentType.LOG_ERROR_OUTPUT;
            case OutputEventArgumentsCategory.IMPORTANT -> ConsoleViewContentType.LOG_VERBOSE_OUTPUT;
            default -> ConsoleViewContentType.LOG_INFO_OUTPUT;
        };
    }
}
