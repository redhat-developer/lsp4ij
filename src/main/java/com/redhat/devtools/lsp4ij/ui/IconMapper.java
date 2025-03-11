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
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.ColorIcon;
import com.redhat.devtools.lsp4ij.features.semanticTokens.LSPSemanticTokenType;
import com.redhat.devtools.lsp4ij.features.semanticTokens.LSPSemanticTokenTypes;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.Color;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.intellij.util.PathUtil.getFileExtension;

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

    private IconMapper() {
    }

    /**
     * Maps LSP4J {@link CompletionItemKind} to Intellij Icons
     */
    public static @Nullable Icon getIcon(@Nullable CompletionItem item) {
        if (item == null || item.getKind() == null) {
            return null;
        }
        Icon icon = null;
        if (item.getKind() == CompletionItemKind.File) {
            icon = getFileIcon(item);
        } else if (item.getKind() == CompletionItemKind.Color) {
            icon = getColorIcon(item);
        }
        return icon == null ? getIcon(item.getKind()) : icon;
    }

    private static Icon getFileIcon(CompletionItem item) {
        FileType fileType = getFileType(item.getLabel());
        if (fileType == null) {
            fileType = getFileType(item.getDetail());
        }
        return fileType == null ? null : fileType.getIcon();
    }

    private static FileType getFileType(String fileOrPathName) {
        if (fileOrPathName == null || fileOrPathName.isBlank()) {
            return null;
        }
        String extension = getFileExtension(fileOrPathName);
        if (extension != null) {
            FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension);
            if (!UnknownFileType.INSTANCE.equals(fileType)) {
                return fileType;
            }
        }
        return null;
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
        return colorToIconCache.computeIfAbsent(hexValue.toUpperCase(Locale.ROOT), key -> {
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
            case Struct:
                // TODO: Should this be AllIcons.Nodes.Types?
                return AllIcons.Json.Object;
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
            case Event:
            case Operator:
            case Color:
            default:
                return AllIcons.Nodes.EmptyNode;
        }
    }

    public static @Nullable Icon getIcon(@Nullable SymbolKind kind) {
        if (kind == null) {
            return null;
        }

        switch (kind) {
            case Namespace:
            case Package:
                return AllIcons.Nodes.Package;
            case Constructor:
                return AllIcons.Nodes.ClassInitializer;
            case Method:
                return AllIcons.Nodes.Method;
            case Function:
                return AllIcons.Nodes.Function;
            case EnumMember://No matching icon, IDEA show enum members as fields
            case Field:
                return AllIcons.Nodes.Field;
            case Variable:
                return AllIcons.Nodes.Variable;
            case Object:
            case Struct:
                // TODO: Should this be AllIcons.Nodes.Types?
                return AllIcons.Json.Object;
            case Array:
                return AllIcons.Json.Array;
            case Class:
                return AllIcons.Nodes.Class;
            case Interface:
                return AllIcons.Nodes.Interface;
            case Module:
                return AllIcons.Nodes.Module;
            case Property:
                return AllIcons.Nodes.Property;
            case Enum:
                return AllIcons.Nodes.Enum;
            case File:
                return AllIcons.FileTypes.Any_type;
            case Constant:
                return AllIcons.Nodes.Constant;
            case TypeParameter:
                return AllIcons.Nodes.Parameter;
            //No matching icons, no fallback
            case Event:
            case Operator:
            case String:
            case Number:
            case Boolean:
            case Key:
            case Null:
            default:
                return AllIcons.Nodes.EmptyNode;
        }
    }

    /**
     * Returns the icon for the provided {@link SemanticTokenTypes} value if possible.
     *
     * @param tokenType the icon name as semantic token type value
     * @return the corresponding icon, or null if no icon could be found
     */
    @Nullable
    public static Icon getIcon(@Nullable String tokenType) {
        if (tokenType != null) {
            // If this is for a custom semantic token type that expresses an inheritance relationship, swap it out now
            LSPSemanticTokenType lspSemanticTokenType = LSPSemanticTokenTypes.valueOf(tokenType);
            if ((lspSemanticTokenType != null) && (lspSemanticTokenType.getInheritFrom() != null)) {
                tokenType = lspSemanticTokenType.getInheritFrom();
            }

            switch (tokenType) {
                case SemanticTokenTypes.Namespace:
                    return AllIcons.Nodes.Package;
                case SemanticTokenTypes.Type:
                    return AllIcons.Nodes.Type;
                case SemanticTokenTypes.Class:
                    return AllIcons.Nodes.Class;
                case SemanticTokenTypes.Enum:
                    return AllIcons.Nodes.Enum;
                case SemanticTokenTypes.Interface:
                    return AllIcons.Nodes.Interface;
                case SemanticTokenTypes.Struct:
                    return AllIcons.Nodes.Type;
                case SemanticTokenTypes.TypeParameter:
                    return AllIcons.Nodes.Parameter;
                case SemanticTokenTypes.Parameter:
                    return AllIcons.Nodes.Parameter;
                case SemanticTokenTypes.Variable:
                    return AllIcons.Nodes.Variable;
                case SemanticTokenTypes.Property:
                    return AllIcons.Nodes.Property;
                case SemanticTokenTypes.EnumMember:
                    return AllIcons.Nodes.Field;
                case SemanticTokenTypes.Function:
                    return AllIcons.Nodes.Function;
                case SemanticTokenTypes.Macro:
                    return AllIcons.Nodes.Function;
                case SemanticTokenTypes.Method:
                    return AllIcons.Nodes.Method;
                // Decorators are just annotations
                case SemanticTokenTypes.Decorator:
                    return AllIcons.Nodes.Annotationtype;
                // TODO: SemanticTokenTypes.Event
            }
        }
        return null;
    }

    private static @NotNull Icon load(String iconPath) {
        return IconLoader.getIcon(iconPath, IconMapper.class);
    }
}
