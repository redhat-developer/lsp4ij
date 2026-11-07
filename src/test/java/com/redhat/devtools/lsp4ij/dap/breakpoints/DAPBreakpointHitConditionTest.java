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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointListener;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A DAP line breakpoint has a hit condition, which it keeps with the project, which its properties panel changes, and
 * which is sent to the DAP server with the breakpoint.
 */
public class DAPBreakpointHitConditionTest extends HeavyPlatformTestCase {

    public void testTheHitConditionIsSent() throws Exception {
        var dir = Files.createTempDirectory("dap-hit-conditions-");
        var everyTime = breakpoint(dir.resolve("a.lua"), 0);
        var fromTheThirdHit = breakpoint(dir.resolve("a.lua"), 1);
        var blank = breakpoint(dir.resolve("a.lua"), 2);
        fromTheThirdHit.getProperties().setHitCondition(">= 3");
        blank.getProperties().setHitCondition(" ");

        var requests = new ArrayList<SetBreakpointsArguments>();
        var handler = handler();
        try {
            for (var breakpoint : List.of(everyTime, fromTheThirdHit, blank)) {
                handler.registerBreakpoint(breakpoint);
            }
            handler.initialize(server(requests), new Capabilities()).join();

            assertEquals(1, requests.size());
            var sent = Arrays.stream(requests.get(0).getBreakpoints()).map(SourceBreakpoint::getHitCondition).toList();
            assertEquals(Arrays.asList(null, ">= 3", null), sent);
        } finally {
            handler.dispose();
            FileUtil.delete(dir.toFile());
        }
    }

    public void testThePropertiesAreSavedAndLoaded() {
        var properties = new DAPBreakpointProperties();
        properties.setHitCondition("% 2");

        var loaded = new DAPBreakpointProperties();
        loaded.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(properties.getState()), DAPBreakpointProperties.class));

        assertEquals("% 2", loaded.getHitCondition());
    }

    public void testThePanelChangesTheHitCondition() throws Exception {
        var dir = Files.createTempDirectory("dap-hit-conditions-");
        var breakpoint = breakpoint(dir.resolve("a.lua"), 0);
        var changed = new ArrayList<XBreakpoint<?>>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(XBreakpointListener.TOPIC, new XBreakpointListener<>() {
            @Override
            public void breakpointChanged(@NotNull XBreakpoint<?> changedBreakpoint) {
                changed.add(changedBreakpoint);
            }
        });

        var panel = new DAPBreakpointType().createCustomPropertiesPanel(getProject());
        try {
            panel.loadFrom(breakpoint);
            var hitCondition = UIUtil.findComponentOfType(panel.getComponent(), JBTextField.class);
            assertNotNull(hitCondition);
            assertEquals("", hitCondition.getText());

            // nothing changed: the debug sessions are not told
            panel.saveTo(breakpoint);
            assertEmpty(changed);

            hitCondition.setText(" >= 3 ");
            panel.saveTo(breakpoint);
            assertEquals(">= 3", breakpoint.getProperties().getHitCondition());
            assertEquals(List.of(breakpoint), changed);

            hitCondition.setText("");
            panel.saveTo(breakpoint);
            assertEquals("", breakpoint.getProperties().getHitCondition());
            assertEquals(List.of(breakpoint, breakpoint), changed);
        } finally {
            panel.dispose();
            FileUtil.delete(dir.toFile());
        }
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
                return "hit-conditions";
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

    private static IDebugProtocolServer server(List<SetBreakpointsArguments> requests) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpoints")) {
                requests.add((SetBreakpointsArguments) a[0]);
                var response = new SetBreakpointsResponse();
                response.setBreakpoints(new Breakpoint[0]);
                return CompletableFuture.completedFuture(response);
            }
            if (m.getName().equals("equals")) return p == a[0];
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            throw new AssertionError(m.getName());
        });
    }

    private XLineBreakpoint<DAPBreakpointProperties> breakpoint(Path path, int line) throws Exception {
        if (!Files.exists(path)) {
            Files.writeString(path, "local a = 1\nlocal b = 2\nlocal c = 3\n");
        }
        var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
        assertNotNull(file);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), line, new DAPBreakpointProperties()));
    }
}
