/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.client.features;

import org.jetbrains.annotations.NotNull;

/**
 * Represents the configuration feature for the Language Server Protocol (LSP) client.
 * <p>
 * This feature controls how configuration changes are handled, such as whether to restart the language server,
 * notify it of configuration changes, or ignore changes.
 */
public class LSPConfigurationFeature {

    /**
     * Enumeration of actions to take when the configuration changes.
     */
    public static enum OnConfigurationChanged {
        /** Restart the language server on configuration changes. */
        RESTART_LANGUAGE_SERVER,
        /** Call the didChangeConfiguration notification on configuration changes. */
        CALL_DID_CHANGE_CONFIGURATION,
        /** Ignore configuration changes. */
        IGNORE;
    }

    private LSPClientFeatures clientFeatures;

    private @NotNull OnConfigurationChanged onConfigurationChanged;

    /**
     * Constructs a new {@code LSPConfigurationFeature} with the default behavior
     * to call {@code didChangeConfiguration} on configuration changes.
     */
    public LSPConfigurationFeature() {
        setOnConfigurationChanged(OnConfigurationChanged.CALL_DID_CHANGE_CONFIGURATION);
    }

    /**
     * Returns the current action to take when the configuration changes.
     *
     * @return the {@link OnConfigurationChanged} value representing the action
     */
    public @NotNull OnConfigurationChanged getOnConfigurationChanged() {
        return onConfigurationChanged;
    }

    /**
     * Sets the action to take when the configuration changes.
     *
     * @param onConfigurationChanged the {@link OnConfigurationChanged} value to set
     */
    public void setOnConfigurationChanged(@NotNull OnConfigurationChanged onConfigurationChanged) {
        this.onConfigurationChanged = onConfigurationChanged;
    }

    /**
     * Sets the containing client features.
     * <p>
     * This method is package-private and intended for internal use.
     *
     * @param clientFeatures the client features to associate with this configuration feature
     */
    void setClientFeatures(@NotNull LSPClientFeatures clientFeatures) {
        this.clientFeatures = clientFeatures;
    }

    /**
     * Returns the containing client features.
     * <p>
     * This method is protected and intended for use by subclasses.
     *
     * @return the associated {@link LSPClientFeatures}
     */
    @NotNull
    protected LSPClientFeatures getClientFeatures() {
        return clientFeatures;
    }
}
