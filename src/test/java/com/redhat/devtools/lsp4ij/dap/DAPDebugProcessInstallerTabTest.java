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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPCommandLineState;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.descriptors.DefaultDebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerManager;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The installer of the "Installer" tab of a debug session, as the one which runs when a debug session starts: its
 * {@code ${server.command}} is the command of the debug adapter which the debug session starts, and its
 * {@code configureServer} updates that command, of the debug adapter and of the run configuration.
 */
public class DAPDebugProcessInstallerTabTest extends LightPlatformTestCase {

    private static final String SERVER_ID = "dap-installer-tab-test";

    // As the installer of go-delve, whose check runs "${server.command}" and whose run configures the installed command
    private static final String INSTALLER = """
            {
              "name": "Installer tab test",
              "run": {
                "configureServer": {
                  "name": "Configure the server command",
                  "command": "${server.command} --log",
                  "update": true
                }
              }
            }""";

    private UserDefinedDebugAdapterServerDefinition server;
    private DAPRunConfigurationOptions runConfiguration;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        server = new UserDefinedDebugAdapterServerDefinition(SERVER_ID, "Installer tab test", "dlv dap", List.of(), List.of());
        DebugAdapterManager.getInstance().addDebugAdapterServerWithoutNotification(server);
        runConfiguration = new DAPRunConfigurationOptions();
        runConfiguration.setServerId(SERVER_ID);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            DebugAdapterManager.getInstance().removeDebugAdapterServer(server);
        } finally {
            super.tearDown();
        }
    }

    public void testTheServerCommandIsTheCommandOfTheDebugSession() throws Exception {
        runConfiguration.setCommand("/opt/go/bin/dlv dap");

        var context = runInstaller((options, environment) -> server.getFactory().createDebugAdapterDescriptor(options, environment));

        assertEquals("/opt/go/bin/dlv dap", context.getProperty("server.command"));
    }

    public void testConfigureServerUpdatesTheCommandOfTheDebugAdapterAndOfTheRunConfiguration() throws Exception {
        runConfiguration.setCommand("/opt/go/bin/dlv dap");

        runInstaller((options, environment) -> server.getFactory().createDebugAdapterDescriptor(options, environment));

        assertEquals("/opt/go/bin/dlv dap --log", runConfiguration.getCommand());
        assertEquals("/opt/go/bin/dlv dap --log", server.getCommandLine());
        var settings = UserDefinedDebugAdapterServerSettings.getInstance().getSettings(SERVER_ID);
        assertNotNull(settings);
        assertEquals("/opt/go/bin/dlv dap --log", settings.getCommandLine());
    }

    public void testWithoutACommandInItsRunConfigurationTheDebugSessionHasTheCommandOfTheDebugAdapter() throws Exception {
        runConfiguration.setCommand("");

        var context = runInstaller((options, environment) -> server.getFactory().createDebugAdapterDescriptor(options, environment));

        assertEquals("dlv dap", context.getProperty("server.command"));
        assertEquals("dlv dap --log", server.getCommandLine());
        assertEquals("dlv dap --log", runConfiguration.getCommand());
    }

    public void testWithoutAUserDefinedDebugAdapterConfigureServerUpdatesTheRunConfiguration() throws Exception {
        runConfiguration.setServerId("");
        runConfiguration.setCommand("node dapDebugServer.js");

        var context = runInstaller((options, environment) -> new DefaultDebugAdapterDescriptor(options, environment, "Run configuration"));

        assertEquals("node dapDebugServer.js", context.getProperty("server.command"));
        assertEquals("node dapDebugServer.js --log", runConfiguration.getCommand());
        assertEquals("dlv dap", server.getCommandLine());
    }

    /**
     * Runs the installer as the "Installer" tab of a debug session of the run configuration does, with its descriptor.
     */
    private @NotNull InstallerContext runInstaller(@NotNull BiFunction<DAPRunConfigurationOptions, ExecutionEnvironment, DebugAdapterDescriptor> descriptor)
            throws ExecutionException {
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var config = type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
        var environment = ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), config).build();
        var dapState = new DAPCommandLineState(descriptor.apply(runConfiguration, environment), runConfiguration, environment);

        var context = new InstallerContext(getProject(), InstallerContext.InstallerAction.RUN);
        context.setShowNotification(false);
        context.setCommandLineUpdater(DAPDebugProcess.createInstallerCommandLineUpdater(dapState));
        var installer = ServerInstallerManager.getInstance().loadInstaller(INSTALLER);
        assertTrue(ServerInstallerManager.getInstance().install(installer, context));
        return context;
    }
}
