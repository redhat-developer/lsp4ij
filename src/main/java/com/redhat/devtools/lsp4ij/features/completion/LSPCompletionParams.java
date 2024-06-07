/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.completion;

import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jetbrains.annotations.Nullable;

/**
 * LSP completion parameters which hosts the offset where completion has been triggered.
 */
public class LSPCompletionParams extends CompletionParams {

    // Use transient to avoid serializing the fields when GSON will be processed
    private transient final int offset;

    private transient final String completionChar;
    private final transient boolean autoPopup;

    public LSPCompletionParams(TextDocumentIdentifier textDocument, Position position, int offset, @Nullable String completionChar, boolean autoPopup) {
        super.setTextDocument(textDocument);
        super.setPosition(position);
        this.offset = offset;
        this.completionChar = completionChar;
        this.autoPopup = autoPopup;
    }

    public int getOffset() {
        return offset;
    }

    public String getCompletionChar() {
        return completionChar;
    }

    public boolean isAutoPopup() {
        return autoPopup;
    }
}
