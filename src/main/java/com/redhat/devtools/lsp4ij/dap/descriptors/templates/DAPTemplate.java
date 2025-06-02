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
package com.redhat.devtools.lsp4ij.dap.descriptors.templates;

import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.dap.LaunchConfiguration;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;

import java.util.List;

/**
 * A DAP (Debug Adapter Protocol) template.
 */
public class DAPTemplate extends ServerTemplate {

    public static final DAPTemplate NONE = new DAPTemplate() {
        @Override
        public String getName() {
            return "None";
        }
    };

    public static final DAPTemplate NEW_TEMPLATE = new DAPTemplate() {
        @Override
        public String getName() {
            return LanguageServerBundle.message("new.language.server.dialog.import.template.selection");
        }
    };

    public static final String LAUNCH_FILE_START_NAME = "launch.";
    public static final String ATTACH_FILE_START_NAME = "attach.";

    public static final String CONNECT_TIMEOUT_JSON_PROPERTY = "connectTimeout";
    public static final String DEBUG_SERVER_READY_PATTERN_JSON_PROPERTY = "debugServerReadyPattern";
    public static final String LAUNCH_PROPERTY = "launch";

    public static final String ATTACH_PROPERTY = "attach";
    public static final String ATTACH_ADDRESS_PROPERTY = "address";
    public static final String ATTACH_PORT_PROPERTY = "port";

    private List<LaunchConfiguration> launchConfigurations;
    private int connectTimeout;
    private String debugServerReadyPattern;

    private String attachAddress;
    private String attachPort;

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setDebugServerReadyPattern(String debugServerReadyPattern) {
        this.debugServerReadyPattern = debugServerReadyPattern;
    }

    public String getDebugServerReadyPattern() {
        return debugServerReadyPattern;
    }

    public List<LaunchConfiguration> getLaunchConfigurations() {
        return launchConfigurations;
    }

    public void setLaunchConfigurations(List<LaunchConfiguration> launchConfigurations) {
        this.launchConfigurations = launchConfigurations;
    }

    public String getAttachAddress() {
        return attachAddress;
    }

    public void setAttachAddress(String attachAddress) {
        this.attachAddress = attachAddress;
    }

    public String getAttachPort() {
        return attachPort;
    }

    public void setAttachPort(String attachPort) {
        this.attachPort = attachPort;
    }

}