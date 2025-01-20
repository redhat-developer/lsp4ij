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

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.icons.AllIcons;
import org.eclipse.lsp4j.debug.CompletionItem;
import org.eclipse.lsp4j.debug.CompletionItemType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Debug Adapter Protocol (DAP) lookup element decorator.
 */
public class DAPLookupElementDecorator extends LookupElementDecorator<LookupElement> {

    private final @NotNull CompletionItem item;

    protected DAPLookupElementDecorator(@NotNull LookupElement delegate,
                                        @NotNull CompletionItem item) {
        super(delegate);
        this.item = item;
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

private static @Nullable Icon getIcon(@Nullable CompletionItemType type) {
    if (type == null) {
        return null;
    }

    switch (type) {
        case CLASS:
            return AllIcons.Nodes.Class;
        case CUSTOMCOLOR:
        case COLOR:
            return AllIcons.Nodes.EmptyNode;
        case CONSTRUCTOR:
            return AllIcons.Nodes.ClassInitializer;
        case ENUM:
            return AllIcons.Nodes.Enum;
        case FIELD:
            return AllIcons.Nodes.Field;
        case FILE:
            return AllIcons.FileTypes.Any_type;
        case FUNCTION:
            return AllIcons.Nodes.Function;
        case INTERFACE:
            return AllIcons.Nodes.Interface;
        case KEYWORD:
            return AllIcons.Nodes.Constant;
        case METHOD:
            return AllIcons.Nodes.Method;
        case MODULE:
            return AllIcons.Nodes.Module;
        case PROPERTY:
            return AllIcons.Nodes.Property;
        case SNIPPET:
            return AllIcons.Nodes.Template;
        case TEXT:
            return AllIcons.Nodes.Word;
        case UNIT:
            return AllIcons.Nodes.Test;
        case VALUE:
        case REFERENCE:
        case VARIABLE:
            return AllIcons.Nodes.Variable;
        default:
            return AllIcons.Nodes.EmptyNode;
    }
}

}
