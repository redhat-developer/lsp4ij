/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.rename;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Provider which returns the 'prepare rename' (text range and placeholder)
 * by using token range strategy (without consuming 'textDocument/prepareRename').
 */
class DefaultPrepareRenameResultProvider implements Function<LanguageServerItem, PrepareRenameResultData> {

    private final LSPPrepareRenameParams prepareRenameParams;

    private TextRange textRange;

    private String placeholder;

    public DefaultPrepareRenameResultProvider(@NotNull LSPPrepareRenameParams prepareRenameParams) {
        this.prepareRenameParams = prepareRenameParams;
    }

    @Nullable
    @Override
    public PrepareRenameResultData apply(LanguageServerItem languageServerItem) {
        if (textRange == null) {
            getTextRange();
        }
        if (textRange == null) {
            // Invalid text range
            // ex: the rename is done in spaces or an empty file
            return null;
        }
        if (placeholder == null) {
            placeholder = getDocument().getText(textRange);
        }
        return new PrepareRenameResultData(textRange, placeholder, languageServerItem);
    }

    public Document getDocument() {
        return prepareRenameParams.getDocument();
    }

    public TextRange getTextRange() {
        if (textRange == null) {
            textRange = LSPIJUtils.getTokenRange(getDocument(), prepareRenameParams.getOffset());
        }
        return textRange;
    }

}
