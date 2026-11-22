/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.features.completion.SnippetTemplateFactory;
import com.redhat.devtools.lsp4ij.features.completion.snippet.DefaultLspSnippetHandler;
import com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetParser;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SnippetTextEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_CURRENT_LINE;
import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_CURRENT_WORD;
import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_DIRECTORY;
import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_FILENAME;
import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_FILENAME_BASE;
import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_FILEPATH;
import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_LINE_INDEX;
import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_LINE_NUMBER;
import static com.redhat.devtools.lsp4ij.features.completion.snippet.LspSnippetVariableConstants.TM_SELECTED_TEXT;

/**
 * The snippet edits of a text document edit ({@code SnippetTextEdit}, LSP 3.18), which LSP4IJ declares it supports
 * with {@code workspace.workspaceEdit.snippetEditSupport} (#1909).
 * <p>
 * A snippet edit is applied with the text of its snippet, in which a placeholder has its default value, as
 * {@code ${1:name}} gives {@code name}. The first snippet edit of the document of the selected editor then replaces
 * that text with a live template of the snippet, as the snippet of a completion starts, so that the user goes through
 * its placeholders. The other snippet edits keep their text, as a client may apply a snippet edit that isn't
 * interactive.
 */
final class SnippetTextEdits {

    private SnippetTextEdits() {
    }

    /**
     * A snippet edit to start as a live template in the selected editor of its document, once the workspace edit is
     * applied.
     *
     * @param editor   the selected editor of the document.
     * @param template the template of the snippet.
     * @param offset   the offset of the text of the snippet once the edits of the document are applied.
     * @param text     the text of the snippet, which the edit inserted.
     */
    record Interactive(@NotNull Editor editor, @NotNull Template template, int offset, @NotNull String text) {

        /**
         * Replaces the text of the snippet, which the edit inserted, with the template, in the same command. The
         * text stays if the template can't start, as when the editor closed.
         */
        void start() {
            Project project = editor.getProject();
            Document document = editor.getDocument();
            if (editor.isDisposed() || project == null || project.isDisposed()
                    || offset + text.length() > document.getTextLength()
                    || !text.contentEquals(document.getImmutableCharSequence().subSequence(offset, offset + text.length()))) {
                return;
            }
            document.deleteString(offset, offset + text.length());
            editor.getCaretModel().moveToOffset(offset);
            TemplateManager.getInstance(project).startTemplate(editor, template);
        }
    }

    /**
     * Returns the text edits of the given edits of the document, in which a snippet edit has the text of its
     * snippet, and gives the first snippet edit to start as a live template, if the document is in the selected editor
     * and the snippet has a placeholder or a tab stop.
     *
     * @param edits       the edits of a text document edit.
     * @param file        the file of the document.
     * @param document    the document, before the edits.
     * @param interactive receives the snippet edit to start as a live template once the workspace edit is applied.
     * @return the text edits to apply to the document.
     */
    static @NotNull List<TextEdit> toTextEdits(@NotNull List<Either<TextEdit, SnippetTextEdit>> edits,
                                               @NotNull VirtualFile file,
                                               @NotNull Document document,
                                               @NotNull Consumer<Interactive> interactive) {
        if (edits.stream().noneMatch(Either::isRight)) {
            return edits.stream().map(Either::getLeft).toList();
        }
        @Nullable Project project = ProjectLocator.getInstance().guessProjectForFile(file);
        @Nullable Editor editor = project != null ? selectedEditor(project, document) : null;
        List<TextEdit> textEdits = new ArrayList<>(edits.size());
        int interactiveIndex = -1;
        @Nullable Template interactiveTemplate = null;
        for (var edit : edits) {
            if (edit.isLeft()) {
                textEdits.add(edit.getLeft());
                continue;
            }
            SnippetTextEdit snippetEdit = edit.getRight();
            if (snippetEdit.getRange() == null) {
                continue;
            }
            String snippet = snippetEdit.getSnippet() != null ? StringUtil.notNullize(snippetEdit.getSnippet().getValue()) : "";
            Function<String, String> variables = variable -> resolveVariable(variable, file, document, snippetEdit.getRange());
            String text = textOf(snippet, variables);
            if (editor != null && interactiveIndex < 0) {
                Template template = createTemplate(snippet, variables, project);
                if (template.getSegmentsCount() > 0 || !template.getVariables().isEmpty()) {
                    interactiveIndex = textEdits.size();
                    interactiveTemplate = template;
                }
            }
            textEdits.add(new TextEdit(snippetEdit.getRange(), text));
        }
        if (interactiveTemplate != null) {
            // As the document has it: applying an edit converts its line separators to \n
            String inserted = StringUtil.convertLineSeparators(textEdits.get(interactiveIndex).getNewText());
            interactive.accept(new Interactive(editor, interactiveTemplate,
                    offsetAfterEdits(document, textEdits, interactiveIndex), inserted));
        }
        return textEdits;
    }

    private static @Nullable Editor selectedEditor(@NotNull Project project, @NotNull Document document) {
        Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
        return editor != null && editor.getDocument() == document ? editor : null;
    }

    /**
     * Returns the text of the given snippet, in which a placeholder or a choice has its default value, and a tab stop
     * repeats the default of the placeholder or the choice with its index, wherever that one is, as
     * {@code ${1:name} = $1} gives {@code name = name}. The default of a placeholder is its text with that of its nested
     * placeholders, as {@code ${1:a ${2:b}} = $1} gives {@code a b = a b}.
     */
    private static @NotNull String textOf(@NotNull String snippet, @NotNull Function<String, String> variables) {
        // A tab stop in the default of a placeholder repeats a default too, as in ${1:${2:x}-$2}=$1, and the default
        // that it repeats can come later in the snippet: a second pass expands it with the defaults of the first
        Map<Integer, String> defaults = defaultsOf(snippet, variables, Map.of());
        Map<Integer, String> expanded = defaultsOf(snippet, variables, defaults);
        var text = new SnippetText(variables) {
            @Override
            public void tabstop(int index) {
                appendContent(expanded.get(index));
            }
        };
        new LspSnippetParser(text).parse(snippet);
        return text.getTemplateContent();
    }

    /**
     * Returns the default of each index of the given snippet: the text of the first placeholder with that index, with
     * the text of its nested placeholders, or the first value of the first choice with that index. A tab stop in a
     * default repeats the default of its index that comes before it, or else the given one.
     */
    private static @NotNull Map<Integer, String> defaultsOf(@NotNull String snippet,
                                                          @NotNull Function<String, String> variables,
                                                          @NotNull Map<Integer, String> known) {
        Map<Integer, String> defaults = new HashMap<>();
        new LspSnippetParser(new SnippetText(variables) {
            private final Deque<PlaceholderStart> placeholders = new ArrayDeque<>();

            @Override
            public void tabstop(int index) {
                appendContent(defaults.containsKey(index) ? defaults.get(index) : known.get(index));
            }

            @Override
            public void startPlaceholder(int index, String name, int level) {
                placeholders.push(new PlaceholderStart(index, getCurrentOffset()));
                super.startPlaceholder(index, name, level);
            }

            @Override
            public void endPlaceholder(int level) {
                // The text of the placeholder, with the text of its nested placeholders
                PlaceholderStart start = placeholders.pop();
                defaults.putIfAbsent(start.index(), getTemplateContent(start.offset()));
            }

            @Override
            public void choice(int index, List<String> choices) {
                defaults.putIfAbsent(index, choices.isEmpty() ? "" : choices.get(0));
                super.choice(index, choices);
            }
        }).parse(snippet);
        return defaults;
    }

    /**
     * The text of a snippet, in which a placeholder or a choice has its default value, and a variable its value, or
     * its name for a variable that has none here.
     */
    private static class SnippetText extends DefaultLspSnippetHandler {

        SnippetText(@NotNull Function<String, String> variables) {
            super(variables, null);
        }

        @Override
        public void startPlaceholder(int index, String name, int level) {
            // The text of a placeholder can be a variable, as in ${1:$TM_FILENAME_BASE}
            String value = name.startsWith("$") ? resolveVariable(name) : null;
            super.startPlaceholder(index, value != null ? value : name, level);
        }

        @Override
        public void variable(String name) {
            String value = resolveVariable(name);
            super.variable(value != null ? value : name);
        }
    }

    /**
     * Where the text of a placeholder starts in the text of its snippet.
     */
    private record PlaceholderStart(int index, int offset) {
    }

    private static @NotNull Template createTemplate(@NotNull String snippet,
                                                    @NotNull Function<String, String> variables,
                                                    @NotNull Project project) {
        Template template = SnippetTemplateFactory.createTemplate(snippet, project, variables, null, false);
        // The template takes the place of the text of the snippet, which the edit inserted
        template.setInline(false);
        // The language server gave the text its indentation
        template.setToIndent(false);
        template.setToReformat(false);
        return template;
    }

    /**
     * Returns the value of the given snippet variable for a snippet edit at the given range, or null for a variable
     * that has none here, which the snippet then shows as a placeholder.
     */
    private static @Nullable String resolveVariable(@NotNull String variable,
                                                    @NotNull VirtualFile file,
                                                    @NotNull Document document,
                                                    @NotNull Range range) {
        String name = variable.startsWith("$") ? variable.substring(1) : variable;
        int line = range.getStart().getLine();
        return switch (name) {
            case TM_FILENAME -> file.getName();
            case TM_FILENAME_BASE -> file.getNameWithoutExtension();
            case TM_FILEPATH -> file.getPath();
            case TM_DIRECTORY -> file.getParent() != null ? file.getParent().getPath() : "";
            case TM_LINE_INDEX -> String.valueOf(line);
            case TM_LINE_NUMBER -> String.valueOf(line + 1);
            case TM_CURRENT_LINE -> line < document.getLineCount()
                    ? document.getText(new TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line)))
                    : "";
            // An edit has no selection or word of its own
            case TM_SELECTED_TEXT, TM_CURRENT_WORD -> "";
            default -> null;
        };
    }

    /**
     * Returns the offset that the new text of the edit at the given index has once the edits are applied, as
     * {@link LSPIJUtils#applyEdits(Editor, Document, List, boolean)} applies them: each edit at its range in the
     * document before the edits, and the inserts at one offset in their order.
     */
    static int offsetAfterEdits(@NotNull Document document, @NotNull List<TextEdit> edits, int index) {
        int start = LSPIJUtils.toOffset(edits.get(index).getRange().getStart(), document);
        int offset = start;
        for (int i = 0; i < edits.size(); i++) {
            if (i == index) {
                continue;
            }
            TextEdit edit = edits.get(i);
            int editStart = LSPIJUtils.toOffset(edit.getRange().getStart(), document);
            int editEnd = LSPIJUtils.toOffset(edit.getRange().getEnd(), document);
            boolean before = editEnd < start
                    || (editEnd == start && editStart < start)
                    || (editStart == start && editEnd == start && i < index);
            if (before) {
                offset += StringUtil.convertLineSeparators(StringUtil.notNullize(edit.getNewText())).length()
                        - (editEnd - editStart);
            }
        }
        return offset;
    }
}
