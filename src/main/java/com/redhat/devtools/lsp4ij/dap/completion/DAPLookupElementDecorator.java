/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.completion;

import com.intellij.codeInsight.completion.CompletionInitializationContext;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.CompletionItem;
import org.eclipse.lsp4j.debug.CompletionItemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

import static com.redhat.devtools.lsp4ij.internal.CompletionUtils.computePrefixStartFromInsertText;

/**
 * Debug Adapter Protocol (DAP) lookup element decorator.
 */
public class DAPLookupElementDecorator extends LookupElementDecorator<LookupElement> {

    private final @NotNull CompletionItem item;
    private final int completionOffset;

    protected DAPLookupElementDecorator(@NotNull LookupElement delegate,
                                        @NotNull CompletionItem item,
                                        int completionOffset) {
        super(delegate);
        this.item = item;
        this.completionOffset = completionOffset;
    }

    @Override
    public void renderElement(@NotNull LookupElementPresentation presentation) {
        presentation.setItemText(item.getLabel());
        presentation.setItemTextBold(this.isBold(item));
        presentation.setStrikeout(this.isStrikeout(item));
        presentation.setTailText(this.getTailText(item), true);
        presentation.setTypeText(this.getTypeText(item));
        presentation.setIcon(this.getIcon(item.getType()));
        presentation.setTypeGrayed(true);
    }

    @Override
    public void handleInsert(@NotNull InsertionContext context) {
        WriteAction.run(() -> handleInsertInWriteAction(context));
    }

    private void handleInsertInWriteAction(@NotNull InsertionContext context) {
        var editor = context.getEditor();
        final int oldCaretOffset = editor.getCaretModel().getOffset();
        var document = context.getDocument();

        int startOffset = getStartOffset(document);
        int length = getLength(context);
        int endOffset = length + startOffset;
        String newText = getNewtText();

        // Update document with the new completion item text to insert
        if (endOffset <= 0) {
            document.insertString(startOffset, newText);
        } else {
            document.replaceString(startOffset, endOffset, newText);
        }

        // Update caret offset / selection (if needed)
        Integer selectionStart = item.getSelectionStart();
        if (selectionStart != null) {
            // Update selection
            selectionStart += startOffset;
            Integer selectionLength = item.getSelectionLength();
            int selectionEnd = selectionStart + Objects.requireNonNullElse(selectionLength, 0);
            editor.getSelectionModel().setSelection(selectionStart, selectionEnd);

            // Update caret offset
            if (oldCaretOffset != selectionEnd) {
                editor.getCaretModel().moveToOffset(selectionEnd);
            }

        } else {
            // No selection, update caret offset only
            int newCaretOffset = editor.getCaretModel().getOffset() + newText.length() - length;
            if (oldCaretOffset != newCaretOffset) {
                editor.getCaretModel().moveToOffset(newCaretOffset);
            }
        }
    }

    private int getStartOffset(@NotNull Document document) {
        Integer start = item.getStart();
        if (start != null) {
            return start;
        }
        Integer prefixStartOffset = computePrefixStartFromInsertText(document, null, completionOffset, getNewtText());
        return Objects.requireNonNullElse(prefixStartOffset, completionOffset);
    }

    private int getLength(@NotNull InsertionContext context) {
        int selectionEndOffset = context.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
        int endOffset = selectionEndOffset - completionOffset;
        Integer length = item.getLength();
        if (length != null) {
            endOffset += length;
        }
        return endOffset;
    }

    private @NotNull String getNewtText() {
        String newText = item.getText();
        if (StringUtils.isBlank(newText)) {
            return item.getLabel();
        }
        return newText;
    }

    private boolean isBold(@NotNull CompletionItem item) {
        return false;
    }

    private boolean isStrikeout(@NotNull CompletionItem item) {
        return false;
    }

    private @Nullable String getTailText(@NotNull CompletionItem item) {
        return null;
    }

    private @Nullable String getTypeText(@NotNull CompletionItem item) {
        return item.getDetail();
    }

    private @Nullable Icon getIcon(@Nullable CompletionItemType type) {
        if (type == null) {
            return null;
        }

        return switch (type) {
            case CLASS -> AllIcons.Nodes.Class;
            case CUSTOMCOLOR, COLOR -> AllIcons.Nodes.EmptyNode;
            case CONSTRUCTOR -> AllIcons.Nodes.ClassInitializer;
            case ENUM -> AllIcons.Nodes.Enum;
            case FIELD -> AllIcons.Nodes.Field;
            case FILE -> AllIcons.FileTypes.Any_type;
            case FUNCTION -> AllIcons.Nodes.Function;
            case INTERFACE -> AllIcons.Nodes.Interface;
            case KEYWORD -> AllIcons.Nodes.Constant;
            case METHOD -> AllIcons.Nodes.Method;
            case MODULE -> AllIcons.Nodes.Module;
            case PROPERTY -> AllIcons.Nodes.Property;
            case SNIPPET -> AllIcons.Nodes.Template;
            case TEXT -> AllIcons.Nodes.Word;
            case UNIT -> AllIcons.Nodes.Test;
            case VALUE, REFERENCE, VARIABLE -> AllIcons.Nodes.Variable;
            default -> AllIcons.Nodes.EmptyNode;
        };
    }

}
