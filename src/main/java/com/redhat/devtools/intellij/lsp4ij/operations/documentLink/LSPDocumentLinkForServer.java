/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
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
package com.redhat.devtools.intellij.lsp4ij.operations.documentLink;

import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.intellij.lsp4ij.LanguageServerWrapper;
import org.eclipse.lsp4j.DocumentLink;

import java.util.Collections;
import java.util.List;

/**
 * LSP document links holder for a file reported by a language server.
 *
 * @author Angelo ZERR
 */
public class LSPDocumentLinkForServer {

    private List<DocumentLink> documentLinks;

    public LSPDocumentLinkForServer(LanguageServerWrapper languageServerWrapper, VirtualFile file) {
    }

    public void update(List<DocumentLink> documentLinks) {
        this.documentLinks = documentLinks;
    }

    public List<DocumentLink> getDocumentLinks() {
        return documentLinks != null ? documentLinks : Collections.emptyList();
    }
}
