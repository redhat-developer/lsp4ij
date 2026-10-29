// SPDX-License-Identifier: EPL-2.0
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;

import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** A breakpoint which the debug adapter can't debug is marked as invalid with a localized message. */
public class DAPUnsupportedBreakpointMessageTest extends HeavyPlatformTestCase {

    private static final List<String> KEYS = List.of(
            "dap.breakpoints.location.not.available",
            "dap.breakpoints.file.not.debuggable");

    public void testBreakpointInFileNotDebuggableIsMarkedInvalid() throws Exception {
        var messages = new ArrayList<String>();
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class}, (p, m, a) -> {
            if (m.getName().equals("setBreakpointInvalid")) {
                messages.add((String) a[1]);
            }
            return null;
        });
        var handler = new DAPBreakpointHandlerBase<XLineBreakpoint<DAPBreakpointProperties>>(DAPBreakpointType.class, session, descriptor(), getProject()) {};

        var dir = Files.createTempDirectory("dap-breakpoints-");
        try {
            var path = dir.resolve("a.lua");
            Files.writeString(path, "local a = 1\n");
            var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path);
            assertNotNull(file);
            XLineBreakpoint<DAPBreakpointProperties> breakpoint = WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                    .addLineBreakpoint(new DAPBreakpointType(), file.getUrl(), 0, new DAPBreakpointProperties()));

            handler.registerBreakpoint(breakpoint);
            assertEquals(List.of(DAPBundle.message("dap.breakpoints.file.not.debuggable")), messages);
        } finally {
            handler.dispose();
            com.intellij.openapi.util.io.FileUtil.delete(dir.toFile());
        }
    }

    public void testMessagesAreTranslated() throws Exception {
        for (String bundle : List.of("/messages/DAPBundle.properties", "/messages/DAPBundle_zh_CN.properties")) {
            var properties = new Properties();
            try (var stream = getClass().getResourceAsStream(bundle)) {
                assertNotNull(bundle, stream);
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            for (String key : KEYS) {
                assertFalse(bundle + ": " + key, properties.getProperty(key, "").isBlank());
            }
        }
    }

    private com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor descriptor() throws Exception {
        var type = Arrays.stream(com.intellij.execution.configurations.ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var config = type.getConfigurationFactories()[0].createTemplateConfiguration(getProject());
        var environment = ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), config).build();
        return new com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor(new DAPRunConfigurationOptions(), environment, null) {
            @Override
            public com.intellij.execution.process.ProcessHandler startServer() {
                throw new UnsupportedOperationException();
            }
            @Override
            public String getId() {
                return "unsupported-breakpoint";
            }
            @Override
            public Map<String, Object> getDapParameters() {
                return Map.of();
            }
            @Override
            public com.intellij.openapi.fileTypes.FileType getFileType() {
                return com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE;
            }
            @Override
            public boolean isDebuggableFile(com.intellij.openapi.vfs.VirtualFile file, com.intellij.openapi.project.Project project) {
                // The debug adapter can't debug any file
                return false;
            }
        };
    }
}
