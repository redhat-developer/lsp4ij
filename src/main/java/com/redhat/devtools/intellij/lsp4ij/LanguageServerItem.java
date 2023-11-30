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
package com.redhat.devtools.intellij.lsp4ij;

import org.eclipse.lsp4j.services.LanguageServer;

/**
 * Item which stores the initialized LSP4j language server and the language server wrapper.
 */
public class LanguageServerItem {

    private final LanguageServerWrapper serverWrapper;
    private final LanguageServer server;

    public LanguageServerItem(LanguageServer server, LanguageServerWrapper serverWrapper) {
        this.server = server;
        this.serverWrapper = serverWrapper;
    }

    /**
     * Returns the LSP4j language server.
     *
     * @return  the LSP4j language server.
     */
    public LanguageServer getServer() {
        return server;
    }

    /**
     * Returns the language server wrapper.
     *
     * @return  the language server wrapper.
     */
    public LanguageServerWrapper getServerWrapper() {
        return serverWrapper;
    }

}