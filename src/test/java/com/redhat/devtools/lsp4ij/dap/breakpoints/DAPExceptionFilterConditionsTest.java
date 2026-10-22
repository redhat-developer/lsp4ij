// SPDX-License-Identifier: EPL-2.0
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import com.redhat.devtools.lsp4ij.dap.settings.UserDefinedDebugAdapterServerSettings.FilterItemSettings;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.ExceptionBreakpointsFilter;
import org.eclipse.lsp4j.debug.ExceptionFilterOptions;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** An exception breakpoint filter which supports a condition is sent with it, as DAP exception filter options. */
public class DAPExceptionFilterConditionsTest extends HeavyPlatformTestCase {

    public void testConditionsAreSentAsFilterOptions() throws Exception {
        var handler = handler();
        var withOptions = new ArrayList<SetExceptionBreakpointsArguments>();
        var withoutOptions = new ArrayList<SetExceptionBreakpointsArguments>();
        var optionsServer = server(withOptions);
        var plainServer = server(withoutOptions);
        try {
            var capabilities = new Capabilities();
            capabilities.setSupportsExceptionFilterOptions(true);
            handler.initialize(optionsServer, capabilities).join();
            handler.initialize(plainServer, new Capabilities()).join();

            var panel = handler.getExceptionBreakpointsPanel();
            panel.setCondition("uncaught", " IllegalArgument ");
            // a filter which doesn't support conditions, and a disabled one, keep theirs out of the request
            panel.setCondition("all", "Ignored");
            panel.setCondition("caught", "OutOfBounds");

            handler.sendExceptionBreakpointFilters(filters(), optionsServer).join();
            handler.sendExceptionBreakpointFilters(filters(), plainServer).join();

            assertEquals(Set.of("all"), Set.of(withOptions.get(0).getFilters()));
            assertEquals(Map.of("uncaught", "IllegalArgument"), options(withOptions.get(0)));
            // a DAP server without filter options gets the filters only
            assertEquals(Set.of("uncaught", "all"), Set.of(withoutOptions.get(0).getFilters()));
            assertNull(withoutOptions.get(0).getFilterOptions());

            // the conditions are kept with the DAP server's filter settings
            var nextHandler = handler();
            try {
                assertEquals("IllegalArgument", nextHandler.getExceptionBreakpointsPanel().getCondition("uncaught"));
                nextHandler.getExceptionBreakpointsPanel().setCondition("uncaught", "");
                assertNull(panel.getCondition("uncaught"));
            } finally {
                nextHandler.dispose();
            }
        } finally {
            handler.dispose();
        }
    }

    public void testConditionsAreSavedWithTheFilterSettings() {
        var settings = new FilterItemSettings();
        settings.setExceptionBreakpointsFilterConditions(new TreeMap<>(Map.of("uncaught", "IllegalArgument")));

        var saved = XmlSerializer.deserialize(XmlSerializer.serialize(settings), FilterItemSettings.class);

        assertEquals(Map.of("uncaught", "IllegalArgument"), saved.getExceptionBreakpointsFilterConditions());
    }

    private static ExceptionBreakpointsFilter[] filters() {
        return new ExceptionBreakpointsFilter[]{
                filter("uncaught", true, true, "Exception types, separated by commas"),
                filter("all", true, false, null),
                filter("caught", false, true, null)};
    }

    private static ExceptionBreakpointsFilter filter(String id, boolean enabled, boolean supportsCondition, String conditionDescription) {
        var filter = new ExceptionBreakpointsFilter();
        filter.setFilter(id);
        filter.setLabel(id);
        filter.setDefault_(enabled);
        filter.setSupportsCondition(supportsCondition);
        filter.setConditionDescription(conditionDescription);
        return filter;
    }

    private static Map<String, String> options(SetExceptionBreakpointsArguments args) {
        return Arrays.stream(args.getFilterOptions()).collect(Collectors.toMap(ExceptionFilterOptions::getFilterId, ExceptionFilterOptions::getCondition));
    }

    private DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>> handler() throws Exception {
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var config = type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
        var environment = ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), config).build();
        var descriptor = new DebugAdapterDescriptor(new DAPRunConfigurationOptions(), environment, null) {
            @Override
            public ProcessHandler startServer() {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getId() {
                return "exception-filter-conditions";
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
            public boolean isDebuggableFile(VirtualFile file, Project project) {
                return true;
            }
        };
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class}, (p, m, a) -> null);
        return new DAPBreakpointHandlerBase<>(DAPBreakpointType.class, session, descriptor, getProject()) {
        };
    }

    private static IDebugProtocolServer server(List<SetExceptionBreakpointsArguments> requests) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setExceptionBreakpoints")) {
                requests.add((SetExceptionBreakpointsArguments) a[0]);
                return CompletableFuture.completedFuture(new SetExceptionBreakpointsResponse());
            }
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            throw new AssertionError(m.getName());
        });
    }
}
