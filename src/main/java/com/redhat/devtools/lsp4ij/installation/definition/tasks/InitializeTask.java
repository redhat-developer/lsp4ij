/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.installation.ServerStarter;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.internal.IntelliJPlatformUtils;
import com.redhat.devtools.lsp4ij.server.ProcessDataProvider;
import com.redhat.devtools.lsp4ij.server.SocketStreamConnectionProvider;
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArgumentsPathFormat;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.debug.DebugLauncher;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Installer task which checks that the language server or the debug adapter of the installer starts and answers the
 * {@code initialize} request. It starts the server as LSP4IJ starts it, with the command line, working directory,
 * environment and transport of its definition, sends {@code initialize}, the LSP request to a language server and the
 * DAP request to a debug adapter, and succeeds only if the server answers within the timeout: a server which ends,
 * doesn't answer, or can't be started, isn't installed. An answer which is an error shows that the server runs, and
 * succeeds too. The task then ends the server, with {@code shutdown} and {@code exit} (DAP: {@code disconnect}), and
 * destroys its process if it hasn't ended shortly after, also when the installation is cancelled.
 * <p>
 * A language server which runs already, which LSP4IJ connects to and doesn't start, isn't touched: the task succeeds.
 *
 * <pre>
 * "initialize": {
 *   "name": "Check that ruby-lsp starts",
 *   "timeout": 10000
 * }
 * </pre>
 */
public class InitializeTask extends InstallerTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(InitializeTask.class);

    /**
     * How long the task waits for the answer to {@code initialize}, from the start of the server, without a
     * {@code timeout}, in milliseconds: long enough for a server which takes some seconds to start, as one on a JVM.
     */
    public static final int DEFAULT_TIMEOUT = 30_000;

    // How long the server has to answer shutdown (DAP: disconnect), and then to end, before its process is destroyed
    private static final long END_TIMEOUT_MILLIS = 2_000;
    // How long the task waits for the process of a destroyed server to end
    private static final long DESTROY_TIMEOUT_MILLIS = 5_000;
    // How long an answer which a server sent before it ended may still take to be read
    private static final long ANSWER_OF_ENDED_SERVER_MILLIS = 500;
    private static final long POLL_MILLIS = 50;

    private final int timeout;

    public InitializeTask(@Nullable String id,
                          @Nullable String name,
                          @Nullable InstallerTask onFail,
                          @Nullable InstallerTask onSuccess,
                          @Nullable Integer timeout,
                          @NotNull ServerInstallerDescriptor serverInstallerDescriptor) {
        super(id, name, onFail, onSuccess, serverInstallerDescriptor);
        this.timeout = timeout != null && timeout > 0 ? timeout : DEFAULT_TIMEOUT;
    }

    /**
     * Returns how long the task waits for the answer to {@code initialize}, from the start of the server.
     *
     * @return how long the task waits for the answer to {@code initialize}, in milliseconds.
     */
    public int getTimeout() {
        return timeout;
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        var commandLineUpdater = context.getCommandLineUpdater();
        ServerStarter starter = commandLineUpdater != null ? commandLineUpdater.getServerStarter() : null;
        if (starter == null) {
            context.printError("The installer doesn't know how LSP4IJ starts the server, so it can't check it.");
            return false;
        }
        @Nullable Project contextProject = context.getProject();
        Project project = contextProject != null ? contextProject : ProjectManager.getInstance().getDefaultProject();
        try {
            StreamConnectionProvider provider = starter.createConnectionProvider(project);
            if (provider == null) {
                context.print("LSP4IJ connects to the server, which runs already, and doesn't start it: there is nothing to check.");
                return true;
            }
            return check(provider, starter, project, context);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            // As an invalid command, or settings which the server can't be started with
            context.printError("The server can't be started: ", e);
            return false;
        }
    }

    /**
     * Starts the server with the given provider, sends it {@code initialize}, and returns true if it answers it before
     * the timeout. The server is gone once this returns.
     */
    private boolean check(@NotNull StreamConnectionProvider provider,
                          @NotNull ServerStarter starter,
                          @NotNull Project project,
                          @NotNull InstallerContext context) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        if (provider instanceof SocketStreamConnectionProvider socketProvider) {
            // Waiting for the connection is part of the timeout
            socketProvider.setStartTimeout(Duration.ofMillis(timeout));
        }
        // As in the Log tab of the LSP console
        provider.addLogErrorHandler(context::printError);
        ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .setNameFormat("LSP4IJ installer initialize #%d")
                .setDaemon(true)
                .setThreadFactory(runnable -> {
                    var thread = new Thread(runnable);
                    // Worker threads must not inherit the plugin classloader, or they pin it and block dynamic unload
                    thread.setContextClassLoader(ApplicationManager.class.getClassLoader());
                    return thread;
                })
                .build());
        Handshake handshake = null;
        try {
            if (!start(provider, executor, deadline, context)) {
                return false;
            }
            handshake = starter.protocol() == ServerStarter.Protocol.LSP
                    ? new LanguageServerHandshake(provider, executor, createInitializeParams(provider, project))
                    : new DebugAdapterHandshake(provider, executor, starter.id());
            if (!answersInitialize(handshake, provider, deadline, context)) {
                return false;
            }
            end(handshake, provider, context);
            return true;
        } finally {
            destroy(provider, handshake, executor);
        }
    }

    /**
     * Starts the server with the given provider, and returns true if it started before the deadline, and false if it
     * can't be started.
     */
    private boolean start(@NotNull StreamConnectionProvider provider,
                          @NotNull ExecutorService executor,
                          long deadline,
                          @NotNull InstallerContext context) {
        try {
            // On another thread, so that a cancellation or the timeout stops the provider while it waits to connect
            await(CompletableFuture.runAsync(provider::start, executor), deadline, null, context);
            return true;
        } catch (ExecutionException e) {
            context.printError("The server can't be started: " + Objects.requireNonNullElse(e.getCause(), e).getMessage());
            return false;
        } catch (TimeoutException | ServerEndedException e) {
            context.printError("The server didn't start within " + timeout + " ms.");
            return false;
        } finally {
            printCommand(provider, context);
        }
    }

    /**
     * Sends {@code initialize} and returns true if the server answers it before the deadline, with a result or an
     * error, and false if it ends first or the request can't be sent.
     */
    private boolean answersInitialize(@NotNull Handshake handshake,
                                      @NotNull StreamConnectionProvider provider,
                                      long deadline,
                                      @NotNull InstallerContext context) {
        try {
            String serverInfo = await(handshake.initialize(), deadline, provider, context);
            context.print("The server answered initialize" + serverInfo + ".");
            return true;
        } catch (TimeoutException e) {
            context.printError("The server didn't answer initialize within " + timeout + " ms.");
            return false;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ResponseErrorException error) {
                if (handshake.echoed()) {
                    // The error is the one which the task gave to its own request
                    context.printError("The command sends back what it gets, as cat does: it isn't the server.");
                    return false;
                }
                context.print("The server answered initialize with an error, which shows that it runs: "
                        + error.getMessage());
                return true;
            }
            context.printError("The server didn't answer initialize: " + Objects.requireNonNullElse(e.getCause(), e).getMessage());
            return false;
        } catch (ServerEndedException e) {
            context.printError("The server ended before it answered initialize.");
            return false;
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            // As when the server ended before the request could be written
            context.printError("The server didn't get initialize: ", e);
            return false;
        }
    }

    /**
     * Asks the server to end, as LSP4IJ does when it stops it, and waits a moment for it to end.
     */
    private static void end(@NotNull Handshake handshake,
                            @NotNull StreamConnectionProvider provider,
                            @NotNull InstallerContext context) {
        try {
            await(handshake.requestEnd(), deadlineIn(END_TIMEOUT_MILLIS), provider, context);
        } catch (ExecutionException | TimeoutException | ServerEndedException e) {
            // Destroyed below, if it doesn't end
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException e) {
            // The server ended already
        }
        try {
            handshake.afterEnd();
        } catch (RuntimeException e) {
            // The server ended already
        }
        long deadline = deadlineIn(END_TIMEOUT_MILLIS);
        while (provider.isAlive() && System.nanoTime() - deadline < 0) {
            checkCanceled(context);
            sleep(POLL_MILLIS);
        }
        if (provider.isAlive()) {
            context.print("The server didn't end once asked to: its process is destroyed.");
        }
    }

    /**
     * Stops the server, which destroys its process if it still runs, waits for its process to end, and stops the
     * threads of the task.
     */
    private static void destroy(@NotNull StreamConnectionProvider provider,
                                @Nullable Handshake handshake,
                                @NotNull ExecutorService executor) {
        Long pid = provider instanceof ProcessDataProvider processData ? processData.getPid() : null;
        try {
            provider.stop();
        } catch (RuntimeException e) {
            LOGGER.warn("Cannot stop the server which the initialize task of an installer started", e);
        }
        if (handshake != null) {
            handshake.close();
        }
        executor.shutdownNow();
        if (pid != null) {
            ProcessHandle.of(pid).ifPresent(InitializeTask::awaitExit);
        }
    }

    private static void awaitExit(@NotNull ProcessHandle process) {
        try {
            process.onExit().get(DESTROY_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOGGER.warn("The process {} of the server which the initialize task of an installer started didn't end", process.pid());
        }
    }

    /**
     * Waits for the given future until the deadline, and returns its value. It ends with a
     * {@link ProcessCanceledException} if the installation is cancelled, and with a {@link ServerEndedException} if the
     * server of the given provider ended without completing the future.
     */
    private static <T> T await(@NotNull CompletableFuture<T> future,
                               long deadline,
                               @Nullable StreamConnectionProvider provider,
                               @NotNull InstallerContext context) throws ExecutionException, TimeoutException, ServerEndedException {
        try {
            while (true) {
                checkCanceled(context);
                try {
                    return future.get(POLL_MILLIS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException notYet) {
                    if (provider != null && !provider.isAlive()) {
                        // What the server sent before it ended may still be read
                        try {
                            return future.get(ANSWER_OF_ENDED_SERVER_MILLIS, TimeUnit.MILLISECONDS);
                        } catch (TimeoutException ended) {
                            throw new ServerEndedException();
                        }
                    }
                    if (System.nanoTime() - deadline >= 0) {
                        throw notYet;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException(e);
        }
    }

    private static void checkCanceled(@NotNull InstallerContext context) {
        ProgressManager.checkCanceled();
        var indicator = context.getProgressIndicator();
        if (indicator != null) {
            indicator.checkCanceled();
        }
    }

    private static long deadlineIn(long millis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProcessCanceledException(e);
        }
    }

    private static void printCommand(@NotNull StreamConnectionProvider provider, @NotNull InstallerContext context) {
        List<String> commands = provider instanceof ProcessDataProvider processData ? processData.getCommands() : null;
        String connection = provider.getConnectionDescription();
        String command = commands != null && !commands.isEmpty() ? "> " + String.join(" ", commands) : null;
        String printed = Stream.of(command, connection != null ? "(" + connection + ")" : null)
                .filter(Objects::nonNull)
                .reduce((first, second) -> first + " " + second)
                .orElse(null);
        context.print(printed);
    }

    /**
     * Returns the {@code initialize} params of a language server: those of LSP4IJ, with the initialization options of
     * the user's settings and the project's folder as root, without client capabilities, so that the server only
     * answers.
     */
    private static @NotNull InitializeParams createInitializeParams(@NotNull StreamConnectionProvider provider,
                                                                    @NotNull Project project) {
        var params = new InitializeParams();
        params.setProcessId((int) ProcessHandle.current().pid());
        params.setClientInfo(IntelliJPlatformUtils.getClientInfo());
        params.setCapabilities(new ClientCapabilities());
        params.setInitializationOptions(provider.getInitializationOptions(null));
        String basePath = project.isDefault() ? null : project.getBasePath();
        if (basePath != null) {
            String rootUri = LSPIJUtils.toUri(new File(basePath)).toASCIIString();
            params.setRootUri(rootUri);
            params.setRootPath(basePath);
            params.setWorkspaceFolders(List.of(new WorkspaceFolder(rootUri, project.getName())));
        }
        return params;
    }

    /**
     * Returns the name and the version of the server from its answer to the LSP {@code initialize}, as " (gopls
     * v0.16.2)", or an empty string if the answer has none.
     */
    private static @NotNull String describeServer(@Nullable JsonElement initializeResult) {
        JsonElement serverInfo = initializeResult != null && initializeResult.isJsonObject()
                ? initializeResult.getAsJsonObject().get("serverInfo")
                : null;
        if (serverInfo == null || !serverInfo.isJsonObject()) {
            return "";
        }
        var info = serverInfo.getAsJsonObject();
        String description = Stream.of("name", "version")
                .map(info::get)
                .filter(value -> value != null && value.isJsonPrimitive())
                .map(JsonElement::getAsString)
                .reduce((name, version) -> name + " " + version)
                .orElse("");
        return description.isEmpty() ? "" : " (" + description + ")";
    }

    /**
     * Ends the wait of {@link #await} when the server ended without completing the future.
     */
    private static final class ServerEndedException extends Exception {
    }

    /**
     * The messages which the task sends to the server: {@code initialize}, and those which end the server.
     */
    private interface Handshake {

        /**
         * Sends {@code initialize}, and returns the name and the version of the server from its answer, for the
         * console.
         */
        @NotNull CompletableFuture<String> initialize();

        /**
         * Sends the request which asks the server to end: {@code shutdown}, or {@code disconnect}.
         */
        @NotNull CompletableFuture<?> requestEnd();

        /**
         * Sends what follows the answer to {@link #requestEnd()}: {@code exit}, or nothing.
         */
        void afterEnd();

        /**
         * Returns true if the server sent back the {@code initialize} request, as a command which sends back what it
         * gets, as {@code cat}, does: the answer to it is then the error which the task gave to its own request.
         */
        boolean echoed();

        /**
         * Stops reading the messages of the server.
         */
        void close();
    }

    /**
     * Returns a wrapper of the consumers of the messages, which sets the given flag when the server sends the
     * {@code initialize} request, which only a server that sends back what it gets does.
     */
    private static @NotNull Function<MessageConsumer, MessageConsumer> detectingEcho(@NotNull AtomicBoolean echoed) {
        return consumer -> !(consumer instanceof RemoteEndpoint) ? consumer : message -> {
            if (message instanceof RequestMessage request && "initialize".equals(request.getMethod())) {
                echoed.set(true);
            }
            consumer.consume(message);
        };
    }

    /**
     * The LSP requests which the task sends. The answer to {@code initialize} is kept as JSON: any answer will do.
     */
    interface InitializeCheckLanguageServer {

        @JsonRequest("initialize")
        CompletableFuture<JsonElement> initialize(InitializeParams params);

        @JsonRequest("shutdown")
        CompletableFuture<Object> shutdown();

        @JsonNotification("exit")
        void exit();
    }

    private static final class LanguageServerHandshake implements Handshake {

        private final @NotNull InitializeCheckLanguageServer server;
        private final @NotNull InitializeParams params;
        private final @NotNull AtomicBoolean echoed = new AtomicBoolean();
        private final @NotNull Future<Void> listening;

        LanguageServerHandshake(@NotNull StreamConnectionProvider provider,
                                @NotNull ExecutorService executor,
                                @NotNull InitializeParams params) {
            Launcher<InitializeCheckLanguageServer> launcher = new Launcher.Builder<InitializeCheckLanguageServer>()
                    .setLocalService(new SilentLanguageClient())
                    .setRemoteInterface(InitializeCheckLanguageServer.class)
                    .setInput(provider.getInputStream())
                    .setOutput(provider.getOutputStream())
                    .setExecutorService(executor)
                    .wrapMessages(detectingEcho(echoed))
                    .create();
            this.server = launcher.getRemoteProxy();
            this.params = params;
            this.listening = launcher.startListening();
        }

        @Override
        public @NotNull CompletableFuture<String> initialize() {
            return server.initialize(params).thenApply(InitializeTask::describeServer);
        }

        @Override
        public @NotNull CompletableFuture<?> requestEnd() {
            return server.shutdown();
        }

        @Override
        public void afterEnd() {
            server.exit();
        }

        @Override
        public boolean echoed() {
            return echoed.get();
        }

        @Override
        public void close() {
            listening.cancel(true);
        }
    }

    private static final class DebugAdapterHandshake implements Handshake {

        private final @NotNull IDebugProtocolServer server;
        private final @NotNull String adapterId;
        private final @NotNull AtomicBoolean echoed = new AtomicBoolean();
        private final @NotNull Future<Void> listening;

        DebugAdapterHandshake(@NotNull StreamConnectionProvider provider,
                              @NotNull ExecutorService executor,
                              @NotNull String adapterId) {
            InputStream input = provider.getInputStream();
            OutputStream output = provider.getOutputStream();
            Launcher<IDebugProtocolServer> launcher = new DebugLauncher.Builder<IDebugProtocolServer>()
                    .setLocalService(new IDebugProtocolClient() {
                    })
                    .setRemoteInterface(IDebugProtocolServer.class)
                    .setInput(input)
                    .setOutput(output)
                    .setExecutorService(executor)
                    .wrapMessages(detectingEcho(echoed))
                    .create();
            this.server = launcher.getRemoteProxy();
            this.adapterId = adapterId;
            this.listening = launcher.startListening();
        }

        @Override
        public @NotNull CompletableFuture<String> initialize() {
            // As a debug session, whose paths and lines a debug adapter may require, as Delve does
            var args = new InitializeRequestArguments();
            args.setClientID("lsp4ij");
            args.setClientName(IntelliJPlatformUtils.getClientInfo().getName());
            args.setAdapterID(adapterId);
            args.setPathFormat(InitializeRequestArgumentsPathFormat.PATH);
            args.setLinesStartAt1(true);
            args.setColumnsStartAt1(true);
            return server.initialize(args).thenApply(capabilities -> "");
        }

        @Override
        public @NotNull CompletableFuture<?> requestEnd() {
            return server.disconnect(new DisconnectArguments());
        }

        @Override
        public void afterEnd() {
            // The disconnect ends the session
        }

        @Override
        public boolean echoed() {
            return echoed.get();
        }

        @Override
        public void close() {
            listening.cancel(true);
        }
    }

    /**
     * The client of the language server, which ignores what the server sends it.
     */
    private static final class SilentLanguageClient implements LanguageClient {

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
        }
    }
}
