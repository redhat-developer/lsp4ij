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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.descriptors.ServerReadyConfig;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;

/**
 * The process of a debug session: a launch starts the Debug Adapter server, and an attach, which connects to a Debug
 * Adapter that already runs, starts nothing, unless the descriptor reaches its Debug Adapter through its process.
 */
public class DAPCommandLineStateTest extends HeavyPlatformTestCase {

    public void testALaunchStartsTheDebugAdapter() throws Exception {
        var adapter = new NopProcessHandler();

        assertSame(adapter, startProcess(new Descriptor(DebugMode.LAUNCH, adapter, false)));
    }

    public void testAnAttachStartsNothingByDefault() throws Exception {
        // the descriptor's startServer() fails the test if it is called
        ProcessHandler process = startProcess(new Descriptor(DebugMode.ATTACH, null, false));

        assertInstanceOf(process, NopProcessHandler.class);
    }

    public void testAnAttachStartsTheProcessOfADescriptorWhoseDebugAdapterIsReachedThroughIt() throws Exception {
        var adapter = new NopProcessHandler();

        assertSame(adapter, startProcess(new Descriptor(DebugMode.ATTACH, adapter, true)));
    }

    private ProcessHandler startProcess(Descriptor descriptor) throws Exception {
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var config = type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
        var environment = ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), config).build();
        return new DAPCommandLineState(descriptor.withEnvironment(environment), new DAPRunConfigurationOptions(), environment).startProcess();
    }

    /**
     * A descriptor in the given debug mode, whose Debug Adapter's process is [adapter]; with [attachesThroughProcess],
     * an attach reaches the Debug Adapter through that process.
     */
    private record Descriptor(DebugMode mode, ProcessHandler adapter, boolean attachesThroughProcess) {

        DebugAdapterDescriptor withEnvironment(ExecutionEnvironment environment) {
            return new DebugAdapterDescriptor(new DAPRunConfigurationOptions(), environment, null) {
                @Override
                public ProcessHandler startServer() {
                    assertNotNull("startServer() is not called", adapter);
                    return adapter;
                }

                @Override
                public ProcessHandler startServer(@NotNull DebugMode debugMode) throws ExecutionException {
                    return attachesThroughProcess ? startServer() : super.startServer(debugMode);
                }

                @Override
                public @NotNull DebugMode getDebugMode() {
                    return mode;
                }

                @Override
                public @NotNull ServerReadyConfig getServerReadyConfig(@NotNull DebugMode debugMode) {
                    return new ServerReadyConfig(0);
                }

                @Override
                public Map<String, Object> getDapParameters() {
                    return Map.of();
                }

                @Override
                public FileType getFileType() {
                    return PlainTextFileType.INSTANCE;
                }

                @Override
                public boolean isDebuggableFile(@NotNull VirtualFile file, @NotNull Project project) {
                    return false;
                }
            };
        }
    }
}
