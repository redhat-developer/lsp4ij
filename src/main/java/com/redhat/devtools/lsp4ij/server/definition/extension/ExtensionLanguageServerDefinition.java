/*******************************************************************************
 * Copyright (c) 2020 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.server.definition.extension;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.redhat.devtools.lsp4ij.LanguageServerEnablementSupport;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * Language server definition coming from the {@link ServerExtensionPointBean server extension point}.
 */
public class ExtensionLanguageServerDefinition extends LanguageServerDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionLanguageServerDefinition.class);

    private final ServerExtensionPointBean extension;

    private Icon icon;

    public ExtensionLanguageServerDefinition(@NotNull ServerExtensionPointBean element) {
        super(element.id, element.getName(), element.getDescription(), element.singleton, element.lastDocumentDisconnectedTimeout, element.supportsLightEdit, false);
        this.extension = element;
        // Enable by default language server from plugin
        super.setEnabled(true, null);
    }

    @Override
    public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
        try {
            return getFactory().createConnectionProvider(project);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Exception occurred while creating an instance of the stream connection provider", e); //$NON-NLS-1$
        }
    }

    @Override
    public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
        LanguageClientImpl languageClient = null;
        try {
            languageClient = getFactory().createLanguageClient(project);
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while creating an instance of the language client", e); //$NON-NLS-1$
        }
        if (languageClient == null) {
            languageClient = super.createLanguageClient(project);
        }
        return languageClient;
    }

    @Override
    public @NotNull LSPClientFeatures createClientFeatures() {
        LSPClientFeatures clientFeatures = null;
        try {
            clientFeatures = getFactory().createClientFeatures();
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while creating an instance of the LSP client features", e); //$NON-NLS-1$
        }
        if (clientFeatures == null) {
            clientFeatures = super.createClientFeatures();
        }
        return clientFeatures;
    }

    @SuppressWarnings("unchecked")
    @Override
    public @NotNull Class<? extends LanguageServer> getServerInterface() {
        Class<? extends LanguageServer> serverInterface = null;
        try {
            serverInterface = getFactory().getServerInterface();
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while getting server interface", e); //$NON-NLS-1$
        }
        if (serverInterface == null) {
            serverInterface = super.getServerInterface();
        }
        return serverInterface;
    }

    @Override
    public boolean isEnabled(@NotNull Project project) {
        try {
            var factory = getFactory();
            if (factory instanceof LanguageServerEnablementSupport languageServerEnablementSupport) {
                return languageServerEnablementSupport.isEnabled(project);
            }
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while getting enabled", e); //$NON-NLS-1$
        }
        return super.isEnabled(project);
    }

    @Override
    public void setEnabled(boolean enabled, @Nullable Project project) {
        try {
            var factory = getFactory();
            if (factory instanceof LanguageServerEnablementSupport languageServerEnablementSupport) {
                languageServerEnablementSupport.setEnabled(enabled, project);
            } else {
                super.setEnabled(enabled, project);
            }
        } catch (Exception e) {
            LOGGER.warn("Exception occurred while setting enabled", e); //$NON-NLS-1$
        }
    }

    private @NotNull LanguageServerFactory getFactory() {
        String serverFactory = extension.getImplementationClassName();
        if (serverFactory == null || serverFactory.isEmpty()) {
            throw new RuntimeException(
                    "Exception occurred while creating an instance of server factory, you have to define server/@factory attribute in the extension point."); //$NON-NLS-1$
        }
        return extension.getInstance();
    }

    @Override
    public Icon getIcon() {
        if (icon == null) {
            icon = findIcon();
        }
        return icon;
    }

    private synchronized Icon findIcon() {
        if (icon != null) {
            return icon;
        }
        if (!StringUtils.isEmpty(extension.icon)) {
            try {
                return IconLoader.findIcon(extension.icon, extension.getPluginDescriptor().getPluginClassLoader());
            } catch (Exception e) {
                LOGGER.error("Error while loading custom server icon for server id='" + extension.id + "'.", e);
            }
        }
        return super.getIcon();
    }

    @Override
    public @NotNull SemanticTokensColorsProvider getSemanticTokensColorsProvider() {
        return super.getSemanticTokensColorsProvider();
    }
}
