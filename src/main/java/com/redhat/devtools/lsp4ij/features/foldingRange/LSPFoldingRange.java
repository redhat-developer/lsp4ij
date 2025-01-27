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

package com.redhat.devtools.lsp4ij.features.foldingRange;

import org.eclipse.lsp4j.FoldingRange;
import org.jetbrains.annotations.NotNull;

/**
 * Augments {@link FoldingRange} with add a flag denoting whether or not it should be collapsed by default.
 */
class LSPFoldingRange extends FoldingRange {

    private final boolean collapsedByDefault;

    /**
     * Creates a new folding range with the information from the provided folding range and whether or not it should be
     * collapsed by default.
     *
     * @param foldingRange       the original folding range
     * @param collapsedByDefault whether or not the folding range should be collapsed by default
     */
    LSPFoldingRange(@NotNull FoldingRange foldingRange, boolean collapsedByDefault) {
        // Clone the provided folding range
        setStartLine(foldingRange.getStartLine());
        setStartCharacter(foldingRange.getStartCharacter());
        setEndLine(foldingRange.getEndLine());
        setEndCharacter(foldingRange.getEndCharacter());
        setKind(foldingRange.getKind());
        setCollapsedText(foldingRange.getCollapsedText());

        // Denote whether or not it should be collapsed by default
        this.collapsedByDefault = collapsedByDefault;
    }

    /**
     * Determines whether or not the folding range should be collapsed by default.
     *
     * @return true if the folding range should be collapsed by default; otherwise false
     */
    boolean isCollapsedByDefault() {
        return collapsedByDefault;
    }
}
