/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.completion;

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.intellij.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerItem;
import com.redhat.devtools.intellij.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.intellij.lsp4ij.commands.CommandExecutor;
import com.redhat.devtools.intellij.lsp4ij.internal.StringUtils;
import com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetIndentOptions;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.redhat.devtools.intellij.lsp4ij.operations.completion.CompletionProposalTools.createLspIndentOptions;
import static com.redhat.devtools.intellij.lsp4ij.operations.completion.snippet.LspSnippetVariableConstants.*;
import static com.redhat.devtools.intellij.lsp4ij.ui.IconMapper.getIcon;

/**
 * LSP completion lookup element.
 */
public class LSPCompletionProposal extends LookupElement {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSPCompletionProposal.class);

    private final CompletionItem item;
    private final int initialOffset;
    private final PsiFile file;
    private final Boolean supportResolveCompletion;
    private int currentOffset;
    private int bestOffset;
    private final Editor editor;
    private final LanguageServerItem languageServer;
    private String documentation;

    public LSPCompletionProposal(PsiFile file, Editor editor, int offset, CompletionItem item, LanguageServerItem languageServer) {
        this.file = file;
        this.item = item;
        this.editor = editor;
        this.languageServer = languageServer;
        this.initialOffset = offset;
        this.currentOffset = offset;
        this.bestOffset = getPrefixCompletionStart(editor.getDocument(), offset);
        ServerCapabilities serverCapabilities = languageServer.getServerWrapper().getServerCapabilities();
        this.supportResolveCompletion = serverCapabilities != null && serverCapabilities.getCompletionProvider() != null && serverCapabilities.getCompletionProvider().getResolveProvider();
        putUserData(CodeCompletionHandlerBase.DIRECT_INSERTION, true);
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
        Template template = null;
        if (item.getInsertTextFormat() == InsertTextFormat.Snippet) {
            // Insert text has snippet syntax, ex : ${1:name}
            String snippetContent = getInsertText();
            // Get the indentation settings
            LspSnippetIndentOptions indentOptions = createLspIndentOptions(snippetContent, file);
            // Load the insert text to build:
            // - an IJ Template instance which will take care of replacement of placeholders
            // - the insert text without placeholders
            template = SnippetTemplateFactory.createTemplate(snippetContent, context.getProject(), name -> getVariableValue(name), indentOptions);
            // Update the TextEdit with the content snippet content without placeholders
            // ex : ${1:name} --> name
            updateInsertText(template.getTemplateText());
        }

        // Apply all text edits
        apply(context.getDocument(), context.getCompletionChar(), 0, context.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET));

        if (template != null && ((TemplateImpl) template).getVariableCount() > 0) {
            // LSP completion with snippet syntax, activate the inline template
            context.setAddCompletionChar(false);
            EditorModificationUtil.moveCaretRelatively(editor, -template.getTemplateText().length());
            TemplateManager.getInstance(context.getProject()).startTemplate(context.getEditor(), template);
        }
    }

    /**
     * Returns the text content to insert coming from the LSP CompletionItem.
     *
     * @return the text content to insert coming from the LSP CompletionItem.
     */
    protected String getInsertText() {
        String insertText = this.item.getInsertText();
        Either<TextEdit, InsertReplaceEdit> eitherTextEdit = this.item.getTextEdit();
        if (eitherTextEdit != null) {
            if (eitherTextEdit.isLeft()) {
                insertText = eitherTextEdit.getLeft().getNewText();
            } else {
                insertText = eitherTextEdit.getRight().getNewText();
            }
        }
        if (insertText == null) {
            insertText = this.item.getLabel();
        }
        return insertText;
    }

    private void updateInsertText(String newText) {
        Either<TextEdit, InsertReplaceEdit> eitherTextEdit = this.item.getTextEdit();
        if (eitherTextEdit != null) {
            if (eitherTextEdit.isLeft()) {
                eitherTextEdit.getLeft().setNewText(newText);
            } else {
                eitherTextEdit.getRight().setNewText(newText);
            }
        }
    }

    public int getPrefixCompletionStart(Document document, int completionOffset) {
        Either<TextEdit, InsertReplaceEdit> textEdit = this.item.getTextEdit();
        if (textEdit != null) {
            if (textEdit.isLeft()) {
                try {
                    return LSPIJUtils.toOffset(this.item.getTextEdit().getLeft().getRange().getStart(), document);
                } catch (RuntimeException e) {
                    LOGGER.warn(e.getLocalizedMessage(), e);
                }
            } else {
                try {
                    return LSPIJUtils.toOffset(this.item.getTextEdit().getRight().getInsert().getStart(), document);
                } catch (RuntimeException e) {
                    LOGGER.warn(e.getLocalizedMessage(), e);
                }
            }
        }
        String insertText = getInsertText();
        try {
            String subDoc = document.getText(new TextRange(
                    Math.max(0, completionOffset - insertText.length()),
                    Math.min(insertText.length(), completionOffset)));
            for (int i = 0; i < insertText.length() && i < completionOffset; i++) {
                String tentativeCommonString = subDoc.substring(i);
                if (insertText.startsWith(tentativeCommonString)) {
                    return completionOffset - tentativeCommonString.length();
                }
            }
        } catch (RuntimeException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
        }
        return completionOffset;
    }

    @Override
    public Set<String> getAllLookupStrings() {
        if (StringUtils.isBlank(item.getFilterText())) {
            return super.getAllLookupStrings();
        }
        return new HashSet<>(Arrays.asList(item.getFilterText(), item.getLabel()));
    }

    @NotNull
    @Override
    public String getLookupString() {
        return item.getLabel();
    }

    private boolean isDeprecated() {
        return (item.getTags() != null && item.getTags().contains(CompletionItemTag.Deprecated))
                || (item.getDeprecated() != null && item.getDeprecated().booleanValue());
    }

    @Override
    public void renderElement(LookupElementPresentation presentation) {
        presentation.setItemText(item.getLabel());
        presentation.setTypeText(item.getDetail());
        presentation.setIcon(getIcon(item.getKind()));
        if (isDeprecated()) {
            presentation.setStrikeout(true);
        }
    }

    protected void apply(Document document, char trigger, int stateMask, int offset) {
        String insertText = null;
        Either<TextEdit, InsertReplaceEdit> eitherTextEdit = item.getTextEdit();
        TextEdit textEdit = null;
        if (eitherTextEdit != null) {
            if (eitherTextEdit.isLeft()) {
                textEdit = eitherTextEdit.getLeft();
            } else {
                // trick to partially support the new InsertReplaceEdit from LSP 3.16. Reuse previously code for TextEdit.
                InsertReplaceEdit insertReplaceEdit = eitherTextEdit.getRight();
                textEdit = new TextEdit(insertReplaceEdit.getInsert(), insertReplaceEdit.getNewText());
            }
        }
        try {
            if (textEdit == null) {
                insertText = getInsertText();
                Position start = LSPIJUtils.toPosition(this.bestOffset, document);
                Position end = LSPIJUtils.toPosition(offset, document); // need 2 distinct objects
                textEdit = new TextEdit(new Range(start, end), insertText);
            } else if (offset > this.initialOffset) {
                // characters were added after completion was activated
                int shift = offset - this.initialOffset;
                textEdit.getRange().getEnd().setCharacter(textEdit.getRange().getEnd().getCharacter() + shift);
            }
            { // workaround https://github.com/Microsoft/vscode/issues/17036
                Position start = textEdit.getRange().getStart();
                Position end = textEdit.getRange().getEnd();
                if (start.getLine() > end.getLine() || (start.getLine() == end.getLine() && start.getCharacter() > end.getCharacter())) {
                    textEdit.getRange().setEnd(start);
                    textEdit.getRange().setStart(end);
                }
            }
            { // allow completion items to be wrong with a too wide range
                Position documentEnd = LSPIJUtils.toPosition(document.getTextLength(), document);
                Position textEditEnd = textEdit.getRange().getEnd();
                if (documentEnd.getLine() < textEditEnd.getLine()
                        || (documentEnd.getLine() == textEditEnd.getLine() && documentEnd.getCharacter() < textEditEnd.getCharacter())) {
                    textEdit.getRange().setEnd(documentEnd);
                }
            }

            if (insertText != null) {
                // try to reuse existing characters after completion location
                int shift = offset - this.bestOffset;
                int commonSize = 0;
                while (commonSize < insertText.length() - shift
                        && document.getTextLength() > offset + commonSize
                        && document.getText().charAt(this.bestOffset + shift + commonSize) == insertText.charAt(commonSize + shift)) {
                    commonSize++;
                }
                textEdit.getRange().getEnd().setCharacter(textEdit.getRange().getEnd().getCharacter() + commonSize);
            }

            List<TextEdit> additionalEdits = item.getAdditionalTextEdits();
            if (additionalEdits != null && !additionalEdits.isEmpty()) {
                List<TextEdit> allEdits = new ArrayList<>();
                allEdits.add(textEdit);
                allEdits.addAll(additionalEdits);
                LSPIJUtils.applyEdits(editor, document, allEdits);
            } else {
                LSPIJUtils.applyEdits(editor, document, Collections.singletonList(textEdit));
            }

            // Execute custom command of the completion item if needed
            Command command = item.getCommand();
            if (command != null) {
                executeCustomCommand(command, LSPIJUtils.toUri(document));
            }
        } catch (RuntimeException ex) {
            LOGGER.warn(ex.getLocalizedMessage(), ex);
        }
    }


    /**
     * Execute custom command of the completion item.
     *
     * @param command
     * @param documentUri
     */
    private void executeCustomCommand(@NotNull Command command, URI documentUri) {
        Project project = editor.getProject();
        // Execute custom command of the completion item.
        LanguageServiceAccessor.getInstance(project)
                .resolveServerDefinition(languageServer.getServer()).map(definition -> definition.id)
                .ifPresent(id -> {
                    CommandExecutor.executeCommand(project, command, documentUri, id);
                });

    }

    public Range getTextEditRange() {
        if (item.getTextEdit().isLeft()) {
            return item.getTextEdit().getLeft().getRange();
        } else {
            // here providing insert range, currently do not know if insert or replace is requested
            return item.getTextEdit().getRight().getInsert();
        }
    }

    public CompletionItem getItem() {
        return item;
    }

    /**
     * Return the result of the resolved LSP variable and null otherwise.
     *
     * @param variableName the variable name to resolve.
     * @return the result of the resolved LSP variable and null otherwise.
     */
    private @Nullable String getVariableValue(String variableName) {
        Document document = editor.getDocument();
        switch (variableName) {
            case TM_FILENAME_BASE:
                String fileName = LSPIJUtils.getFile(document).getNameWithoutExtension();
                return fileName != null ? fileName : ""; //$NON-NLS-1$
            case TM_FILENAME:
                return LSPIJUtils.getFile(document).getName();
            case TM_FILEPATH:
                return LSPIJUtils.getFile(document).getPath();
            case TM_DIRECTORY:
                return LSPIJUtils.getFile(document).getParent().getPath();
            case TM_LINE_INDEX:
                int lineIndex = getTextEditRange().getStart().getLine();
                return Integer.toString(lineIndex);
            case TM_LINE_NUMBER:
                int lineNumber = getTextEditRange().getStart().getLine();
                return Integer.toString(lineNumber + 1);
            case TM_CURRENT_LINE:
                int currentLineIndex = getTextEditRange().getStart().getLine();
                try {
                    int lineOffsetStart = document.getLineStartOffset(currentLineIndex);
                    int lineOffsetEnd = document.getLineEndOffset(currentLineIndex);
                    String line = document.getText(new TextRange(lineOffsetStart, lineOffsetEnd));
                    return line;
                } catch (RuntimeException e) {
                    LOGGER.warn(e.getMessage(), e);
                    return ""; //$NON-NLS-1$
                }
            case TM_SELECTED_TEXT:
                Range selectedRange = getTextEditRange();
                try {
                    int startOffset = LSPIJUtils.toOffset(selectedRange.getStart(), document);
                    int endOffset = LSPIJUtils.toOffset(selectedRange.getEnd(), document);
                    String selectedText = document.getText(new TextRange(startOffset, endOffset));
                    return selectedText;
                } catch (RuntimeException e) {
                    LOGGER.warn(e.getMessage(), e);
                    return ""; //$NON-NLS-1$
                }
            case TM_CURRENT_WORD:
                return ""; //$NON-NLS-1$
            default:
                return null;
        }
    }

    public MarkupContent getDocumentation() {
        if (item.getDocumentation() == null && supportResolveCompletion) {
            try {
                CompletionItem resolved = languageServer.getServer()
                        .getTextDocumentService()
                        .resolveCompletionItem(item)
                        .get(1000, TimeUnit.MILLISECONDS);
                if (resolved != null) {
                    item.setDocumentation(resolved.getDocumentation());
                }
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof CancellationException)) {
                    LOGGER.warn(e.getLocalizedMessage(), e);
                }
            } catch (TimeoutException e) {
                LOGGER.warn(e.getLocalizedMessage(), e);
            } catch (InterruptedException e) {
                LOGGER.warn(e.getLocalizedMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
        return getDocumentation(item.getDocumentation());
    }

    private static MarkupContent getDocumentation(Either<String, MarkupContent> documentation) {
        if (documentation == null) {
            return null;
        }
        if (documentation.isLeft()) {
            String content = documentation.getLeft();
            return new MarkupContent(content, MarkupKind.PLAINTEXT);
        }
        return documentation.getRight();
    }
}
