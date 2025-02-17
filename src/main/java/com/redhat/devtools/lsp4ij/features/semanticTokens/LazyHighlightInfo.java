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

import static com.intellij.codeHighlighting.RainbowHighlighter.RAINBOW_ELEMENT;

public record LazyHighlightInfo(int end, TextAttributesKey colorKey) {
    @FunctionalInterface
    public interface Consumer {
        void accept(int start, int end, TextAttributesKey colorKey);
    }

    public HighlightInfo resolve(int start) {
        return resolve(start, end, colorKey);
    }

    public static HighlightInfo resolve(int start, int end, TextAttributesKey colorKey) {
        return HighlightInfo
                .newHighlightInfo(RAINBOW_ELEMENT)
                .range(start, end)
                .textAttributes(colorKey)
                .create();
    }
}
