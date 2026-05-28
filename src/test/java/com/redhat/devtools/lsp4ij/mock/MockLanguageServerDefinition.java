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
package com.redhat.devtools.lsp4ij.mock;

import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedClientFeatures;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link LanguageServerDefinition} implementation to register the mock language server.
 */
public class MockLanguageServerDefinition extends LanguageServerDefinition implements ClientConfigurableLanguageServerDefinition {

    private static final String SERVER_ID = "mock-server-id";

    private final boolean clientConfigurable;
    private final ClientConfigurationSettings clientConfigurationSettings = new ClientConfigurationSettings();
    private final @NotNull MockLanguageServer server;

    public MockLanguageServerDefinition(boolean clientConfigurable, boolean useServerSingleton) {
        this(SERVER_ID, clientConfigurable, useServerSingleton);
    }

    public MockLanguageServerDefinition(@NotNull String serverId, boolean useServerSingleton) {
        this(serverId, false, useServerSingleton);
    }

    private MockLanguageServerDefinition(@NotNull String serverId, boolean clientConfigurable, boolean useServerSingleton) {
        super(serverId, "name", null, true, 5, true);
        this.clientConfigurable = clientConfigurable;
        this.server = useServerSingleton ? null : new MockLanguageServer(MockLanguageServer::defaultServerCapabilities);
    }

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        return new MockConnectionProvider(server);
    }

    @Override
    @NotNull
    public LSPClientFeatures createClientFeatures() {
        return clientConfigurable ? new UserDefinedClientFeatures() : super.createClientFeatures();
    }

    @Override
    @Nullable
    public ClientConfigurationSettings getLanguageServerClientConfiguration() {
        return clientConfigurable ? clientConfigurationSettings : null;
    }

    public @NotNull MockLanguageServer getServer() {
        return server;
    }
}
