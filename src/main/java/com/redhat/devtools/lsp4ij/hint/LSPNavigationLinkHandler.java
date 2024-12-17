/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.hint;

import com.intellij.codeInsight.highlighting.TooltipLinkHandler;
import com.intellij.openapi.editor.Editor;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.redhat.devtools.lsp4ij.LSPIJUtils.HASH_SEPARATOR;

/**
 * Handles tooltip links in format {@code #lsp-navigation/file_path:startLine;startChar;endLine;endChar}.
 * On a click opens specified file in an editor and positions caret to the given offset.
 *
 * <p>
 * This handler looks like {@link com.intellij.codeInsight.hint.NavigationLinkHandler} but as LSP works with position (line, character)
 * instead of offset, we provide this handler to avoid resolving offset from LSP position when
 * IntelliJ annotation is created with the tooltip.
 * </p>
 */
public class LSPNavigationLinkHandler extends TooltipLinkHandler {

    private static final String PREFIX = "#lsp-navigation/";

    @Override
    public boolean handleLink(@NotNull String fileUrl,
                              @NotNull Editor editor) {
        return LSPIJUtils.openInEditor(fileUrl, null, true, true, null, editor.getProject());
    }

    /**
     * Returns the LSP navigation url from the given location.
     *
     * <p>
     * {@code #lsp-navigation/file_path:startLine;startChar;endLine;endChar}
     * </p>
     *
     * @param location the LSP location.
     * @return the LSP navigation url from the given location.
     */
    public static String toNavigationUrl(@NotNull Location location) {
        StringBuilder url = new StringBuilder(PREFIX);
        url.append(location.getUri());
        appendStartPositionIfNeeded(location.getRange(), url);
        return url.toString();
    }

    /**
     * Serialize LSP range used in the LSP location Url.
     *
     * @param range  the LSP range.
     * @param result
     */
    private static void appendStartPositionIfNeeded(@Nullable Range range, StringBuilder result) {
        Position start = range != null ? range.getStart() : null;
        if (start == null) {
            return;
        }
        result.append(HASH_SEPARATOR);
        result.append("L");
        result.append(start.getLine());
        result.append(":");
        result.append(start.getCharacter());
    }

}
