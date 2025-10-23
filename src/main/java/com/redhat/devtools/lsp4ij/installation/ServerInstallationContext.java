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
package com.redhat.devtools.lsp4ij.installation;

import org.jetbrains.annotations.NotNull;

/**
 * Context information used during the installation process of a server.
 * <p>
 * This class encapsulates options such as whether the installation should be forced
 * and whether the server should be started automatically after installation.
 */
public class ServerInstallationContext {

    private boolean forceInstall;
    private boolean startServerAfterInstallation;

    /**
     * Creates a new {@code ServerInstallationContext} with default settings:
     * <ul>
     *     <li>{@code forceInstall} set to {@code false}</li>
     *     <li>{@code startServerAfterInstallation} set to {@code true}</li>
     * </ul>
     */
    public ServerInstallationContext() {
        setForceInstall(false);
        setStartServerAfterInstallation(true);
    }

    /**
     * Returns whether the installation should be forced.
     * <p>
     * If {@code true}, the installation process will proceed even if the server
     * appears to be already installed.
     *
     * @return {@code true} if installation should be forced, {@code false} otherwise.
     */
    public boolean isForceInstall() {
        return forceInstall;
    }

    /**
     * Sets whether the installation should be forced.
     *
     * @param forceInstall {@code true} to force installation, {@code false} otherwise.
     * @return this {@code ServerInstallationContext} instance for chaining.
     */
    public @NotNull ServerInstallationContext setForceInstall(boolean forceInstall) {
        this.forceInstall = forceInstall;
        return this;
    }

    /**
     * Returns whether the server must be started automatically after installation.
     *
     * @return {@code true} if the server should start after installation, {@code false} otherwise.
     */
    public boolean isStartServerAfterInstallation() {
        return startServerAfterInstallation;
    }

    /**
     * Sets whether the server must be started automatically after installation.
     *
     * @param startServerAfterInstallation {@code true} to start the server after installation, {@code false} otherwise.
     * @return this {@code ServerInstallationContext} instance for chaining.
     */
    public @NotNull ServerInstallationContext setStartServerAfterInstallation(boolean startServerAfterInstallation) {
        this.startServerAfterInstallation = startServerAfterInstallation;
        return this;
    }
}
