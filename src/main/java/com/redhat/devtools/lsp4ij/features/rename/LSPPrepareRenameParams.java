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
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PrepareRenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * Prepare rename parameters which hosts document and offset.
 */
class LSPPrepareRenameParams extends PrepareRenameParams {

    // Use transient to avoid serializing the fields when GSON will be processed
    private transient final int offset;
    private transient final Document document;

    public LSPPrepareRenameParams(TextDocumentIdentifier textDocument, Position position, int offset, Document document) {
        super.setTextDocument(textDocument);
        super.setPosition(position);
        this.offset = offset;
        this.document = document;
    }

    public int getOffset() {
        return offset;
    }

    public Document getDocument() {
        return document;
    }
}
