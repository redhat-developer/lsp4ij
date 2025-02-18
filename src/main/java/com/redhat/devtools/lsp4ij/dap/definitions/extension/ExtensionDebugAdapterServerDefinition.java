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
package com.redhat.devtools.lsp4ij.dap.definitions.extension;

import com.intellij.openapi.util.IconLoader;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import com.redhat.devtools.lsp4ij.server.definition.ServerMapping;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.List;

/**
 * Represents a debug adapter server definition for an extension.
 * <p>
 * This class is used to define a custom debug adapter server based on an extension
 * that implements the {@link DebugAdapterServerExtensionPointBean} interface. It allows
 * for the configuration of server mappings and the retrieval of an associated icon
 * for the server, either from a custom source or falling back to a default.
 * </p>
 *
 * <p>Key features:</p>
 * <ul>
 *     <li>Supports loading custom debug server factories from the extension.</li>
 *     <li>Supports custom icons for debug servers, loading from the extension if specified.</li>
 *     <li>Maps server settings using {@link ServerMapping} instances.</li>
 * </ul>
 *
 * @see DebugAdapterServerExtensionPointBean
 * @see ServerMapping
 * @see DebugAdapterDescriptorFactory
 */
public class ExtensionDebugAdapterServerDefinition extends DebugAdapterServerDefinition {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionDebugAdapterServerDefinition.class);

    private final @NotNull DebugAdapterServerExtensionPointBean extension;
    private final @NotNull List<ServerMapping> serverMappings;
    private Icon icon;

    /**
     * Creates an instance of a debug adapter server definition for an extension.
     *
     * @param extension      the extension defining the server configuration (must not be null)
     * @param serverMappings the server mappings for the extension (must not be null)
     */
    public ExtensionDebugAdapterServerDefinition(@NotNull DebugAdapterServerExtensionPointBean extension,
                                                 @NotNull List<ServerMapping> serverMappings) {
        super(extension.getId(), extension.getName());
        this.serverMappings = serverMappings;
        this.extension = extension;
    }

    /**
     * Creates a factory for the debug adapter server based on the extension's configuration.
     *
     * @return the {@link DebugAdapterDescriptorFactory} instance for the server
     * @throws RuntimeException if the extension does not define a valid server factory
     */
    @Override
    protected @NotNull DebugAdapterDescriptorFactory createFactory() {
        String serverFactory = extension.getImplementationClassName();
        if (serverFactory == null || serverFactory.isEmpty()) {
            throw new RuntimeException(
                    "Exception occurred while creating an instance of debug server factory, you have to define server/@factory attribute in the extension point."); //$NON-NLS-1$
        }
        return extension.getInstance();
    }

    /**
     * Returns the list of server mappings defined for the extension.
     *
     * @return a list of {@link ServerMapping} instances associated with this server
     */
    @Override
    protected List<ServerMapping> getServerMappings() {
        return serverMappings;
    }

    /**
     * Returns the icon associated with the debug adapter server.
     * <p>
     * If a custom icon is defined in the extension, it is loaded and returned.
     * If no icon is defined, the default icon is returned.
     * </p>
     *
     * @return the {@link Icon} associated with the server
     */
    @Override
    public Icon getIcon() {
        if (icon == null) {
            icon = findIcon();
        }
        return icon;
    }

    /**
     * Finds the custom icon defined in the extension.
     * <p>
     * This method attempts to load the custom icon from the extension's specified icon path.
     * If loading fails or no icon is defined, it falls back to the default icon.
     * </p>
     *
     * @return the {@link Icon} if found, otherwise the default icon
     */
    private synchronized Icon findIcon() {
        if (icon != null) {
            return icon;
        }
        if (!StringUtils.isEmpty(extension.getIcon())) {
            try {
                return IconLoader.findIcon(extension.getIcon(), extension.getPluginDescriptor().getPluginClassLoader());
            } catch (Exception e) {
                LOGGER.error("Error while loading custom server icon for server id='" + extension.getId() + "'.", e);
            }
        }
        return super.getIcon();
    }
}
