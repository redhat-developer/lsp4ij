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
package com.redhat.devtools.lsp4ij.dap;

import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterServerListener;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The attach address and port of a debug adapter, as the Settings | Debug Adapters page edits them: an update of the
 * server keeps them, in its definition and in its settings, and reports a change only when one of them changed.
 */
public class DebugAdapterManagerAttachSettingsTest extends LightPlatformTestCase {

    private static final String SERVER_ID = "dap-attach-settings-test";

    private UserDefinedDebugAdapterServerDefinition server;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new UserDefinedDebugAdapterServerDefinition(SERVER_ID, "Attach settings test", "dlv dap", List.of(), List.of());
        server.setAttachAddress("localhost");
        server.setAttachPort("5678");
        DebugAdapterManager.getInstance().addDebugAdapterServerWithoutNotification(server);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            DebugAdapterManager.getInstance().removeDebugAdapterServer(server);
        } finally {
            super.tearDown();
        }
    }

    public void testAChangedAttachPortIsKept() {
        var event = update("localhost", "6789");

        assertNotNull(event);
        assertEquals("6789", server.getAttachPort());
        assertEquals("6789", settings().getAttachPort());
        assertEquals("localhost", settings().getAttachAddress());
    }

    public void testAChangedAttachAddressIsKept() {
        var event = update("127.0.0.1", "5678");

        assertNotNull(event);
        assertEquals("127.0.0.1", server.getAttachAddress());
        assertEquals("127.0.0.1", settings().getAttachAddress());
        assertEquals("5678", settings().getAttachPort());
    }

    public void testAnUnchangedServerReportsNoChange() {
        // the attach port isn't compared with the attach address
        assertNull(update("localhost", "5678"));
    }

    private DebugAdapterServerListener.ChangedEvent update(@NotNull String attachAddress, @NotNull String attachPort) {
        var request = new DebugAdapterManager.UpdateDebugAdapterServerRequest(server,
                server.getDisplayName(),
                server.getUrl(),
                server.getUserEnvironmentVariables(),
                server.isIncludeSystemEnvironmentVariables(),
                server.getCommandLine(),
                server.getConnectTimeout(),
                server.getDebugServerReadyPattern(),
                server.getLanguageMappings(),
                server.getFileTypeMappings(),
                server.getLaunchConfigurations(),
                attachAddress,
                attachPort,
                server.getInstallerConfiguration());
        return DebugAdapterManager.getInstance().updateServerDefinition(request, false);
    }

    private @NotNull UserDefinedDebugAdapterServerSettings.ItemSettings settings() {
        var settings = UserDefinedDebugAdapterServerSettings.getInstance().getSettings(SERVER_ID);
        assertNotNull(settings);
        return settings;
    }
}
