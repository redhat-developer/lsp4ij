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

import com.redhat.devtools.lsp4ij.LanguageServerItem;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.util.List;
import java.util.Objects;

/**
 * Rename parameters which hosts document and offset.
 */
class LSPRenameParams extends RenameParams {

    // Use transient to avoid serializing the field when GSON will be processed
    private transient final List<LanguageServerItem> languageServers;

    public LSPRenameParams(TextDocumentIdentifier textDocument, Position position, List<LanguageServerItem> languageServers) {
        super.setTextDocument(textDocument);
        super.setPosition(position);
        this.languageServers = languageServers;
    }

    public List<LanguageServerItem> getLanguageServers() {
        return languageServers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LSPRenameParams that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(languageServers, that.languageServers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), languageServers);
    }
}
