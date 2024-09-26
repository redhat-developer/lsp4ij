/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

/**
 * Language server factory API.
 */
public interface LanguageServerFactory {

    /**
     * Returns ann instance of a connection provider to connect to the language server.
     *
     * @param project the project.
     *
     * @return an instance of a connection provider to connect to the language server.
     */
    @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project);

    /**
     * Returns an instance of language client.
     *
     * @param project the project.
     *
     * @return an instance of language client.
     */
    @NotNull default LanguageClientImpl createLanguageClient(@NotNull Project project) {
        return new LanguageClientImpl(project);
    }

    /**
     * Returns the language server interface class API.
     *
     * @return the language server interface class API.
     */
    @NotNull default Class<? extends LanguageServer> getServerInterface() {
        return LanguageServer.class;
    }

    @NotNull default LSPClientFeatures createClientFeatures() {
        return new LSPClientFeatures();
    }
}
