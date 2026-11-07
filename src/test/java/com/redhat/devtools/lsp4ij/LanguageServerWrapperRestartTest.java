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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockConnectionProvider;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.CannotStartProcessException;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Test for the restart attempts of {@link LanguageServerWrapper} after a start failure.
 *
 * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/1673">#1673</a>
 */
public class LanguageServerWrapperRestartTest extends UsefulTestCase {

    private LSPCodeInsightTestFixture myFixture;
    private FailingOnceServerDefinition serverDefinition;

    public void testStartDoesNotAbortRestartInFlight() {
        var wrapper = new LanguageServerWrapper(myFixture.getProject(), serverDefinition);
        Disposer.register(getTestRootDisposable(), wrapper);

        // The first start fails: serverError is set.
        wrapper.start();
        waitUntil(() -> wrapper.getServerError() != null && wrapper.getServerStatus() == ServerStatus.stopped,
                "the first start should fail");
        assertEquals(1, serverDefinition.connectionCount.get());

        // The next start() launches a restart attempt which initializes slowly.
        serverDefinition.getServer().setTimeToProceedQueries(2000);
        wrapper.start();
        waitUntil(() -> serverDefinition.connectionCount.get() == 2, "the restart attempt should be launched");
        assertEquals(1, wrapper.getNumberOfRestartAttempts());

        // Further start() calls while the attempt is still initializing must not abort it and spawn new processes.
        for (int i = 0; i < 5; i++) {
            wrapper.start();
        }
        assertEquals(2, serverDefinition.connectionCount.get());
        assertEquals(1, wrapper.getNumberOfRestartAttempts());

        // The restart attempt completes.
        waitUntil(() -> wrapper.getServerStatus() == ServerStatus.started, "the restart attempt should start the server");
        assertNull(wrapper.getServerError());
        assertEquals(2, serverDefinition.connectionCount.get());
        assertEquals(1, wrapper.getNumberOfRestartAttempts());
        serverDefinition.getServer().setTimeToProceedQueries(0);

        wrapper.stop();
        waitUntil(() -> wrapper.getServerStatus() == ServerStatus.stopped, "the server should stop");
    }

    public void testAStartWhichFailsBeforeStartReturnsStopsTheServer() throws Exception {
        var wrapper = new LanguageServerWrapper(myFixture.getProject(), serverDefinition);
        Disposer.register(getTestRootDisposable(), wrapper);

        // The first connection fails before start() has returned, as when the pooled thread which creates it fails
        // at once
        var initialized = runPooledThreadTasksHere(wrapper::getInitializedServer);
        waitUntil(() -> wrapper.getServerError() != null && wrapper.getServerStatus() == ServerStatus.stopped,
                "the failed start should stop the server");
        // The caller gets no server, not a NullPointerException from the failure handler
        assertNull(initialized.get(10, TimeUnit.SECONDS));

        // The next start starts the server
        wrapper.start();
        waitUntil(() -> wrapper.getServerStatus() == ServerStatus.started, "the next start should start the server");
        assertNull(wrapper.getServerError());
        assertEquals(2, serverDefinition.connectionCount.get());

        wrapper.stop();
        waitUntil(() -> wrapper.getServerStatus() == ServerStatus.stopped, "the server should stop");
    }

    public void testAReplacedAttemptDoesNotInstallItsServerWhenItsInitializeReplyComes() {
        var definition = new ScriptedServerDefinition();
        var wrapper = new LanguageServerWrapper(myFixture.getProject(), definition);
        Disposer.register(getTestRootDisposable(), wrapper);

        // The start task runs in this thread, so the first attempt has sent initialize when start() returns
        runPooledThreadTasksHere(() -> {
            wrapper.start();
            return null;
        });
        var first = definition.servers.get(0);
        assertTrue(first.initializeSent);

        // A stop replaces the first attempt, and a newer start sends the second attempt's initialize. The start chain's
        // stages run in this thread as the test answers it.
        wrapper.stop();
        runPooledThreadTasksHere(() -> {
            wrapper.start();
            return null;
        });
        var second = definition.servers.get(1);
        second.reply();
        assertEquals(ServerStatus.started, wrapper.getServerStatus());
        assertSame(second, wrapper.getLanguageServer());

        // The first attempt's reply comes after the stop replaced it
        first.reply();
        assertSame("the replaced attempt must not install its server", second, wrapper.getLanguageServer());
        assertEquals(ServerStatus.started, wrapper.getServerStatus());
        assertFalse("the replaced attempt must not send its server initialized", first.initialized);

        wrapper.stop();
    }

    public void testAConnectionQueuedBeforeAStopNeitherStartsTheServerAgainNorConnectsTheFile() {
        var definition = new ScriptedServerDefinition();
        var wrapper = new LanguageServerWrapper(myFixture.getProject(), definition);
        Disposer.register(getTestRootDisposable(), wrapper);
        var file = myFixture.addFileToProject("queued.foo", "queued").getVirtualFile();

        // The server starts, with its start task in this thread and its initialize answered by the test
        runPooledThreadTasksHere(() -> {
            wrapper.start();
            return null;
        });
        definition.servers.get(0).reply();
        assertEquals(ServerStatus.started, wrapper.getServerStatus());

        // The connection of a file is queued, and a stop comes before it runs
        var queued = new ArrayList<Runnable>();
        wrapper.setConnectionExecutor(queued::add);
        var connected = wrapper.connect(file, new LanguageServerWrapper.LSPFileConnectionInfo(null, null, null, true));
        assertEquals(1, queued.size());
        wrapper.stop();

        // The connection runs after the stop
        queued.forEach(Runnable::run);
        assertTrue("the connection must end without waiting for a server", connected.isDone());
        assertNull(connected.join());
        assertEquals("the connection must not start the server again", 1, definition.servers.size());
        assertNull("the file must not be connected", wrapper.getOpenedDocument(wrapper.toUri(file), false));
    }

    /**
     * Calls the action, running each task which this thread gives {@link Application#executeOnPooledThread(Runnable)}
     * before that call returns, as if a pooled thread ran it at once. For the call, the application is replaced by a
     * proxy which delegates to it.
     */
    private static <T> T runPooledThreadTasksHere(Supplier<T> action) {
        var application = ApplicationManager.getApplication();
        var thread = Thread.currentThread();
        var inline = new AtomicBoolean(true);
        var inlineApplication = (Application) Proxy.newProxyInstance(
                application.getClass().getClassLoader(),
                publicInterfaces(application.getClass()),
                (proxy, method, args) -> {
                    if (inline.get()
                            && Thread.currentThread() == thread
                            && method.getName().equals("executeOnPooledThread")
                            && method.getParameterTypes()[0] == Runnable.class) {
                        ((Runnable) args[0]).run();
                        return CompletableFuture.completedFuture(null);
                    }
                    try {
                        return method.invoke(application, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        var restore = Disposer.newDisposable();
        ApplicationManager.setApplication(inlineApplication, restore);
        try {
            return action.get();
        } finally {
            inline.set(false);
            Disposer.dispose(restore);
        }
    }

    private static Class<?>[] publicInterfaces(Class<?> type) {
        var interfaces = new LinkedHashSet<Class<?>>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> i : c.getInterfaces()) {
                if (Modifier.isPublic(i.getModifiers())) {
                    interfaces.add(i);
                }
            }
        }
        return interfaces.toArray(Class<?>[]::new);
    }

    private static void waitUntil(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                fail("Timeout: " + message);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted: " + message);
            }
        }
    }

    /**
     * Server definition whose first connection fails to start.
     */
    private static class FailingOnceServerDefinition extends MockLanguageServerDefinition {

        private final AtomicInteger connectionCount = new AtomicInteger();

        FailingOnceServerDefinition() {
            super("test-server-restart", false);
        }

        @Override
        public @NotNull StreamConnectionProvider createConnectionProvider(@NotNull Project project) {
            if (connectionCount.incrementAndGet() == 1) {
                return new MockConnectionProvider(getServer()) {
                    @Override
                    public void start() throws CannotStartProcessException {
                        throw new CannotStartProcessException("Simulated start failure");
                    }
                };
            }
            return super.createConnectionProvider(project);
        }
    }

    /**
     * Server definition whose every connection is to a new {@link ScriptedServer}, in place of the process's server,
     * so that the test answers each attempt's initialize.
     */
    private static class ScriptedServerDefinition extends MockLanguageServerDefinition {

        private final List<ScriptedServer> servers = new CopyOnWriteArrayList<>();

        ScriptedServerDefinition() {
            super("test-server-scripted", false);
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            return new LSPClientFeatures() {
                @Override
                public <S extends LanguageServer> @NotNull Launcher.Builder<S> createLauncherBuilder() {
                    return new Launcher.Builder<>() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public Launcher<S> create() {
                            var server = new ScriptedServer();
                            servers.add(server);
                            return new Launcher<>() {
                                @Override
                                public Future<Void> startListening() {
                                    return new CompletableFuture<>();
                                }

                                @Override
                                public S getRemoteProxy() {
                                    return (S) server;
                                }

                                @Override
                                public RemoteEndpoint getRemoteEndpoint() {
                                    return null;
                                }
                            };
                        }
                    };
                }
            };
        }
    }

    /**
     * Language server whose initialize reply comes when the test calls {@link #reply()}, in the test's thread.
     */
    private static class ScriptedServer implements LanguageServer {

        private final CompletableFuture<InitializeResult> initializeResult = new CompletableFuture<>();
        private volatile boolean initializeSent;
        private volatile boolean initialized;

        void reply() {
            initializeResult.complete(new InitializeResult(new ServerCapabilities()));
        }

        @Override
        public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
            initializeSent = true;
            return initializeResult;
        }

        @Override
        public void initialized(InitializedParams params) {
            initialized = true;
        }

        @Override
        public CompletableFuture<Object> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void exit() {
        }

        @Override
        public TextDocumentService getTextDocumentService() {
            return MockLanguageServer.INSTANCE.getTextDocumentService();
        }

        @Override
        public WorkspaceService getWorkspaceService() {
            return MockLanguageServer.INSTANCE.getWorkspaceService();
        }
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        serverDefinition = new FailingOnceServerDefinition();
        List<ServerMappingSettings> mappings = List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.foo"), null));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    @Override
    protected void tearDown() throws Exception {
        serverDefinition.getServer().waitBeforeTearDown();
        LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
        try {
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }

}
