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
package com.redhat.devtools.lsp4ij.server.definition;

import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.LanguageServerFactory;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for Language server definition.
 */
public abstract class LanguageServerDefinition implements LanguageServerFactory {

    private static final int DEFAULT_LAST_DOCUMENTED_DISCONNECTED_TIMEOUT = 5;

    public final @NotNull
    String id;
    public final @NotNull
    String label;
    public final boolean isSingleton;
    public final @NotNull
    Map<Language, String> languageIdMappings;
    public final String description;
    public final int lastDocumentDisconnectedTimeout;
    private boolean enabled;

    public final boolean supportsLightEdit;

    public LanguageServerDefinition(@NotNull String id, @NotNull String label, String description, boolean isSingleton, Integer lastDocumentDisconnectedTimeout, boolean supportsLightEdit) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.isSingleton = isSingleton;
        this.lastDocumentDisconnectedTimeout = lastDocumentDisconnectedTimeout != null && lastDocumentDisconnectedTimeout > 0 ? lastDocumentDisconnectedTimeout : DEFAULT_LAST_DOCUMENTED_DISCONNECTED_TIMEOUT;
        this.languageIdMappings = new ConcurrentHashMap<>();
        this.supportsLightEdit = supportsLightEdit;
        setEnabled(true);
    }


    /**
     * Returns true if the language server definition is enabled and false otherwise.
     *
     * @return true if the language server definition is enabled and false otherwise.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set enabled the language server definition.
     *
     * @param enabled enabled the language server definition.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void registerAssociation(@NotNull Language language, @NotNull String serverId) {
        this.languageIdMappings.put(language, serverId);
    }

    @NotNull
    public String getDisplayName() {
        return label != null ? label : id;
    }

    @Override
    public @NotNull LanguageClientImpl createLanguageClient(@NotNull Project project) {
        return new LanguageClientImpl(project);
    }

    @Override
    public @NotNull Class<? extends LanguageServer> getServerInterface() {
        return LanguageServer.class;
    }

    public <S extends LanguageServer> Launcher.Builder<S> createLauncherBuilder() {
        return new Launcher.Builder<>();
    }

    public boolean supportsCurrentEditMode(@NotNull Project project) {
        return project != null && (supportsLightEdit || !LightEdit.owns(project));
    }

    public Icon getIcon() {
        return AllIcons.Webreferences.Server;
    }
}
