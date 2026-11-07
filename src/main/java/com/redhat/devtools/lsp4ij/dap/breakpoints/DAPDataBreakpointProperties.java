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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.eclipse.lsp4j.debug.DataBreakpointAccessType;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Debug Adapter Protocol (DAP) data breakpoint properties: the data which the DAP server watches, as its
 * 'dataBreakpointInfo' answer names it, with its description, the access type which stops the program, the hit
 * condition, and the DAP server whose data it is.
 * <p>
 * A data id which the DAP server says can't persist ('canPersist') is valid only in the debug session which gave it:
 * such a breakpoint is bound to the DAP server of that session, which is not saved with the project, and is removed
 * when the session ends.
 */
public class DAPDataBreakpointProperties extends XBreakpointProperties<DAPDataBreakpointProperties> {

    private @NotNull String dataId = "";
    private @NotNull String description = "";
    private @Nullable DataBreakpointAccessType accessType;
    private @NotNull String hitCondition = "";
    private boolean canPersist;
    private @NotNull String serverId = "";
    // The DAP server which gave the data id, while its debug session runs
    private volatile @Nullable IDebugProtocolServer server;

    public DAPDataBreakpointProperties() {
    }

    public DAPDataBreakpointProperties(@NotNull String dataId,
                                       @NotNull String description,
                                       @Nullable DataBreakpointAccessType accessType,
                                       boolean canPersist,
                                       @NotNull String serverId) {
        this.dataId = dataId;
        this.description = description;
        this.accessType = accessType;
        this.canPersist = canPersist;
        this.serverId = serverId;
    }

    /**
     * Returns the id of the data, as the DAP server gave it.
     *
     * @return the id of the data.
     */
    public @NotNull String getDataId() {
        return dataId;
    }

    public void setDataId(@NotNull String dataId) {
        this.dataId = dataId;
    }

    /**
     * Returns the description of the data, as the DAP server gave it, such as {@code Counter.count}.
     *
     * @return the description of the data.
     */
    public @NotNull String getDescription() {
        return description;
    }

    public void setDescription(@NotNull String description) {
        this.description = description;
    }

    /**
     * Returns the access type which stops the program, and null for the DAP server's own.
     *
     * @return the access type which stops the program, and null for the DAP server's own.
     */
    public @Nullable DataBreakpointAccessType getAccessType() {
        return accessType;
    }

    public void setAccessType(@Nullable DataBreakpointAccessType accessType) {
        this.accessType = accessType;
    }

    /**
     * Returns the hit condition, which the DAP server interprets, and an empty string if there is none.
     *
     * @return the hit condition, and an empty string if there is none.
     */
    public @NotNull String getHitCondition() {
        return hitCondition;
    }

    public void setHitCondition(@NotNull String hitCondition) {
        this.hitCondition = hitCondition;
    }

    /**
     * Returns true if the data id stays valid in later debug sessions, and false if it is valid only in the debug
     * session which gave it.
     *
     * @return true if the data id stays valid in later debug sessions and false otherwise.
     */
    public boolean isCanPersist() {
        return canPersist;
    }

    public void setCanPersist(boolean canPersist) {
        this.canPersist = canPersist;
    }

    /**
     * Returns the id of the DAP server configuration whose data it is, which a later debug session of the same
     * configuration sends it to when the data id can persist.
     *
     * @return the id of the DAP server configuration whose data it is.
     */
    public @NotNull String getServerId() {
        return serverId;
    }

    public void setServerId(@NotNull String serverId) {
        this.serverId = serverId;
    }

    /**
     * Returns the DAP server which gave the data id, while its debug session runs, and null otherwise. It is not saved
     * with the project.
     *
     * @return the DAP server which gave the data id, and null once its debug session ended.
     */
    @Transient
    public @Nullable IDebugProtocolServer getServer() {
        return server;
    }

    @Transient
    public void setServer(@Nullable IDebugProtocolServer server) {
        this.server = server;
    }

    @Override
    public @NotNull DAPDataBreakpointProperties getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull DAPDataBreakpointProperties state) {
        dataId = state.dataId;
        description = state.description;
        accessType = state.accessType;
        hitCondition = state.hitCondition;
        canPersist = state.canPersist;
        serverId = state.serverId;
    }
}
