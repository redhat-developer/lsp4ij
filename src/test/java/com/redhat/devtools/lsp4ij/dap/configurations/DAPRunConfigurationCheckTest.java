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
package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.configurations.RuntimeConfigurationWarning;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.definitions.userdefined.UserDefinedDebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.server.definition.ServerMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests what {@link DAPRunConfiguration#checkConfiguration()} reports, which IntelliJ shows in the run configuration
 * dialog and before a run starts. An attach whose port can't be resolved, such as {@code $port} when the DAP
 * parameters have no {@code port}, can never connect, so it is an error which names the field and how to fix it,
 * instead of an {@code IllegalArgumentException: port out of range:-1} in the console. A working directory which isn't a
 * directory, once {@code ~} is expanded and a relative path is resolved against the project's directory, is a warning,
 * with which the run still starts, instead of being dropped without a word.
 */
public class DAPRunConfigurationCheckTest extends LightPlatformTestCase {

    // As the attach configuration of the ruby-rdbg template, without a port
    private static final String ATTACH_WITHOUT_PORT = """
            {"type": "rdbg", "name": "Attach with rdbg", "request": "attach", "localfs": true}""";

    public void testAnAttachWhosePortHasNoValueInTheDapParametersIsAnError() {
        var configuration = attach(create(), "$port", ATTACH_WITHOUT_PORT);

        assertError(DAPBundle.message("dap.settings.editor.configuration.attach.port.unresolved", "port", "$port"),
                configuration);
    }

    public void testAnAttachWhosePortIsNotANumberInTheDapParametersIsAnError() {
        var configuration = attach(create(), "$port", "{\"port\": \"12345\"}");

        assertError(DAPBundle.message("dap.settings.editor.configuration.attach.port.unresolved", "port", "$port"),
                configuration);
    }

    public void testAnAttachWithoutAPortIsAnError() {
        var configuration = attach(create(), "", ATTACH_WITHOUT_PORT);

        assertError(DAPBundle.message("dap.settings.editor.configuration.attach.port.missing"), configuration);
    }

    public void testAnAttachWhosePortIsNotANumberIsAnError() {
        var configuration = attach(create(), "port", ATTACH_WITHOUT_PORT);

        assertError(DAPBundle.message("dap.settings.editor.configuration.attach.port.notNumber", "port"), configuration);
    }

    public void testAnAttachWithAPortNumberIsValid() throws RuntimeConfigurationException {
        attach(create(), "12345", ATTACH_WITHOUT_PORT).checkConfiguration();
    }

    public void testAnAttachWhosePortIsInTheDapParametersIsValid() throws RuntimeConfigurationException {
        attach(create(), "$port", "{\"request\": \"attach\", \"port\": 12345}").checkConfiguration();
        // As the Python Debugpy template, whose address is $connect.host
        attach(create(), "$connect.port", "{\"connect\": {\"host\": \"localhost\", \"port\": 5678}}").checkConfiguration();
    }

    public void testALaunchDoesNotUseTheAttachPort() throws RuntimeConfigurationException {
        var configuration = attach(create(), "$port", ATTACH_WITHOUT_PORT);
        configuration.setDebugMode(DebugMode.LAUNCH);

        configuration.checkConfiguration();
    }

    public void testAnAttachWithAUserDefinedServerIsChecked() {
        var server = new UserDefinedDebugAdapterServerDefinition("dap-check-test", "Check test", "dap-server",
                List.of(), List.of());
        var configuration = attach(create(server), "$port", ATTACH_WITHOUT_PORT);

        assertError(DAPBundle.message("dap.settings.editor.configuration.attach.port.unresolved", "port", "$port"),
                configuration);
    }

    public void testAnAttachWithAServerFromAnExtensionPointIsNotChecked() throws RuntimeConfigurationException {
        // Its descriptor can reach its Debug Adapter in another way for an attach, as through the process it starts
        var server = new DebugAdapterServerDefinition("dap-check-test-extension", "Extension") {
            @Override
            protected @NotNull DebugAdapterDescriptorFactory createFactory() {
                return new DebugAdapterDescriptorFactory() {
                };
            }

            @Override
            protected List<ServerMapping> getServerMappings() {
                return List.of();
            }
        };

        attach(create(server), "$port", ATTACH_WITHOUT_PORT).checkConfiguration();
    }

    public void testDapParametersWhichAreNotJsonAreLeftToTheRun() throws RuntimeConfigurationException {
        // The run then fails with the message of the JSON parser, as before
        attach(create(), "$port", "{\"port\": ").checkConfiguration();
    }

    public void testAWorkingDirectoryWhichDoesNotExistIsAWarning() throws Exception {
        var configuration = launch(create());
        String missing = new File(createTempDirectory(), "missing").getPath();
        configuration.setWorkingDirectory(missing);

        assertWarning(DAPBundle.message("dap.settings.editor.configuration.cwd.notDirectory", missing), configuration);
    }

    public void testAWorkingDirectoryWhichIsAFileIsAWarning() throws Exception {
        var configuration = launch(create());
        String file = Files.createFile(createTempDirectory().toPath().resolve("hello.rb")).toString();
        configuration.setWorkingDirectory(file);

        assertWarning(DAPBundle.message("dap.settings.editor.configuration.cwd.notDirectory", file), configuration);
    }

    public void testTheWarningNamesTheResolvedWorkingDirectory() {
        var configuration = launch(create());
        configuration.setWorkingDirectory("missing/directory");
        String basePath = getProject().getBasePath();
        assertNotNull("The project of the tests has a directory", basePath);

        assertWarning(DAPBundle.message("dap.settings.editor.configuration.cwd.notDirectory",
                Path.of(basePath).resolve("missing").resolve("directory").normalize().toString()), configuration);
    }

    public void testAnExistingWorkingDirectoryOrNoneIsValid() throws Exception {
        var configuration = launch(create());
        configuration.checkConfiguration();

        configuration.setWorkingDirectory(createTempDirectory().getPath());
        configuration.checkConfiguration();

        // The home directory
        configuration.setWorkingDirectory("~");
        configuration.checkConfiguration();
    }

    private static void assertError(@NotNull String expected, @NotNull DAPRunConfiguration configuration) {
        try {
            configuration.checkConfiguration();
            fail("checkConfiguration() reported no error, expected: " + expected);
        } catch (RuntimeConfigurationError e) {
            assertEquals(expected, e.getLocalizedMessage());
        } catch (RuntimeConfigurationException e) {
            fail("checkConfiguration() reported \"" + e.getLocalizedMessage() + "\" as "
                    + e.getClass().getSimpleName() + ", expected the error: " + expected);
        }
    }

    private static void assertWarning(@NotNull String expected, @NotNull DAPRunConfiguration configuration) {
        try {
            configuration.checkConfiguration();
            fail("checkConfiguration() reported no warning, expected: " + expected);
        } catch (RuntimeConfigurationWarning e) {
            assertEquals(expected, e.getLocalizedMessage());
        } catch (RuntimeConfigurationException e) {
            fail("checkConfiguration() reported \"" + e.getLocalizedMessage() + "\" as "
                    + e.getClass().getSimpleName() + ", expected the warning: " + expected);
        }
    }

    private static @NotNull DAPRunConfiguration launch(@NotNull DAPRunConfiguration configuration) {
        configuration.setCommand("dap-server");
        configuration.setDebugMode(DebugMode.LAUNCH);
        return configuration;
    }

    private @NotNull File createTempDirectory() throws Exception {
        File directory = FileUtil.createTempDirectory("dap-check", null);
        Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory));
        return directory;
    }

    private static @NotNull DAPRunConfiguration attach(@NotNull DAPRunConfiguration configuration,
                                                       @NotNull String attachPort,
                                                       @NotNull String attachConfiguration) {
        configuration.setCommand("dap-server");
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachPort(attachPort);
        configuration.setAttachConfiguration(attachConfiguration);
        return configuration;
    }

    /**
     * Creates a DAP run configuration without a server, as the template of the DAP run configuration type gives it.
     */
    private @NotNull DAPRunConfiguration create() {
        return (DAPRunConfiguration) factory().createTemplateConfiguration(getProject());
    }

    /**
     * Creates a DAP run configuration with the given server.
     */
    private @NotNull DAPRunConfiguration create(@NotNull DebugAdapterServerDefinition server) {
        return new DAPRunConfiguration(getProject(), factory(), "DAP") {
            @Override
            protected @Nullable DebugAdapterServerDefinition getDebugAdapterServer() {
                return server;
            }
        };
    }

    private static @NotNull ConfigurationFactory factory() {
        var type = ConfigurationTypeUtil.findConfigurationType("DAPConfiguration");
        assertNotNull("The DAP run configuration type is registered", type);
        return type.getConfigurationFactories()[0];
    }
}
