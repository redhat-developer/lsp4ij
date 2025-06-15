/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

/**
 * Configuration class controlling the visibility and editability of various UI components
 * in the language server panel UI ({@link com.redhat.devtools.lsp4ij.settings.ui.LanguageServerPanel}).
 * <p>
 * This includes settings for the Server tab, Mapping tab, Configuration tab, Debug tab,
 * and Installer tab, allowing customization of which sections or features are displayed
 * and whether certain UI elements are editable.
 */
public class UIConfiguration {

    // Server tab configuration
    private boolean showServerName;
    private boolean showCommandLine;

    // Mapping tab configuration
    private boolean serverMappingsEditable;

    // Configuration tab configuration
    private boolean showServerConfiguration;
    private boolean showServerInitializationOptions;
    private boolean showServerExperimental;
    private boolean showClientConfiguration;

    // Debug tab configuration
    private boolean showDebug;
    private boolean showDebugPortAndSuspend;

    // Installer tab configuration
    private boolean showInstaller;

    /**
     * Returns whether the server name should be displayed in the UI.
     *
     * @return true if the server name is shown, false otherwise
     */
    public boolean isShowServerName() {
        return showServerName;
    }

    /**
     * Sets whether the server name should be displayed in the UI.
     *
     * @param showServerName true to show the server name, false to hide it
     */
    public void setShowServerName(boolean showServerName) {
        this.showServerName = showServerName;
    }

    /**
     * Returns whether the command line used to launch the server should be displayed.
     *
     * @return true if the command line is shown, false otherwise
     */
    public boolean isShowCommandLine() {
        return showCommandLine;
    }

    /**
     * Sets whether the command line used to launch the server should be displayed.
     *
     * @param showCommandLine true to show the command line, false to hide it
     */
    public void setShowCommandLine(boolean showCommandLine) {
        this.showCommandLine = showCommandLine;
    }

    /**
     * Returns whether the server mappings are editable in the UI.
     *
     * @return true if editable, false otherwise
     */
    public boolean isServerMappingsEditable() {
        return serverMappingsEditable;
    }

    /**
     * Sets whether the server mappings are editable in the UI.
     *
     * @param serverMappingsEditable true to allow editing, false to make read-only
     */
    public void setServerMappingsEditable(boolean serverMappingsEditable) {
        this.serverMappingsEditable = serverMappingsEditable;
    }

    /**
     * Returns whether the server configuration section is shown.
     *
     * @return true if shown, false otherwise
     */
    public boolean isShowServerConfiguration() {
        return showServerConfiguration;
    }

    /**
     * Sets whether the server configuration section is shown.
     *
     * @param showServerConfiguration true to show the section, false to hide it
     */
    public void setShowServerConfiguration(boolean showServerConfiguration) {
        this.showServerConfiguration = showServerConfiguration;
    }

    /**
     * Returns whether the server initialization options are shown.
     *
     * @return true if shown, false otherwise
     */
    public boolean isShowServerInitializationOptions() {
        return showServerInitializationOptions;
    }

    /**
     * Sets whether the server initialization options are shown.
     *
     * @param showServerInitializationOptions true to show the options, false to hide them
     */
    public void setShowServerInitializationOptions(boolean showServerInitializationOptions) {
        this.showServerInitializationOptions = showServerInitializationOptions;
    }

    /**
     * Returns whether experimental server features/configuration are shown.
     *
     * @return true if shown, false otherwise
     */
    public boolean isShowServerExperimental() {
        return showServerExperimental;
    }

    /**
     * Sets whether experimental server features/configuration are shown.
     *
     * @param showServerExperimental true to show experimental features, false to hide them
     */
    public void setShowServerExperimental(boolean showServerExperimental) {
        this.showServerExperimental = showServerExperimental;
    }

    /**
     * Returns whether client-side configuration is shown.
     *
     * @return true if shown, false otherwise
     */
    public boolean isShowClientConfiguration() {
        return showClientConfiguration;
    }

    /**
     * Sets whether client-side configuration is shown.
     *
     * @param showClientConfiguration true to show client configuration, false to hide it
     */
    public void setShowClientConfiguration(boolean showClientConfiguration) {
        this.showClientConfiguration = showClientConfiguration;
    }

    /**
     * Returns whether the debug tab is shown.
     *
     * @return true if shown, false otherwise
     */
    public boolean isShowDebug() {
        return showDebug;
    }

    /**
     * Sets whether the debug tab is shown.
     *
     * @param showDebug true to show the debug tab, false to hide it
     */
    public void setShowDebug(boolean showDebug) {
        this.showDebug = showDebug;
    }

    /**
     * Returns whether debug port and suspend options are shown in the debug tab.
     *
     * @return true if shown, false otherwise
     */
    public boolean isShowDebugPortAndSuspend() {
        return showDebugPortAndSuspend;
    }

    /**
     * Sets whether debug port and suspend options are shown in the debug tab.
     *
     * @param showDebugPortAndSuspend true to show debug port and suspend options, false to hide them
     */
    public void setShowDebugPortAndSuspend(boolean showDebugPortAndSuspend) {
        this.showDebugPortAndSuspend = showDebugPortAndSuspend;
    }

    /**
     * Returns whether the installer tab is shown.
     *
     * @return true if shown, false otherwise
     */
    public boolean isShowInstaller() {
        return showInstaller;
    }

    /**
     * Sets whether the installer tab is shown.
     *
     * @param showInstaller true to show the installer tab, false to hide it
     */
    public void setShowInstaller(boolean showInstaller) {
        this.showInstaller = showInstaller;
    }
}
