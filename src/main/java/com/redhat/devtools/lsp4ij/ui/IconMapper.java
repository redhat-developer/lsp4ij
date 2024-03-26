/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.ColorIcon;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps LSP4J kinds to Intellij Icons. See the <a href="https://jetbrains.design/intellij/resources/icons_list/" target="_blank">JetBrains icon list</a> for reference.
 */
public class IconMapper {

    // Copied from IntelliJ icons. To be removed once the minimal supported version of IDEA is > 232
    // See https://github.com/JetBrains/intellij-community/blob/50157fc8eec4af77f67bd468ada4dff39daa1b88/platform/util/ui/src/com/intellij/icons/ExpUiIcons.java#L226
    // Original light https://github.com/JetBrains/intellij-community/blob/50157fc8eec4af77f67bd468ada4dff39daa1b88/platform/icons/src/expui/fileTypes/text.svg
    // Original dark https://github.com/JetBrains/intellij-community/blob/50157fc8eec4af77f67bd468ada4dff39daa1b88/platform/icons/src/expui/fileTypes/text_dark.svg
    public static final @NotNull Icon Text = load("images/expui/fileTypes/text.svg");

    private static final Map<String, Icon> colorToIconCache = new ConcurrentHashMap<>();

    private static final int ICON_SIZE = 16;

    private IconMapper(){
    }

    /**
     * Maps LSP4J {@link CompletionItemKind} to Intellij Icons
     */
    public static @Nullable Icon getIcon(@Nullable CompletionItem item) {
        if (item == null || item.getKind() == null) {
            return null;
        }
        Icon icon = null;
        if (item.getKind() == CompletionItemKind.Color) {
            icon = getColorIcon(item);
        }
        return icon == null? getIcon(item.getKind()) : icon;
    }

    private static Icon getColorIcon(@NotNull CompletionItem item) {
        //While this method is private, we already know this is a Color kind, no need to check again
        String docString = getDocString(item.getDocumentation());
        String hexValue = null;
        if (docString != null && docString.startsWith("#")) { //$NON-NLS-1$
            hexValue = docString;
        } else if (item.getLabel() != null && item.getLabel().startsWith("#")) { //$NON-NLS-1$
            hexValue = item.getLabel();
        }
        return getColorIcon(hexValue);
    }

    private static @Nullable String getDocString(@Nullable Either<String, MarkupContent> documentation) {
        if (documentation != null) {
            if (documentation.isLeft()) {
                return documentation.getLeft();
            } else { //We ignore the markup content here, as we know this is a color kind
                return documentation.getRight().getValue();
            }
        }
        return null;
    }

    private static Icon getColorIcon(@Nullable String hexValue) {
        if (!isValidHexColor(hexValue)) {
            return null;
        }
        return colorToIconCache.computeIfAbsent(hexValue.toUpperCase(), key -> {
            try {
                Color decodedColor = java.awt.Color.decode(key);
                return new ColorIcon(ICON_SIZE, decodedColor, true);
            } catch (Exception ignored) {
                //ignore error
            }
            return AllIcons.Nodes.EmptyNode;
        });
    }

    private static boolean isValidHexColor(@Nullable String hexValue) {
        return hexValue != null && hexValue.matches("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");
    }

    public static @Nullable Icon getIcon(@Nullable CompletionItemKind kind) {
        if (kind == null) {
            return null;
        }

        switch (kind) {
            case Snippet:
                return AllIcons.Nodes.Template;
            case Text:
                return Text;
            case Constructor:
                return AllIcons.Nodes.ClassInitializer;
            case Method:
                return AllIcons.Nodes.Method;
            case Function:
                return AllIcons.Nodes.Function;
            case EnumMember://No matching icon, IDEA show enum members as fields
            case Field:
                return AllIcons.Nodes.Field;
            case Value: //No matching icon
            case Reference:
            case Variable:
                return AllIcons.Nodes.Variable;
            case Class:
                return AllIcons.Nodes.Class;
            case Interface:
                return AllIcons.Nodes.Interface;
            case Module:
                return AllIcons.Nodes.Module;
            case Property:
                return AllIcons.Nodes.Property;
            case Unit:
                return AllIcons.Nodes.Test;
            case Enum:
                return AllIcons.Nodes.Enum;
            case File:
                return AllIcons.FileTypes.Any_type;
            case Folder:
                return AllIcons.Nodes.Folder;
            case Constant:
            case Keyword:
                return AllIcons.Nodes.Constant;
            case TypeParameter:
                return AllIcons.Nodes.Parameter;
            //No matching icons, no fallback
            case Struct:
            case Event:
            case Operator:
            case Color:
            default:
                return AllIcons.Nodes.EmptyNode;
        }
    }

    private static @NotNull Icon load(String iconPath) {
        return IconLoader.getIcon(iconPath, IconMapper.class);
    }
}
