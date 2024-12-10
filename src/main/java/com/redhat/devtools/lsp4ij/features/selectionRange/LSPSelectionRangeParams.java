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
package com.redhat.devtools.lsp4ij.features.selectionRange;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SelectionRangeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.util.List;

/**
 * LSP selection range parameters which hosts the offset where selection range has been triggered.
 */
public class LSPSelectionRangeParams extends SelectionRangeParams {

    // Use transient to avoid serializing the fields when GSON will be processed
    private transient final int offset;

    public LSPSelectionRangeParams(TextDocumentIdentifier textDocument, List<Position> positions, int offset) {
        super.setTextDocument(textDocument);
        setPositions(positions);
        this.offset = offset;
    }

    public int getOffset() {
        return offset;
    }
}
