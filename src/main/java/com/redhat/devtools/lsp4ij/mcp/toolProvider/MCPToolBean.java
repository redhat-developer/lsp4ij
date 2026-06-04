/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.mcp.toolProvider;

import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.serviceContainer.BaseKeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension point bean for MCP tools.
 *
 * <p>Defines the metadata for an MCP tool registered via plugin.xml:</p>
 * <pre>{@code
 * <extensions defaultExtensionNs="com.redhat.devtools.lsp4ij">
 *     <mcpTool
 *         name="lsp-listServers"
 *         description="List all started language servers"
 *         implementation="com.redhat.devtools.lsp4ij.mcp.tools.ListLanguageServersTool"/>
 * </extensions>
 * }</pre>
 */
public class MCPToolBean extends BaseKeyedLazyInstance<MCPTool> {

    /**
     * Unique name of the tool (e.g., "lsp-listServers").
     * This is used as the key for the tool registration.
     */
    @Attribute("name")
    @RequiredElement
    public String name;

    /**
     * Human-readable description of what the tool does.
     */
    @Attribute("description")
    @RequiredElement
    public String description;

    /**
     * Implementation class name.
     */
    @Attribute("implementation")
    @RequiredElement
    public String implementation;

    /**
     * Get the tool name (used as the key).
     */
    @NotNull
    public String getName() {
        return name;
    }

    /**
     * Get the tool description.
     */
    @NotNull
    public String getDescription() {
        return description;
    }

    @Override
    protected @Nullable String getImplementationClassName() {
        return implementation;
    }
}
