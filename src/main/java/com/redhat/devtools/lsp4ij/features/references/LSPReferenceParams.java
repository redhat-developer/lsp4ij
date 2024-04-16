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
package com.redhat.devtools.lsp4ij.features.references;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

/**
 * LSP reference parameters which hosts the offset where reference has been triggered.
 */
public class LSPReferenceParams extends ReferenceParams {

    // Use transient to avoid serializing the fields when GSON will be processed
    private transient final int offset;

    public LSPReferenceParams(TextDocumentIdentifier textDocument, Position position, int offset) {
        super.setTextDocument(textDocument);
        super.setPosition(position);
        this.offset = offset;
        var  context = new ReferenceContext();
        context.setIncludeDeclaration(true);
        setContext(context);
    }

    public int getOffset() {
        return offset;
    }
}
