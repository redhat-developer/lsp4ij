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
package com.redhat.devtools.lsp4ij.mcp.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MCP server settings (application-level).
 * Persists port and trace level.
 */
@Service(Service.Level.APP)
@State(
        name = "MCPSettings",
        storages = @Storage("mcp.xml")
)
public final class MCPSettings implements PersistentStateComponent<MCPSettings> {

    private int port = 9339;
    private MCPTrace trace = MCPTrace.off;

    public static MCPSettings getInstance() {
        return ApplicationManager.getApplication().getService(MCPSettings.class);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public MCPTrace getTrace() {
        return trace;
    }

    public void setTrace(MCPTrace trace) {
        this.trace = trace;
    }

    @Nullable
    @Override
    public MCPSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull MCPSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
