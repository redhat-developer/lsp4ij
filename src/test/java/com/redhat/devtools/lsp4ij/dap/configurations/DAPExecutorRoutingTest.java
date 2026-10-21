package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.testFramework.LightPlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptorFactory;
import java.util.Arrays;

public class DAPExecutorRoutingTest extends LightPlatformTestCase {
    public void testGenericConfigurationDoesNotClaimProfilerExecutors() {
        var factory = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow().getConfigurationFactories()[0];
        var config = (DAPRunConfiguration) factory.createTemplateConfiguration(getProject());
        config.setCommand("python3 -m debugpy.adapter");
        assertTrue(config.canRun(DefaultRunExecutor.EXECUTOR_ID));
        assertTrue(config.canRun(DefaultDebugExecutor.EXECUTOR_ID));
        assertNotNull(ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, config));
        for (String executor : new String[]{"Profiler", "FactorioLuaProfiler", "unrelated-executor"}) {
            assertFalse(config.canRun(executor));
            assertNull(ProgramRunner.getRunner(executor, config));
        }
    }

    public void testFactoryRequiresExplicitCustomExecutorOptIn() {
        var defaults = new DebugAdapterDescriptorFactory() {};
        assertTrue(defaults.canRun(DefaultRunExecutor.EXECUTOR_ID));
        assertTrue(defaults.canRun(DefaultDebugExecutor.EXECUTOR_ID));
        assertFalse(defaults.canRun("custom-profiler"));
        var factory = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow().getConfigurationFactories()[0];
        var config = new DAPRunConfiguration(getProject(), factory, "Custom profiler") {
            @Override protected DebugAdapterDescriptorFactory getServerFactory() {
                return new DebugAdapterDescriptorFactory() {
                    @Override public boolean canRun(String executorId) {
                        return "custom-profiler".equals(executorId) || super.canRun(executorId);
                    }
                };
            }
        };
        assertTrue(config.canRun("custom-profiler"));
        assertNotNull(ProgramRunner.getRunner("custom-profiler", config));
        assertFalse(config.canRun("unrelated-executor"));
        assertTrue(config.canRun(DefaultDebugExecutor.EXECUTOR_ID));
    }
}
