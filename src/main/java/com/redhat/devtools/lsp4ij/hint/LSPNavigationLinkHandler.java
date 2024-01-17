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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles tooltip links in format {@code #lsp-navigation/file_path:startLine;startChar;endLine;endChar}.
 * On a click opens specified file in an editor and positions caret to the given offset.
 *
 * <p>
 *     This handler looks like {@link com.intellij.codeInsight.hint.NavigationLinkHandler} but as LSP works with position (line, character)
 *     instead of offset, we provide this handler to avoid resolving offset from LSP position when
 *     IntelliJ annotation is created with the tooltip.
 * </p>
 */
public class LSPNavigationLinkHandler extends TooltipLinkHandler  {

    private static final Logger LOGGER = LoggerFactory.getLogger(LSPNavigationLinkHandler.class);//$NON-NLS-1$

    private static final String PREFIX = "#lsp-navigation/";
    public static final String POS_SEPARATOR = ";";

    @Override
    public boolean handleLink(@NotNull String refSuffix, @NotNull Editor editor) {
        int pos = refSuffix.lastIndexOf(':');
        if (pos <= 0 || pos == refSuffix.length() - 1) {
            LOGGER.info("Malformed suffix: " + refSuffix);
            return true;
        }

        String uri = refSuffix.substring(0, pos);
        Range range = toRange(refSuffix.substring(pos + 1));
        Location location = new Location();
        location.setUri(uri);
        if (range != null) {
            location.setRange(range);
        }
        return LSPIJUtils.openInEditor(location, editor.getProject());
    }

    /**
     * Returns the LSP navigation url from the given location.
     *
     * <p>
     *     {@code #lsp-navigation/file_path:startLine;startChar;endLine;endChar}
     * </p>
     *
     * @param location the LSP location.
     * @return the LSP navigation url from the given location.
     */
    public static String toNavigationUrl(@NotNull Location location) {
        StringBuilder url = new StringBuilder(PREFIX);
        url.append(location.getUri());
        url.append(":");
        if (location.getRange() != null) {
            toString(location.getRange(), url);
        }
        return url.toString();
    }

    /**
     * Serialize LSP range used in the LSP location Url.
     * @param range the LSP range.
     * @param result
     */
    private static void toString(@NotNull Range range, StringBuilder result) {
            result.append(range.getStart().getLine());
            result.append(POS_SEPARATOR);
            result.append(range.getStart().getCharacter());
            result.append(POS_SEPARATOR);
            result.append(range.getEnd().getLine());
            result.append(POS_SEPARATOR);
            result.append(range.getEnd().getCharacter());
    }

    private static Range toRange(String rangeString) {
        if (rangeString.isEmpty()) {
            return null;
        }
        String[] positions = rangeString.split(POS_SEPARATOR);
        Position start = new Position(toInt(0, positions), toInt(1, positions));
        Position end = new Position(toInt(2, positions), toInt(3, positions));
        return new Range(start, end);
    }

    private static int toInt(int index, String[] positions) {
        return Integer.valueOf(positions[index]);
    }
}
