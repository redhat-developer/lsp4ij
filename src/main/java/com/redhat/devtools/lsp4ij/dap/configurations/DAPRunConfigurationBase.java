package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.project.Project;
import com.redhat.devtools.lsp4ij.dap.definitions.DebugAdapterServerDefinition;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import com.redhat.devtools.lsp4ij.dap.descriptors.DefaultDebugAdapterDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DAPRunConfigurationBase<T> extends RunConfigurationBase<T> {

    protected DAPRunConfigurationBase(@NotNull Project project,
                                      @Nullable ConfigurationFactory factory,
                                      @Nullable String name) {
        super(project, factory, name);
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor,
                                    @NotNull ExecutionEnvironment environment) {
        var debugAdapterServer = getDebugAdapterServer();
        DebugAdapterDescriptor serverDescriptor = debugAdapterServer != null ?
                debugAdapterServer.getFactory().createDebugAdapterDescriptor(getOptions(), environment) :
                new DefaultDebugAdapterDescriptor(getOptions(), environment, getName());
        return new DAPCommandLineState(serverDescriptor, getOptions(), environment);
    }

    /**
     * Returns the server DAP factory descriptor and null otherwise.
     *
     * @return the server DAP factory descriptor and null otherwise.
     */
    @Nullable
    protected DebugAdapterDescriptorFactory getServerFactory() {
        var server = getDebugAdapterServer();
        return server != null ? server.getFactory() : null;
    }

    /**
     * Returns true if the given executor id (ex: 'Debug') can be executed and false otherwise.
     *
     * @param executorId the executor id (ex: 'Debug').
     * @return true if the given executor id (ex: 'Debug') can be executed and false otherwise.
     */
    public boolean canRun(@NotNull String executorId) {
        var serverFactory = getServerFactory();
        return serverFactory == null || serverFactory.canRun(executorId);
    }

    protected abstract @Nullable DebugAdapterServerDefinition getDebugAdapterServer();
}
