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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.impl.BreakpointManagerState;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl;
import com.redhat.devtools.lsp4ij.dap.DAPProjectLifecycleListener;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;
import com.redhat.devtools.lsp4ij.dap.descriptors.DebugAdapterDescriptor;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.DataBreakpoint;
import org.eclipse.lsp4j.debug.DataBreakpointAccessType;
import org.eclipse.lsp4j.debug.DataBreakpointInfoArguments;
import org.eclipse.lsp4j.debug.DataBreakpointInfoResponse;
import org.eclipse.lsp4j.debug.SetDataBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetDataBreakpointsResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * "Break on Value Change" asks the DAP server which data a variable is, and adds a data breakpoint on it, which is
 * sent with 'setDataBreakpoints', all of them at each change, to the DAP server whose data it is, shown verified or
 * not as the DAP server answers, and removed when its session ends unless its data id can persist.
 */
public class DAPDataBreakpointHandlerTest extends HeavyPlatformTestCase {

    private static final String SERVER_ID = "data-breakpoints";

    private record Request(String server, List<String> dataIds) {
    }

    private final List<DataBreakpointInfoArguments> asked = new ArrayList<>();
    private final List<Request> requests = new ArrayList<>();
    private final List<SetDataBreakpointsArguments> sent = new ArrayList<>();
    // What the IJ breakpoints were shown as, in order
    private final List<String> shown = new ArrayList<>();
    private final List<String> refusals = new ArrayList<>();
    private final List<XDebugSessionListener> sessionListeners = new ArrayList<>();

    public void testNothingIsSentToADebugAdapterWithoutDataBreakpoints() throws Exception {
        assertFalse(DAPDataBreakpointHandler.isSupportsDataBreakpoints(new Capabilities()));
        var unsupported = new Capabilities();
        unsupported.setSupportsDataBreakpoints(false);
        assertFalse(DAPDataBreakpointHandler.isSupportsDataBreakpoints(unsupported));
        assertTrue(DAPDataBreakpointHandler.isSupportsDataBreakpoints(supported()));

        var handler = handler();
        try {
            var server = server("server", arguments -> new Breakpoint[0]);
            handler.initialize(server, unsupported).join();
            var count = breakpoint("7:count", server, false);
            handler.registerBreakpoint(count);
            handler.unregisterBreakpoint(count, false);

            assertEmpty(requests);
        } finally {
            handler.dispose();
        }
    }

    public void testTheVariableIsAskedForAndARefusalAddsNothing() throws Exception {
        var handler = handler();
        try {
            var server = server("server", arguments -> switch (arguments.getName()) {
                case "i" -> info(null, "Only a field of an object can be watched.", null, null);
                case "fails" -> throw new IllegalStateException("no such variable");
                default -> info(null, null, null, null);
            }, arguments -> new Breakpoint[0]);
            handler.initialize(server, supported()).join();

            // the variable of the container 7, in the frame 3
            assertNull(handler.addDataBreakpoint(server, DAPDataBreakpointHandler.createDataBreakpointInfoArguments(7, "i", 3)).join());
            assertEquals(1, asked.size());
            assertEquals(Integer.valueOf(7), asked.get(0).getVariablesReference());
            assertEquals("i", asked.get(0).getName());
            assertEquals(Integer.valueOf(3), asked.get(0).getFrameId());
            // the DAP server says why it can't watch the variable, and nothing is added
            assertEquals(List.of("Only a field of an object can be watched."), refusals);
            assertEmpty(dataBreakpoints());
            assertEmpty(requests);
            refusals.clear();

            // a watch has no container: its expression is the name
            assertNull(handler.addDataBreakpoint(server, DAPDataBreakpointHandler.createDataBreakpointInfoArguments(null, "a + b", 3)).join());
            assertNull(asked.get(1).getVariablesReference());
            assertEquals("a + b", asked.get(1).getName());
            // a refusal without a reason names the variable
            assertEquals(List.of("The DAP server cannot break on a change of 'a + b'"), refusals);
            refusals.clear();

            // a failed request adds nothing either
            assertNull(handler.addDataBreakpoint(server, DAPDataBreakpointHandler.createDataBreakpointInfoArguments(7, "fails", 3)).join());
            assertEquals(List.of("Cannot break on a change of 'fails': no such variable"), refusals);
            assertEmpty(dataBreakpoints());
        } finally {
            handler.dispose();
        }
    }

    public void testAWatchableVariableAddsADataBreakpointOfItsDebugAdapter() throws Exception {
        var handler = handler();
        try {
            var server = server("server", arguments -> info("7:count", "Counter.count",
                    new DataBreakpointAccessType[]{DataBreakpointAccessType.READ, DataBreakpointAccessType.WRITE}, false),
                    arguments -> new Breakpoint[0]);
            handler.initialize(server, supported()).join();

            var count = add(handler, server, 7, "count");
            assertNotNull(count);
            assertEquals(List.of(count), dataBreakpoints());
            var properties = count.getProperties();
            assertEquals("7:count", properties.getDataId());
            assertEquals("Counter.count", properties.getDescription());
            // a change of the value is a write
            assertEquals(DataBreakpointAccessType.WRITE, properties.getAccessType());
            assertFalse(properties.isCanPersist());
            assertEquals(SERVER_ID, properties.getServerId());
            assertSame(server, properties.getServer());
            assertEquals("Counter.count", XDebuggerUtil.getInstance().findBreakpointType(DAPDataBreakpointType.class).getDisplayText(count));
            assertEmpty(refusals);

            // the same data again adds no other breakpoint
            assertSame(count, add(handler, server, 7, "count"));
            assertEquals(List.of(count), dataBreakpoints());
        } finally {
            handler.dispose();
        }
    }

    public void testTheAccessTypeIsAWriteIfOffered() {
        assertEquals(DataBreakpointAccessType.WRITE, DAPDataBreakpointHandler.getAccessType(new DataBreakpointAccessType[]{
                DataBreakpointAccessType.READ, DataBreakpointAccessType.WRITE}));
        assertEquals(DataBreakpointAccessType.READ_WRITE, DAPDataBreakpointHandler.getAccessType(new DataBreakpointAccessType[]{
                DataBreakpointAccessType.READ_WRITE, DataBreakpointAccessType.READ}));
        // an access type which LSP4J doesn't know is null
        assertEquals(DataBreakpointAccessType.READ, DAPDataBreakpointHandler.getAccessType(new DataBreakpointAccessType[]{
                null, DataBreakpointAccessType.READ}));
        // the DAP server's own when it offers none
        assertNull(DAPDataBreakpointHandler.getAccessType(new DataBreakpointAccessType[0]));
        assertNull(DAPDataBreakpointHandler.getAccessType(null));
    }

    public void testDataIdsAccessTypesConditionsAndHitConditionsAreSent() throws Exception {
        var server = server("server", arguments -> new Breakpoint[0]);
        var count = breakpoint("7:count", server, false);
        var total = breakpoint("8:total", server, false);
        var disabledCondition = breakpoint("9:size", server, false);
        var disabled = breakpoint("10:unused", server, false);
        WriteAction.run(() -> {
            count.setConditionExpression(XDebuggerUtil.getInstance().createExpression("count > 1", null, null, EvaluationMode.EXPRESSION));
            total.getProperties().setHitCondition(">= 3");
            total.getProperties().setAccessType(DataBreakpointAccessType.READ_WRITE);
            disabledCondition.setConditionExpression(XDebuggerUtil.getInstance().createExpression("false", null, null, EvaluationMode.EXPRESSION));
            ((XBreakpointBase<?, ?, ?>) disabledCondition).setConditionEnabled(false);
            disabled.setEnabled(false);
        });

        // the helper maps each IJ breakpoint, in order
        assertEquals(List.of("7:count WRITE if count > 1", "8:total READ_WRITE hit >= 3", "9:size WRITE"),
                describe(DAPDataBreakpointHandler.createSetDataBreakpointsArguments(List.of(count, total, disabledCondition))));

        var handler = handler();
        try {
            handler.initialize(server, supported()).join();
            for (var breakpoint : List.of(count, total, disabledCondition, disabled)) {
                if (breakpoint.isEnabled()) {
                    // IntelliJ registers the enabled breakpoints only
                    handler.registerBreakpoint(breakpoint);
                }
            }

            // each request has every enabled data breakpoint
            assertEquals(List.of("7:count WRITE if count > 1", "8:total READ_WRITE hit >= 3", "9:size WRITE"), describe(sent.get(sent.size() - 1)));
        } finally {
            handler.dispose();
        }
    }

    public void testEachChangeSendsAllTheBreakpoints() throws Exception {
        var handler = handler();
        try {
            var server = server("server", arguments -> new Breakpoint[0]);
            handler.initialize(server, supported()).join();
            var count = breakpoint("7:count", server, false);
            var total = breakpoint("8:total", server, false);
            var size = breakpoint("9:size", server, false);

            // each one added is sent with the others
            handler.registerBreakpoint(count);
            handler.registerBreakpoint(total);
            handler.registerBreakpoint(size);
            assertEquals(List.of(
                    new Request("server", List.of("7:count")),
                    new Request("server", List.of("7:count", "8:total")),
                    new Request("server", List.of("7:count", "8:total", "9:size"))), requests);
            requests.clear();

            // a removal sends the remaining ones
            handler.unregisterBreakpoint(count, false);
            handler.unregisterBreakpoint(size, false);
            assertEquals(List.of(new Request("server", List.of("8:total", "9:size")), new Request("server", List.of("8:total"))), requests);
            requests.clear();

            // the last one is removed with an empty request
            handler.unregisterBreakpoint(total, true);
            assertEquals(List.of(new Request("server", List.of())), requests);
            requests.clear();

            // one which is not registered, such as a disabled one, sends nothing
            handler.unregisterBreakpoint(total, false);
            assertEmpty(requests);
        } finally {
            handler.dispose();
        }
    }

    public void testEachDebugAdapterGetsTheBreakpointsOfItsData() throws Exception {
        var handler = handler();
        try {
            var parent = server("parent", arguments -> new Breakpoint[0]);
            var child = server("child", arguments -> new Breakpoint[0]);
            handler.initialize(parent, supported()).join();
            handler.initialize(child, supported()).join();
            var count = breakpoint("7:count", parent, false);
            var total = breakpoint("7:total", child, false);

            handler.registerBreakpoint(count);
            handler.registerBreakpoint(total);
            assertEquals(List.of(
                    new Request("parent", List.of("7:count")),
                    new Request("child", List.of()),
                    new Request("parent", List.of("7:count")),
                    new Request("child", List.of("7:total"))), requests);
            requests.clear();

            // the data of another debug session is that session's to send
            var other = breakpoint("7:other", server("other", arguments -> new Breakpoint[0]), false);
            handler.registerBreakpoint(other);
            handler.unregisterBreakpoint(other, false);
            assertEmpty(requests);
        } finally {
            handler.dispose();
        }
    }

    public void testEachBreakpointIsShownAsTheDebugAdapterAnswers() throws Exception {
        var handler = handler();
        try {
            var server = server("server", arguments -> Arrays.stream(arguments.getBreakpoints())
                    .map(breakpoint -> breakpoint.getDataId().equals("7:count")
                            ? answer(11, true, null)
                            : answer(12, false, "Only a change of the field's value stops the program."))
                    .toArray(Breakpoint[]::new));
            handler.initialize(server, supported()).join();
            var count = breakpoint("7:count", server, false);
            var total = breakpoint("8:total", server, false);
            handler.registerBreakpoint(count);
            shown.clear();
            handler.registerBreakpoint(total);

            assertEquals(List.of("verified 7:count", "invalid 8:total: Only a change of the field's value stops the program."), shown);
            assertSame(count, handler.findBreakpointById(11));
            assertSame(total, handler.findBreakpointById(12));
            shown.clear();

            // a 'breakpoint' event with the reason 'changed'
            handler.updateBreakpoint(answer(12, true, null), null);
            // another breakpoint's id is not one of these
            handler.updateBreakpoint(answer(99, false, "a line breakpoint"), null);
            assertEquals(List.of("verified 8:total"), shown);
        } finally {
            handler.dispose();
        }
    }

    public void testAFailedRequestShowsTheBreakpointsInvalid() throws Exception {
        var handler = handler();
        try {
            var server = server("server", arguments -> {
                throw new IllegalStateException("cannot set data breakpoints");
            });
            handler.initialize(server, supported()).join();
            handler.registerBreakpoint(breakpoint("7:count", server, false));

            assertEquals(List.of("invalid 7:count: cannot set data breakpoints"), shown);
        } finally {
            handler.dispose();
        }
    }

    public void testTheIconsAreClearedWhenTheSessionEnds() throws Exception {
        var handler = handler();
        try {
            var server = server("server", arguments -> new Breakpoint[0]);
            handler.initialize(server, supported()).join();
            handler.registerBreakpoint(breakpoint("9:size", server, true));
            shown.clear();

            sessionListeners.forEach(XDebugSessionListener::sessionStopped);

            // IntelliJ computes the icon of a line breakpoint again, not the icon of a data breakpoint
            assertEquals(List.of("cleared 9:size"), shown);
        } finally {
            handler.dispose();
        }
    }

    public void testTheBreakpointsWhichCantPersistAreRemovedWhenTheSessionEnds() throws Exception {
        var handler = handler();
        try {
            var server = server("server", arguments -> new Breakpoint[0]);
            handler.initialize(server, supported()).join();
            var count = breakpoint("7:count", server, false);
            var disabled = breakpoint("8:total", server, false);
            WriteAction.run(() -> disabled.setEnabled(false));
            var persisting = breakpoint("9:size", server, true);
            var otherSession = breakpoint("7:other", server("other", arguments -> new Breakpoint[0]), false);
            handler.registerBreakpoint(count);
            handler.registerBreakpoint(persisting);
            // the session is disposed before it ends
            handler.dispose();

            sessionListeners.forEach(XDebugSessionListener::sessionStopped);

            // those of this session which can't persist are removed, enabled or not; the one which can persist is kept,
            // and is no longer bound to the DAP server of the session
            assertSameElements(dataBreakpoints(), persisting, otherSession);
            assertNull(persisting.getProperties().getServer());
            assertNotNull(otherSession.getProperties().getServer());
            // and they are not saved with the project
            var state = ((XBreakpointManagerImpl) XDebuggerManager.getInstance(getProject()).getBreakpointManager())
                    .saveState(new BreakpointManagerState());
            assertEquals(2, state.getBreakpoints().stream().filter(saved -> saved.getTypeId().equals("dap-data-breakpoint")).count());
        } finally {
            handler.dispose();
        }
    }

    public void testABreakpointLeftOverByASessionWhichDidntEndIsRemoved() throws Exception {
        // as loaded with the project, after an IDE which stopped without ending the session: no DAP server
        var leftOver = breakpoint("7:count", null, false);
        var persisting = breakpoint("8:total", null, true);
        var otherConfiguration = breakpoint("9:size", null, true);
        WriteAction.run(() -> otherConfiguration.getProperties().setServerId("another-server"));

        var handler = handler();
        try {
            for (var breakpoint : List.of(leftOver, persisting, otherConfiguration)) {
                handler.registerBreakpoint(breakpoint);
            }
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
            assertSameElements(dataBreakpoints(), persisting, otherConfiguration);

            // the one which can persist is sent to the DAP server of its configuration when it starts
            handler.initialize(server("server", arguments -> new Breakpoint[0]), supported()).join();
            assertEquals(List.of(new Request("server", List.of("8:total"))), requests);
        } finally {
            handler.dispose();
        }
    }

    public void testTheBreakpointsWhichCantPersistAreRemovedWhenTheProjectCloses() throws Exception {
        var server = server("server", arguments -> new Breakpoint[0]);
        breakpoint("7:count", server, false);
        var persisting = breakpoint("8:total", server, true);

        // a debug session which still runs ends with the project, maybe after the project is saved
        new DAPProjectLifecycleListener().projectClosingBeforeSave(getProject());

        assertEquals(List.of(persisting), dataBreakpoints());
        assertNull(persisting.getProperties().getServer());
    }

    private static Capabilities supported() {
        var capabilities = new Capabilities();
        capabilities.setSupportsDataBreakpoints(true);
        return capabilities;
    }

    private static DataBreakpointInfoResponse info(@Nullable String dataId,
                                                   @Nullable String description,
                                                   DataBreakpointAccessType @Nullable [] accessTypes,
                                                   @Nullable Boolean canPersist) {
        var info = new DataBreakpointInfoResponse();
        info.setDataId(dataId);
        if (description != null) {
            // LSP4J requires it, which a DAP server may leave out
            info.setDescription(description);
        }
        info.setAccessTypes(accessTypes);
        info.setCanPersist(canPersist);
        return info;
    }

    private static Breakpoint answer(int id, boolean verified, String message) {
        var breakpoint = new Breakpoint();
        breakpoint.setId(id);
        breakpoint.setVerified(verified);
        breakpoint.setMessage(message);
        return breakpoint;
    }

    private static List<String> describe(SetDataBreakpointsArguments arguments) {
        return Arrays.stream(arguments.getBreakpoints())
                .map(DAPDataBreakpointHandlerTest::describe)
                .toList();
    }

    private static String describe(DataBreakpoint breakpoint) {
        var description = new StringBuilder(breakpoint.getDataId());
        description.append(" ").append(breakpoint.getAccessType());
        if (breakpoint.getCondition() != null) {
            description.append(" if ").append(breakpoint.getCondition());
        }
        if (breakpoint.getHitCondition() != null) {
            description.append(" hit ").append(breakpoint.getHitCondition());
        }
        return description.toString();
    }

    private List<XBreakpoint<DAPDataBreakpointProperties>> dataBreakpoints() {
        return List.copyOf(XDebuggerManager.getInstance(getProject()).getBreakpointManager().getBreakpoints(DAPDataBreakpointType.class));
    }

    /**
     * Adds a data breakpoint on the variable of the given container, as "Break on Value Change" does.
     */
    private XBreakpoint<DAPDataBreakpointProperties> add(DAPDataBreakpointHandler handler,
                                                          IDebugProtocolServer server,
                                                          int variablesReference,
                                                          String name) {
        var added = handler.addDataBreakpoint(server, DAPDataBreakpointHandler.createDataBreakpointInfoArguments(variablesReference, name, 1));
        PlatformTestUtil.waitWithEventsDispatching("The data breakpoint is not added", added::isDone, 10);
        return added.join();
    }

    /**
     * Returns a data breakpoint of the project, on the given data of the given DAP server, which writes stop at.
     */
    private XBreakpoint<DAPDataBreakpointProperties> breakpoint(String dataId,
                                                                 @Nullable IDebugProtocolServer server,
                                                                 boolean canPersist) {
        var type = XDebuggerUtil.getInstance().findBreakpointType(DAPDataBreakpointType.class);
        var properties = new DAPDataBreakpointProperties(dataId, dataId, DataBreakpointAccessType.WRITE, canPersist, SERVER_ID);
        properties.setServer(server);
        return WriteAction.compute(() -> XDebuggerManager.getInstance(getProject()).getBreakpointManager()
                .addBreakpoint(type, properties));
    }

    private DAPDataBreakpointHandler handler() throws Exception {
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
                return SERVER_ID;
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
        var session = (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class}, (p, m, a) -> {
            if (m.getName().equals("addSessionListener")) {
                sessionListeners.add((XDebugSessionListener) a[0]);
            }
            return null;
        });
        return new DAPDataBreakpointHandler(session, descriptor, getProject()) {
            @Override
            protected void setBreakpointVerified(XBreakpoint<DAPDataBreakpointProperties> ijBreakpoint) {
                shown.add("verified " + ijBreakpoint.getProperties().getDataId());
            }

            @Override
            protected void setBreakpointInvalid(XBreakpoint<DAPDataBreakpointProperties> ijBreakpoint, String message) {
                shown.add("invalid " + ijBreakpoint.getProperties().getDataId() + ": " + message);
            }

            @Override
            protected void clearIcon(XBreakpoint<?> ijBreakpoint) {
                if (ijBreakpoint.getProperties() instanceof DAPDataBreakpointProperties properties) {
                    shown.add("cleared " + properties.getDataId());
                }
            }

            @Override
            protected void reportRefusal(String reason) {
                refusals.add(reason);
            }
        };
    }

    private IDebugProtocolServer server(String name, Function<SetDataBreakpointsArguments, Breakpoint[]> answers) {
        return server(name, arguments -> {
            throw new AssertionError("dataBreakpointInfo");
        }, answers);
    }

    /**
     * Returns a DAP server, which the requests name, which answers 'dataBreakpointInfo' and 'setDataBreakpoints' with
     * the given functions: one which throws an IllegalStateException fails the request.
     */
    private IDebugProtocolServer server(String name,
                                        Function<DataBreakpointInfoArguments, DataBreakpointInfoResponse> infos,
                                        Function<SetDataBreakpointsArguments, Breakpoint[]> answers) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(), new Class[]{IDebugProtocolServer.class}, (p, m, a) -> {
            switch (m.getName()) {
                case "dataBreakpointInfo" -> {
                    var arguments = (DataBreakpointInfoArguments) a[0];
                    asked.add(arguments);
                    try {
                        return CompletableFuture.completedFuture(infos.apply(arguments));
                    } catch (IllegalStateException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                }
                case "setDataBreakpoints" -> {
                    var arguments = (SetDataBreakpointsArguments) a[0];
                    sent.add(arguments);
                    requests.add(new Request(name, Arrays.stream(arguments.getBreakpoints()).map(DataBreakpoint::getDataId).toList()));
                    Breakpoint[] breakpoints;
                    try {
                        breakpoints = answers.apply(arguments);
                    } catch (IllegalStateException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                    var response = new SetDataBreakpointsResponse();
                    response.setBreakpoints(breakpoints);
                    return CompletableFuture.completedFuture(response);
                }
                case "equals" -> {
                    return p == a[0];
                }
                case "hashCode" -> {
                    return System.identityHashCode(p);
                }
                case "toString" -> {
                    return name;
                }
                default -> throw new AssertionError(m.getName());
            }
        });
    }
}
