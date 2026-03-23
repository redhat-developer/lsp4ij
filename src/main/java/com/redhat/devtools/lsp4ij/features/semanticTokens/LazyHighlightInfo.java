/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * FalsePattern - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeHighlighting.RainbowHighlighter.RAINBOW_ELEMENT;

public record LazyHighlightInfo(int end, TextAttributesKey colorKey) {

    @FunctionalInterface
    public interface Consumer {
        void accept(int start, int end, TextAttributesKey colorKey);
    }

    public @Nullable HighlightInfo resolve(int start) {
        return resolve(start, end, colorKey);
    }

    /**
     * Creates a {@link HighlightInfo} for the given range and color key.
     *
     * <p>Returns {@code null} (instead of throwing {@link IllegalArgumentException}) when the
     * range is invalid. This can happen when the document is mutated between the moment semantic
     * tokens are received from the LSP server and the moment IntelliJ applies the highlights:
     * a concurrent edit may shift or invalidate offsets, producing a start ≥ end situation.
     * Returning {@code null} is safe here — the highlight is simply skipped for this pass, and
     * the next highlighting pass will request fresh tokens with correct offsets.</p>
     *
     * @param start    the start offset (inclusive)
     * @param end      the end offset (exclusive)
     * @param colorKey the text attributes key to apply
     * @return a {@link HighlightInfo}, or {@code null} if the range is invalid
     */
    public static @Nullable HighlightInfo resolve(int start, int end, @NotNull TextAttributesKey colorKey) {
        // Guard against inverted or zero-length ranges caused by stale/desynchronised
        // semantic token offsets (document edited while LSP response was in flight).
        if (start < 0 || start >= end) {
            return null;
        }
        return HighlightInfo
                .newHighlightInfo(RAINBOW_ELEMENT)
                .range(start, end)
                .textAttributes(colorKey)
                .create();
    }
}