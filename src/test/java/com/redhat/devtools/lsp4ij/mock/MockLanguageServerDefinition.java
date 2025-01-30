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
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.ClientConfigurationSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link LanguageServerDefinition} implementation to register the mock language server.
 */
public class MockLanguageServerDefinition extends LanguageServerDefinition implements ClientConfigurableLanguageServerDefinition {

    private static final String SERVER_ID = "mock-server-id";

    private final ClientConfigurationSettings clientConfigurationSettings = new ClientConfigurationSettings();

    public MockLanguageServerDefinition() {
        this(SERVER_ID);
    }

    public MockLanguageServerDefinition(String serverId) {
        super(serverId, "name", null, true, 5, true);
    }

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        return new MockConnectionProvider();
    }

    @Override
    @Nullable
    public ClientConfigurationSettings getLanguageServerClientConfiguration() {
        return clientConfigurationSettings;
    }
}
