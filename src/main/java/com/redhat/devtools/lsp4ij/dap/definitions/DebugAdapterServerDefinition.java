/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.definitions;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.configurations.DebuggableFile;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileNamePatternMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerFileTypeMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerLanguageMapping;
import com.redhat.devtools.lsp4ij.server.definition.ServerMapping;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Base class for defining a debug adapter server.
 * <p>
 * This class provides common properties and methods for managing debug adapter servers,
 * including server identification, display information, and debugging capabilities.
 * It also defines how to determine if a file is debuggable based on server-specific mappings.
 * <p>
 * Subclasses must implement methods to provide server-specific mappings and a factory
 * for creating debug adapter descriptors.
 *
 * <p>Key features:</p>
 * <ul>
 *     <li>Defines a unique server ID and name.</li>
 *     <li>Determines if a file is debuggable based on mappings (file type, filename pattern, language).</li>
 *     <li>Provides a factory for creating debug adapter descriptors.</li>
 * </ul>
 *
 * @see DebugAdapterDescriptorFactory
 * @see ServerMapping
 */
public abstract class DebugAdapterServerDefinition implements DebuggableFile {

    private final @NotNull String id;
    private @NotNull String name;
    private @NotNull DebugAdapterDescriptorFactory factory;

    /**
     * Creates a new debug adapter server definition with a given ID and name.
     *
     * @param id   the unique identifier of the debug adapter server (must not be null)
     * @param name the display name of the debug adapter server (must not be null)
     */
    protected DebugAdapterServerDefinition(@NotNull String id, @NotNull String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Returns the unique identifier of the debug adapter server.
     *
     * @return the server's unique ID
     */
    public @NotNull String getId() {
        return id;
    }

    /**
     * Returns the display name of the debug adapter server.
     *
     * @return the server's display name
     */
    public @NotNull String getName() {
        return name;
    }

    /**
     * Sets a new display name for the debug adapter server.
     *
     * @param name the new display name (must not be null)
     */
    protected void setName(@NotNull String name) {
        this.name = name;
    }

    /**
     * Returns the debug adapter server display name.
     *
     * @return the debug adapter server display name
     */
    @NotNull
    public String getDisplayName() {
        return name;
    }

    /**
     * Returns the description of the debug adapter server.
     *
     * <p>Subclasses may override this method to provide a meaningful description.</p>
     *
     * @return the server description, or {@code null} if not defined
     */
    public String getDescription() {
        return null;
    }

    /**
     * Returns the icon associated with the debug adapter server.
     *
     * @return an {@link Icon} representing the server
     */
    public Icon getIcon() {
        return AllIcons.Webreferences.Server;
    }

    @Override
    public boolean isDebuggableFile(@NotNull VirtualFile file,
                                    @NotNull Project project) {
        Language language = null;
        for (var mapping : getServerMappings()) {
            if (mapping instanceof ServerFileTypeMapping s) {
                if (file.getFileType().equals(s.getFileType())) {
                    return true;
                }
            } else if (mapping instanceof ServerFileNamePatternMapping s) {
                String filename = file.getName();
                for (var matcher : s.getFileNameMatchers()) {
                    if (matcher.acceptsCharSequence(filename)) {
                        return true;
                    }
                }
            } else if (mapping instanceof ServerLanguageMapping s) {
                if (language == null) {
                    language = LSPIJUtils.getFileLanguage(file, project);
                }
                if (s.getLanguage().equals(language)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the {@link DebugAdapterDescriptorFactory} associated with this server definition.
     *
     * <p>If the factory has not been initialized, it is created and linked to this server definition.</p>
     *
     * @return the {@link DebugAdapterDescriptorFactory} instance
     */
    @NotNull
    public final DebugAdapterDescriptorFactory getFactory() {
        if (factory == null) {
            factory = createFactory();
            factory.setServerDefinition(this);
        }
        return factory;
    }

    /**
     * Creates a new {@link DebugAdapterDescriptorFactory} instance.
     *
     * <p>Subclasses must implement this method to provide their own factory implementation.</p>
     *
     * @return a new instance of {@link DebugAdapterDescriptorFactory}
     */
    protected abstract @NotNull DebugAdapterDescriptorFactory createFactory();

    /**
     * Returns the list of server mappings used to determine debuggable files.
     *
     * <p>Mappings may define supported file types, filename patterns, or programming languages.</p>
     *
     * @return a list of {@link ServerMapping} instances
     */
    protected abstract List<ServerMapping> getServerMappings();
}
