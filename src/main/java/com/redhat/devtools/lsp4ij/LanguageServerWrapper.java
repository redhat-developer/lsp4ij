/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.JsonObject;
import com.intellij.notification.Notification;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.WorkspaceFolderNotificationManager;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.console.explorer.TracingMessageConsumer;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils;
import com.redhat.devtools.lsp4ij.features.documentContent.DocumentContentFile;
import com.redhat.devtools.lsp4ij.features.files.AbstractLSPFileListener;
import com.redhat.devtools.lsp4ij.features.files.operations.FileOperationsManager;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationContext;
import com.redhat.devtools.lsp4ij.installation.ServerInstallationStatus;
import com.redhat.devtools.lsp4ij.installation.ServerInstaller;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.internal.VirtualFileCancelChecker;
import com.redhat.devtools.lsp4ij.internal.capabilities.ClientCapabilitiesFactory;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureManager;
import com.redhat.devtools.lsp4ij.internal.editor.EditorFeatureType;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.lifecycle.NullLanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.server.*;
import com.redhat.devtools.lsp4ij.server.capabilities.TextDocumentServerCapabilityRegistry;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static com.redhat.devtools.lsp4ij.LSPIJUtils.findResourceFor;
import static com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils.clearProblem;
import static com.redhat.devtools.lsp4ij.internal.ApplicationUtils.runCancellableReadAction;
import static com.redhat.devtools.lsp4ij.internal.CancellationSupport.showNotificationError;
import static com.redhat.devtools.lsp4ij.internal.IntelliJPlatformUtils.getClientInfo;

/**
 * Language server wrapper.
 */
public class LanguageServerWrapper implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerWrapper.class);

    private static final int MAX_NUMBER_OF_RESTART_ATTEMPTS = 20; // TODO move this max value in settings

    private static final @NotNull TextDocumentSyncOptions DEFAULT_SYNC_OPTIONS;

    static {
        DEFAULT_SYNC_OPTIONS = new TextDocumentSyncOptions();
        DEFAULT_SYNC_OPTIONS.setOpenClose(true);
        DEFAULT_SYNC_OPTIONS.setChange(TextDocumentSyncKind.None);
        DEFAULT_SYNC_OPTIONS.setSave(true);
    }

    @NotNull
    protected final Project initialProject;
    @NotNull
    protected final Map<URI /* file Uri */, OpenedDocument> openedDocuments;
    @NotNull
    protected final Map<URI /* file Uri */, ClosedDocument> closedDocuments;
    // Lock object to prevent deadlock when accessing both openedDocuments and closedDocuments
    private final Object documentsLock = new Object();
    // A new synchronizer must not restart at version 1 after close/reopen: a delayed edit for the
    // previous incarnation could then look current. LSP versions only have to increase, not be
    // consecutive, so one counter for all documents of this connection keeps them unique.
    private final AtomicInteger documentVersion = new AtomicInteger();
    // Guarded by documentsLock. Ordinary lookups promote scoped connections to the
    // existing editor/feature lifetime; a temporary request must never close those.
    private final Map<URI, ScopedDocumentConnection> scopedDocuments = new HashMap<>();

    private static final class ScopedDocumentConnection {
        private int owners;
        private boolean persistent;

        private ScopedDocumentConnection(boolean persistent) {
            this.persistent = persistent;
        }
    }

    private record DocumentConnectionClaim(URI uri, ScopedDocumentConnection connection,
                                           AtomicBoolean released) {
    }
    @Nullable
    protected final URI initialPath;
    private final @Nullable WorkspaceFolder instanceRoot;
    private volatile @Nullable String instanceLabel;
    // An instance of a definition with several is switched off on its own
    private volatile boolean instanceDisabled;

    // The last stop that runs in the background, for a stop that comes while it is running
    private CompletableFuture<Void> runningStop = CompletableFuture.completedFuture(null);
    protected final InitializeParams initParams = new InitializeParams();
    @NotNull
    private final LanguageServerDefinition serverDefinition;
    private final ExecutorService dispatcher;
    private final Object notificationLock = new Object();
    private @NotNull CompletableFuture<LanguageServer> lastNotification = CompletableFuture.completedFuture(null);
    private final ExecutorService listener;
    private final ExecutorService messageWriter;
    private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();
    /**
     * Map containing unregistration handlers for dynamic capability registrations.
     */
    private final @NotNull
    Map<String, Runnable> dynamicRegistrations = new HashMap<>();
    private final LSPFileListener fileListener;
    private final AtomicInteger keepAliveCounter = new AtomicInteger();
    protected StreamConnectionProvider lspStreamProvider;
    private volatile MessageBusConnection messageBusConnection;
    private Future<?> launcherFuture;
    private int numberOfRestartAttempts;
    private @Nullable CompletableFuture<Void> initializeFuture;
    private volatile LanguageServer languageServer;
    private LanguageClientImpl languageClient;
    private ServerCapabilities serverCapabilities;
    private volatile @Nullable Alarm stopAlarm = null;
    private ServerStatus serverStatus;
    private volatile boolean disposed;
    private LanguageServerException serverError;
    private Long currentProcessId;
    private List<String> currentProcessCommandLines;
    private boolean initiallySupportsWorkspaceFolders = false;
    private FileOperationsManager fileOperationsManager;
    private WorkspaceFolderNotificationManager workspaceFolderNotificationManager;

    private LSPClientFeatures clientFeatures;
    // error notification displayed when server start fails.
    private volatile @Nullable Notification errorNotification;
    private @Nullable TracingMessageConsumer tracing;
    private volatile @Nullable ConcurrentLinkedQueue<LSPTrace> traces;
    private @Nullable Alarm traceFlushAlarm;
    private volatile @Nullable InitializingContext currentInitializingContext;
    // Where the connection of a document runs once its start attempt is initialized: the executor that
    // thenComposeAsync uses by default, which a test can replace to run the connection when it chooses
    private @NotNull Executor connectionExecutor = CompletableFuture.completedFuture(null).defaultExecutor();
    private @NotNull TextDocumentSyncOptions syncOptions;

    /* Backwards compatible constructor */
    public LanguageServerWrapper(@NotNull Project project, @NotNull LanguageServerDefinition serverDefinition) {
        this(project, serverDefinition, null);
    }

    public LanguageServerWrapper(@NotNull Project project,
                                 @NotNull LanguageServerDefinition serverDefinition,
                                 @Nullable URI initialPath) {
        this(project, serverDefinition, initialPath, null);
    }

    /**
     * Creates the language server instance with the given root.
     *
     * @param project          the project.
     * @param serverDefinition the language server definition.
     * @param instanceRoot     the root of this instance.
     * @return the language server instance with the given root.
     * @see com.redhat.devtools.lsp4ij.features.workspaceFolder.WorkspaceFolderStrategy#hasServerInstances()
     */
    @ApiStatus.Internal
    public static @NotNull LanguageServerWrapper forInstanceRoot(@NotNull Project project,
                                                                 @NotNull LanguageServerDefinition serverDefinition,
                                                                 @NotNull WorkspaceFolder instanceRoot) {
        return new LanguageServerWrapper(project, serverDefinition, null, instanceRoot);
    }

    /**
     * Unified private constructor to set sensible defaults in all cases
     */
    private LanguageServerWrapper(@NotNull Project project,
                                  @NotNull LanguageServerDefinition serverDefinition,
                                  @Nullable URI initialPath,
                                  @Nullable WorkspaceFolder instanceRoot) {
        this.initialProject = project;
        this.initialPath = initialPath;
        this.instanceRoot = instanceRoot;
        this.serverDefinition = serverDefinition;
        this.openedDocuments = new ConcurrentHashMap<>();
        this.closedDocuments = new ConcurrentHashMap<>();
        this.fileListener = new LSPFileListener(this);
        VirtualFileManager.getInstance().addAsyncFileListener(fileListener, this);

        String projectName = sanitize(!serverDefinition.isSingleton() ? ("@" + project.getName()) : "");  //$NON-NLS-1$//$NON-NLS-2$
        if (instanceRoot != null) {
            projectName += sanitize("@" + instanceRoot.getName());
        }

        // Worker threads must not inherit the plugin classloader, or they pin it and block dynamic unload.
        var workerCtxClassLoader = ApplicationManager.class.getClassLoader();
        String dispatcherThreadNameFormat = "LS-" + serverDefinition.getId() + projectName + "#dispatcher"; //$NON-NLS-1$ //$NON-NLS-2$
        this.dispatcher = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat(dispatcherThreadNameFormat)
                .setThreadFactory(r -> {
                    var t = new Thread(r);
                    t.setContextClassLoader(workerCtxClassLoader);
                    return t;
                })
                .build());

        // Executor service passed through to the LSP4j layer when we attempt to start the LS. It will be used
        // to create a listener that sits on the input stream and processes inbound messages (responses, or server-initiated
        // requests).
        String messageWriterThreadNameFormat = "LS-" + serverDefinition.getId() + projectName + "#messageWriter"; //$NON-NLS-1$ //$NON-NLS-2$
        this.messageWriter = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat(messageWriterThreadNameFormat)
                .setThreadFactory(r -> {
                    var t = new Thread(r);
                    t.setContextClassLoader(workerCtxClassLoader);
                    return t;
                })
                .build());

        String listenerThreadNameFormat = "LS-" + serverDefinition.getId() + projectName + "#listener-%d"; //$NON-NLS-1$ //$NON-NLS-2$
        this.listener = Executors.newCachedThreadPool(new ThreadFactoryBuilder()
                .setNameFormat(listenerThreadNameFormat)
                .setThreadFactory(r -> {
                    var t = new Thread(r);
                    t.setContextClassLoader(workerCtxClassLoader);
                    return t;
                })
                .build());
        updateStatus(ServerStatus.none);

        // When project is disposed, we dispose the language server
        // But the language server should be disposed before because when project is closing
        // We do that to be sure that language server is disposed.
        Disposer.register(project, this);
    }

    /**
     * Removes '%' from the given name
     */
    private static String sanitize(String name) {
        return name.replace("%", "");
    }

    private static boolean supportsWorkspaceFolders(ServerCapabilities serverCapabilities) {
        return serverCapabilities != null && serverCapabilities.getWorkspace() != null
                && serverCapabilities.getWorkspace().getWorkspaceFolders() != null
                && Boolean.TRUE.equals(serverCapabilities.getWorkspace().getWorkspaceFolders().getSupported());
    }

    /**
     * Returns the parent process id (process id of Intellij).
     *
     * @return the parent process id (process id of Intellij).
     */
    private static int getParentProcessId() {
        return (int) ProcessHandle.current().pid();
    }

    @NotNull
    public Project getProject() {
        return initialProject;
    }

    void stopDispatcher() {
        this.dispatcher.shutdownNow();
        this.messageWriter.shutdownNow();

        // Only really needed for testing - the listener (an instance of ConcurrentMessageProcessor) should exit
        // as soon as the input stream from the LS is closed, and a cached thread pool will recycle idle
        // threads after a 60 second timeout - or immediately in response to JVM shutdown.
        // If we don't do this then a full test run will generate a lot of threads because we create new
        // instances of this class for each test
        this.listener.shutdownNow();

        // Wait briefly for threads to actually exit. All waits share one 2-second deadline.
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            if (!this.dispatcher.awaitTermination(Math.max(0L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                LOGGER.warn("Dispatcher executor for language server '{}' did not terminate within 2s; classloader unload may be blocked",
                        serverDefinition.getId());
            }
            if (!this.messageWriter.awaitTermination(Math.max(0L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                LOGGER.warn("Message writer executor for language server '{}' did not terminate within 2s; classloader unload may be blocked",
                        serverDefinition.getId());
            }
            if (!this.listener.awaitTermination(Math.max(0L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                LOGGER.warn("Listener executor for language server '{}' did not terminate within 2s; classloader unload may be blocked",
                        serverDefinition.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void stopAndDisable() {
        stopAndRefreshEditorFeature(true, true);
    }

    /**
     * Stops the language server and removes the code vision, inlay hints and folding of its open editors, as
     * {@link #stopAndDisable()} does, without switching it off: for a stop of its definition, which switches the
     * definition off instead.
     */
    synchronized void stopAndRefreshEditorFeature() {
        stopAndRefreshEditorFeature(true, false);
    }

    /**
     * Starts the language server if it is switched on, in one step with {@link #stopAndDisable()}, which can switch it
     * off meanwhile.
     */
    synchronized void startIfEnabled() {
        if (isEnabled()) {
            start();
        }
    }

    /**
     * Stop the language server, disable it and
     * remove IJ code vision, inlay hints, folding
     * for all opened editors which edit the files associated to the language server.
     */
    private synchronized CompletableFuture<Void> stopAndRefreshEditorFeature(boolean refreshEditorFeature, boolean disable) {
        // Collect opened files before stopping the language server
        List<VirtualFile> openedFiles = refreshEditorFeature ?
                new ArrayList<>(openedDocuments.values())
                .stream()
                .map(OpenedDocument::getFile)
                .toList() : Collections.emptyList();

        // Disable the language server
        if (disable) {
            setEnabled(false);
        }

        // Stop the language server
        var stopped = stop();

        if (!openedFiles.isEmpty()) {
            // remove IJ code vision, inlay hints, folding
            // for all opened editors which edit the files associated to the language server.
            for (var file : openedFiles) {
                // refresh IJ code visions, inlay hints, folding
                EditorFeatureManager.getInstance(getProject())
                        .refreshEditorFeature(file, EditorFeatureType.ALL, true, new VirtualFileCancelChecker(file));
            }
        }
        return stopped;
    }

    /**
     * Stops the language server and then disposes it, for a language server that goes while the project stays. Unlike
     * {@link #dispose(boolean)}, the language server still gets shutdown and exit: a disposed language server no longer
     * sends messages.
     *
     * @param refreshEditorFeature true to refresh the code vision, inlay hints and folding of the opened editors.
     */
    @ApiStatus.Internal
    public void stopAndDispose(boolean refreshEditorFeature) {
        // On the UI thread: while it processes a VFS event, the platform goes through a live view of the async file
        // listeners, so removing the listener of this language server from another thread can make the event fail
        stopAndRefreshEditorFeature(refreshEditorFeature, false)
                .whenComplete((unused, error) -> ApplicationManager.getApplication()
                        .invokeLater(() -> Disposer.dispose(this), ModalityState.any()));
    }

    /**
     * Enable the language server, restart it and refresh ode vision, inlay hints, folding for all opened editors
     * which edit the files associated to the language server.
     */
    public synchronized void restart() {
        numberOfRestartAttempts = 0;
        serverError = null;
        setEnabled(true);
        if (serverStatus != ServerStatus.installed && serverStatus != ServerStatus.installing) {
            stop();
        }
        // start the language server
        getInitializedServer()
                .thenAccept(unused -> {
                    // The language server is started.
                    // For all opened files of the project:
                    // 1. send a textDocument/didOpen notification
                    // 2. refresh code vision, inlay hints, folding for all opened editors
                    // which edit the files associated to the language server.
                    Project p = getProject();
                    if (p.isDefault() || p.isDisposed()) {
                        return;
                    }
                    LanguageServiceAccessor.getInstance(p).
                            sendDidOpenAndRefreshEditorFeatureForOpenedFiles(serverDefinition, p);
                });
    }

    public boolean isEnabled() {
        return !isDisposed() && !instanceDisabled && serverDefinition.isEnabled(initialProject);
    }

    /**
     * Switches this language server on or off. An instance of a definition with several is switched off on its own, as
     * by a stop or too many failed starts, so that the other instances keep working. Switching it on also switches the
     * definition on, which would keep it off otherwise.
     */
    private void setEnabled(boolean enabled) {
        if (instanceRoot != null) {
            instanceDisabled = !enabled;
            if (!enabled) {
                return;
            }
        }
        this.serverDefinition.setEnabled(enabled, initialProject);
    }

    /**
     * Starts a language server and triggers initialization. If language server is
     * started and active, does nothing. If language server is inactive, restart it.
     *
     * @throws LanguageServerException thrown when the language server cannot be started
     */
    public synchronized void start() throws LanguageServerException {
        if (isDisposed()) {
            // A disposed language server is not enabled (isEnabled()) and never starts again: nothing would stop the
            // connection it created, and no one would read what it answered. getInitializedServer() and connect()
            // then give no language server, as for one that cannot start.
            return;
        }
        // Check for errors in LSP messages
        // launcherFuture manages request/reponse LSP messages from the server
        if (serverError == null) {
            // If the launcher is done, check if there was an error
            if (launcherFuture != null && launcherFuture.isDone()) {
                // There is an error with messages. Ex: invalid int id)
                // {
                //  "id" : 1779401475267,
                //  "jsonrpc" : "2.0",
                //  "method" : "client/registerCapability",
                //  "params" : {
                try {
                    // Try to get the result with a short timeout
                    launcherFuture.get(5, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // Interrupted - no action needed
                } catch (ExecutionException e) {
                    // An error occurred in LSP message processing
                    serverError = new LanguageServerException(e.getCause());
                } catch (TimeoutException e) {
                    // Timeout - no error detected
                }
                // Show error notification if serverError was set
                // The server will be stopped automatically after several attempts
                showNotificationStartServerError();
            }
        }
        if (serverError != null) {
            if (this.initializeFuture != null && !this.initializeFuture.isDone()) {
                // A restart attempt is still initializing: serverError is only cleared once it
                // succeeds, so stopping here would kill that attempt and spawn another process
                // on every start() call (e.g. on each Search Everywhere keystroke).
                // See: https://github.com/redhat-developer/lsp4ij/issues/1673
                return;
            }
            // Here the language server has been not possible
            // we stop it and attempts a new restart if needed
            stop();
            if (numberOfRestartAttempts > MAX_NUMBER_OF_RESTART_ATTEMPTS - 1) {
                // Disable the language server
                setEnabled(false);
                return;
            } else {
                numberOfRestartAttempts++;
            }
        }
        final var filesToReconnect = new ArrayList<VirtualFile>();
        if (this.languageServer != null) {
            if (isActive()) {
                return;
            } else {
                for (Map.Entry<URI /* file Uri */, OpenedDocument> entry : this.openedDocuments.entrySet()) {
                    filesToReconnect.add(entry.getValue().getFile());
                }
                stop();
            }
        }

        if (this.initializeFuture == null) {
            final VirtualFile rootURI = getRootURI();
            this.launcherFuture = new CompletableFuture<>();
            @NotNull var context = this.currentInitializingContext = new InitializingContext();
            // Use IntelliJ pooled thread instead of ForkJoinPool.commonPool() to avoid
            // ForkJoinPool.helpAsyncBlocker() executing blocking operations (like OSProcessHandler.waitFor())
            // in the current thread when called from ReadAction.
            // We cannot use 'dispatcher' (single thread executor) as it's reserved for ordered notification dispatch.
            // See: https://github.com/redhat-developer/lsp4ij/issues/1442
            CompletableFuture<InitializingContext> startFuture = new CompletableFuture<>();
            Runnable startTask = () -> {
                try {
                        var initializingContext = context;
                        // Starting process...
                        updateStatus(ServerStatus.starting);

                        // Initialize LSP traces
                        if (tracing != null) {
                            tracing = null;
                        }

                        var provider = instanceRoot != null ?
                                serverDefinition.createConnectionProvider(initialProject, instanceRoot) :
                                serverDefinition.createConnectionProvider(initialProject);
                        initializingContext.provider = provider;
                        initParams.setInitializationOptions(provider.getInitializationOptions(rootURI));

                        // Add error log
                        provider.addLogErrorHandler(new LanguageServerLogErrorHandler() {
                            @Override
                            public void logError(String error) {
                                ServerMessageHandler.logMessage(LanguageServerWrapper.this, new MessageParams(MessageType.Error, error));
                            }

                            @Override
                            public void logInfo(String message) {
                                ServerMessageHandler.logMessage(LanguageServerWrapper.this, new MessageParams(MessageType.Info, message));
                            }
                        });
                        provider.addUnexpectedServerStopHandler(() -> {
                            // There is an unexpected stop of the connection
                            // 1. the process was killed outside IntelliJ
                            // 2. the start command takes some times and fails
                            // -->
                            // Stop the language server
                            serverError = new ServerWasStoppedException("The server was stopped unexpectedly.");
                            stop(initializingContext);
                            // Show a notification error with "The server was stopped unexpectedly." error message.
                            showNotificationStartServerError();
                        });

                        this.currentProcessId = null;
                        this.currentProcessCommandLines = null;
                        provider.start();

                        // As process can be stopped, we loose pid and command lines information
                        // when server is stopped, we store them here.
                        // to display them in the Language server explorer even if process is killed.
                        if (provider instanceof ProcessDataProvider data) {
                            this.currentProcessId = data.getPid();
                            this.currentProcessCommandLines = data.getCommands();
                        }

                        synchronized (this) {
                            if (isDisposed() || currentInitializingContext != initializingContext) {
                                // The language server was stopped or disposed while the connection was created, or
                                // before this start: that stop could not close the connection, which did not exist
                                // yet, and nothing else will, so close it here
                                provider.stop();
                                startFuture.cancel(false);
                                return;
                            }
                        }

                        // Throws the CannotStartProcessException exception if process is not alive.
                        // This use case comes for instance when the start process command fails (not a valid start command)
                        provider.ensureIsAlive();
                        startFuture.complete(context);
                } catch (Exception e) {
                    startFuture.completeExceptionally(e);
                }
            };
            this.initializeFuture = startFuture
                    .thenApply(initializingContext -> {
                        var languageClient = instanceRoot != null ?
                                serverDefinition.createLanguageClient(initialProject, instanceRoot) :
                                serverDefinition.createLanguageClient(initialProject);
                        initializingContext.languageClient = languageClient;
                        languageClient.setServerWrapper(this);
                        initParams.setProcessId(getParentProcessId());

                        if (rootURI != null) {
                            initParams.setRootUri(toUriString(rootURI));
                            initParams.setRootPath(rootURI.getPath());
                        }

                        var provider = initializingContext.provider;
                        // wrapMessages applies this wrapper to both directions:
                        // outgoing (StreamMessageConsumer) and incoming (RemoteEndpoint).
                        // Separate by direction so incoming server requests are never
                        // blocked behind a slow outgoing write.
                        // wrapMessages applies this wrapper to both directions:
                        // outgoing (StreamMessageConsumer) and incoming (RemoteEndpoint).
                        // Outgoing: single FIFO queue preserves message ordering
                        // (didOpen before didChange before didClose, requests after
                        // their preceding notifications).
                        // Incoming: handle on the listener thread so server-initiated
                        // requests (e.g. workspace/configuration) are never blocked
                        // behind a slow outgoing notification write that saturates
                        // the pipe.
                        UnaryOperator<MessageConsumer> wrapper = consumer -> {
                            boolean outgoing = consumer instanceof StreamMessageConsumer;
                            return message -> {
                                if (isDisposed()) {
                                    return;
                                }
                                logMessage(message, consumer);
                                try {
                                    if (outgoing) {
                                        CompletableFuture.runAsync(
                                                        () -> consumer.consume(message), messageWriter)
                                                .exceptionally(e -> {
                                                    getLanguageServerLifecycleManager().onError(this, e);
                                                    return null;
                                                });
                                    } else {
                                        consumer.consume(message);
                                    }
                                } catch (Throwable e) {
                                    getLanguageServerLifecycleManager().onError(this, e);
                                    throw e;
                                }
                                if (provider != null && isActive()) {
                                    provider.handleMessage(message, this.languageServer, rootURI);
                                }
                            };
                        };

                        Launcher<LanguageServer> launcher = getClientFeatures().createLauncherBuilder() //
                                .setLocalService(languageClient)//
                                .setRemoteInterface(serverDefinition.getServerInterface())//
                                .setInput(provider.getInputStream())//
                                .setOutput(provider.getOutputStream())//
                                .setExecutorService(listener)//
                                .wrapMessages(wrapper)//
                                .create();
                        var languageServer = launcher.getRemoteProxy();
                        initializingContext.languageServer = languageServer;
                        languageClient.connect(languageServer);
                        initializingContext.launcherFuture = launcher.startListening();
                        return initializingContext;
                    })
                    .thenCompose(initializingContext -> initServer(rootURI, initializingContext))
                    .thenApply(initializingContext -> {
                        synchronized (this) {
                            // An attempt that a stop replaced while it initialized must not clear the error that the
                            // current attempt restarts for, set the capabilities of its own server, nor send that
                            // server, which the stop is shutting down, initialized
                            requireCurrent(initializingContext);
                            serverError = null;
                            if (errorNotification != null) {
                                // Close the current error notification
                                // displayed when server start fails.
                                errorNotification.expire();
                                errorNotification = null;
                            }
                            serverCapabilities = initializingContext.initializeResult.getCapabilities();
                            syncOptions = createSyncOptions();
                            getClientFeatures().setServerCapabilities(serverCapabilities);
                            this.initiallySupportsWorkspaceFolders = supportsWorkspaceFolders(serverCapabilities);
                            initializingContext.languageServer.initialized(new InitializedParams());
                        }
                        return initializingContext;
                    })
                    .thenApply(initializingContext -> {
                        synchronized (this) {
                            // An attempt that a stop replaced while it initialized must not install its connection, which
                            // the stop is shutting down, over the current attempt's
                            requireCurrent(initializingContext);
                            messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
                            messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileListener);

                            fileOperationsManager = new FileOperationsManager(this);
                            fileOperationsManager.setServerCapabilities(serverCapabilities);

                            workspaceFolderNotificationManager = new WorkspaceFolderNotificationManager(this);

                            this.lspStreamProvider = initializingContext.provider;
                            this.languageClient = initializingContext.languageClient;
                            this.languageServer = initializingContext.languageServer;
                            this.launcherFuture = initializingContext.launcherFuture;

                            updateStatus(ServerStatus.started);
                        }
                        initializingContext.languageClient.updateWorkspaceDiagnosticProviders();
                        return initializingContext;
                    })
                    .thenAccept(initializingContext -> {
                        for (VirtualFile fileToReconnect : filesToReconnect) {
                            synchronized (this) {
                                // A stop after the install must not have the reconnection start the server again. The
                                // stop takes this monitor too, so it comes before the check or after the connect call,
                                // which takes documentsLock after this monitor, as the stop does
                                if (currentInitializingContext != initializingContext) {
                                    return;
                                }
                                connect(fileToReconnect, new LSPFileConnectionInfo(null, null, null, true));
                            }
                        }
                    })
                    .exceptionally(e -> {
                        if (e instanceof CompletionException) {
                            e = e.getCause();
                        }
                        if (e instanceof CannotStartProcessException) {
                            serverError = (CannotStartProcessException) e;
                        } else {
                            serverError = new CannotStartServerException("Error while starting language server '" + serverDefinition.getId() + "' (pid=" + getCurrentProcessId() + ")", e);
                        }
                        showNotificationStartServerError();
                        initializeFuture.completeExceptionally(serverError);
                        getLanguageServerLifecycleManager().onError(this, e);
                        stop(context);
                        return null;
                    });
            // Submitted only now that initializeFuture is set: a start which fails at once runs the failure handler
            // above, which fails initializeFuture and then stops the server, clearing it
            ApplicationManager.getApplication().executeOnPooledThread(startTask);
        }
    }

    /**
     * Sets where the connection of a document runs once its start attempt is initialized, so that a test can run it
     * when it chooses, as after a stop.
     *
     * @param connectionExecutor the executor.
     */
    @TestOnly
    void setConnectionExecutor(@NotNull Executor connectionExecutor) {
        this.connectionExecutor = connectionExecutor;
    }

    /**
     * Throws a {@link CancellationException} unless the given start attempt is still the current one. A stop, or a
     * stop and a newer start, replaces an attempt, but cancels only the last stage of its start chain, so the stages
     * before it still run when the attempt's initialize reply comes. The caller holds this wrapper's monitor, which
     * stop() takes to replace the attempt.
     *
     * @param initializingContext the start attempt.
     */
    private void requireCurrent(@NotNull InitializingContext initializingContext) {
        if (currentInitializingContext != initializingContext) {
            throw new CancellationException("The start attempt was replaced by a stop or a newer start");
        }
    }

    /**
     * Show a notification error when server cannot be started.
     */
    private void showNotificationStartServerError() {
        if (serverError == null) {
            return;
        }
        // Show the notification if needed
        boolean showNotification = errorNotification == null ||
                errorNotification.getBalloon() == null ||
                errorNotification.isExpired() ||
                !errorNotification.getContent().equals(serverError.getMessage());
        if (showNotification) {
            // Close old error notification if needed
            if (errorNotification != null && !errorNotification.isExpired()) {
                errorNotification.expire();
            }
            // Show start server error notification.
            var notification = showNotificationError(this, "Cannot start server", serverError);
            errorNotification = notification;
            if (isDisposed()) {
                // A dispose while it was shown didn't see it, and its actions would keep this language server
                errorNotification = null;
                notification.expire();
            }
        }
    }

    private CompletableFuture<InitializingContext> initServer(final VirtualFile rootURI,
                                                              @NotNull InitializingContext initializingContext) {
        var provider = initializingContext.provider;
        initParams.setCapabilities(ClientCapabilitiesFactory
                .create(provider.getExperimentalFeaturesPOJO()));
        initParams.setClientInfo(getClientInfo());
        initParams.setTrace(provider.getTrace(rootURI));

        var folders = getClientFeatures()
            .getWorkspaceFolderFeature()
            .getInitialWorkspaceFolders(initialProject);
        initParams.setWorkspaceFolders(folders);

        // Customize initialize params if needed
        getClientFeatures().initializeParams(initParams);

        // no then...Async future here as we want this chain of operation to be sequential and "atomic"-ish
        var languageServer = initializingContext.languageServer;
        return languageServer.initialize(initParams)
                .thenApply(initializeResult -> {
                    initializingContext.initializeResult = initializeResult;
                    return initializingContext;
                });
    }

    @Nullable
    private VirtualFile getRootURI() {
        if (instanceRoot != null) {
            return FileUriSupport.findFileByUri(instanceRoot.getUri(), getClientFeatures());
        }
        var roots = LSPIJUtils.getRoots(getProject());
        if (roots.size() == 1) {
            return roots.iterator().next();
        }
        return null;
    }

    private void logMessage(Message message, MessageConsumer consumer) {
        getLanguageServerLifecycleManager().logLSPMessage(message, consumer, this);
    }

    /**
     * Cancels the stop scheduled after the last document disconnected, if any. The status is left as it is: a pending
     * stop doesn't change it.
     */
    private void removeStopTimer() {
        Alarm stopAlarm = this.stopAlarm;
        if (stopAlarm != null) {
            stopAlarm.cancelAllRequests();
        }
    }

    public void updateStatus(@NotNull ServerStatus serverStatus) {
        boolean statusChanged = (this.serverStatus != serverStatus) && (serverStatus != ServerStatus.none);
        this.serverStatus = serverStatus;
        // If this is an "interesting" status change, increment the project-level modification tracker and the wrapper's
        // modification tracker before firing events
        if (statusChanged) {
            Project p = getProject();
            if (!p.isDefault() && !p.isDisposed()) {
                LanguageServiceAccessor.getInstance(p).incrementModificationCount();
            }
            incrementModificationCount();
            getLanguageServerLifecycleManager().onStatusChanged(this);
            if (languageClient != null) {
                languageClient.handleServerStatusChanged(serverStatus);
            }
            getClientFeatures().handleServerStatusChanged(serverStatus);
        }
    }

    /**
     * Schedules the stop of the language server after lastDocumentDisconnectedTimeout. The status stays as it is until
     * the stop runs: the server is still running and usable while the stop is pending, and a document that connects or
     * a lease cancels it.
     */
    private void startStopTimer() {
        int delayMs = (int) TimeUnit.SECONDS.toMillis(serverDefinition.getLastDocumentDisconnectedTimeout());
        getStopAlarm().addRequest(this::stopIfNotKeptAlive, delayMs);
    }

    /**
     * Stops the language server once the delay after its last document disconnected has passed, unless something keeps
     * it alive by then, such as {@link LSPClientFeatures#keepServerAlive()}, which is asked again here. A check that
     * fails keeps the server, as a yes does: it is not stopped on an error, and stops when its last document or lease
     * goes next.
     */
    void stopIfNotKeptAlive() {
        boolean keptAlive;
        try {
            keptAlive = keepAlive();
        } catch (RuntimeException e) {
            LOGGER.error("Failed to check whether language server {} is kept alive", serverDefinition.getId(), e);
            keptAlive = true;
        }
        if (keptAlive) {
            return;
        }
        try {
            stop();
        } catch (Throwable t) {
            LOGGER.error("Failed to stop language server {}", serverDefinition.getId(), t);
        }
    }

    private @NotNull Alarm getStopAlarm() {
        if (stopAlarm == null) {
            synchronized (this) {
                if (stopAlarm == null) {
                    stopAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
                }
            }
        }
        return stopAlarm;
    }

    /**
     * @return whether the underlying connection to language server is still active
     */
    public boolean isActive() {
        return this.launcherFuture != null && !this.launcherFuture.isDone() && !this.launcherFuture.isCancelled();
    }

    /**
     * Check whether this LS is suitable for provided project. Starts the LS if not
     * already started.
     *
     * @return whether this language server can operate on the given project
     * @since 0.5
     */
    public boolean canOperate(Project project) {
        if (project != null && project.equals(this.initialProject)) {
            return true;
        }

        return serverDefinition.isSingleton();
    }

    /**
     * Connect the given file to the language server and returns the language server instance when:
     *
     * <ul>
     *     <li>language server is initialized</li>
     *     <li>didOpen for the given file Uri happens</li>
     * </ul>
     *
     * <p>
     *     In other case (ex : language server initialize future is null, file cannot be retrieved by the given Uri),
     *     the method return a CompletableFuture which returns null.
     * </p>
     *
     * @param file               the file to connect to the language server
     * @param fileConnectionInfo the document of the file and null otherwise. In the null case, the document will be retrieved from the file
     *                           by using a blocking read action.
     * @return the completable future with the language server instance or null.
     */
    CompletableFuture<@Nullable LanguageServer> connect(@NotNull VirtualFile file,
                                                        @NotNull LanguageServerWrapper.LSPFileConnectionInfo fileConnectionInfo) {
        synchronized (documentsLock) {
            promoteConnection(toUri(file));
        }
        return connectDocument(file, fileConnectionInfo, null, null);
    }

    /**
     * Connects the file as {@link #connect(VirtualFile, LSPFileConnectionInfo)} does, for a connection asked for when
     * the last editor of the file had closed the given number of times. A close of the file's last editor since then
     * disconnects the file, which this connection then leaves disconnected: it gives no language server.
     */
    CompletableFuture<@Nullable LanguageServer> connect(@NotNull VirtualFile file,
                                                        @NotNull LSPFileConnectionInfo fileConnectionInfo,
                                                        int editorCloses) {
        return connectDocument(file, fileConnectionInfo, null, editorCloses);
    }

    CompletableFuture<@Nullable LanguageServer> connect(@NotNull VirtualFile file,
                                                        @NotNull LSPFileConnectionInfo info,
                                                        @NotNull DocumentConnectionScope scope) {
        URI uri = toUri(file);
        if (uri == null) {
            return CompletableFuture.completedFuture(null);
        }
        DocumentConnectionClaim claim;
        synchronized (documentsLock) {
            var connection = scopedDocuments.computeIfAbsent(uri,
                    ignored -> new ScopedDocumentConnection(openedDocuments.containsKey(uri)));
            connection.owners++;
            claim = new DocumentConnectionClaim(uri, connection, new AtomicBoolean());
        }
        scope.register(() -> releaseConnection(file, claim));
        return connectDocument(file, info, claim, null);
    }

    private void promoteConnection(@Nullable URI uri) {
        var connection = scopedDocuments.get(uri);
        if (connection != null) {
            connection.persistent = true;
        }
    }

    private boolean isCurrent(@Nullable DocumentConnectionClaim claim) {
        return claim == null || (!claim.released().get()
                && scopedDocuments.get(claim.uri()) == claim.connection());
    }

    /**
     * Returns true if the last editor of the file has closed since a connection was asked for, when it had closed the
     * given number of times. A connection with no number is not checked. A close is counted before its disconnection
     * takes documentsLock, so a caller that holds the lock and finds no new close connects the file before the close
     * disconnects it.
     */
    private static boolean isEditorClosedSince(@NotNull VirtualFile file, @Nullable Integer editorCloses) {
        return editorCloses != null && ConnectDocumentToLanguageServerSetupParticipant.getEditorCloses(file) != editorCloses;
    }

    private void releaseConnection(@NotNull VirtualFile file, @NotNull DocumentConnectionClaim claim) {
        if (!claim.released().compareAndSet(false, true)) {
            return;
        }
        // File opening and the final editor-ownership check are serialized on the EDT.
        // Do not wait for the EDT here: formatting can run under a read action during save.
        ApplicationManager.getApplication().invokeLater(() -> {
            synchronized (documentsLock) {
                var connection = scopedDocuments.get(claim.uri());
                if (connection != claim.connection() || --connection.owners != 0) {
                    return;
                }
                scopedDocuments.remove(claim.uri());
                if (connection.persistent || (!initialProject.isDisposed()
                        && FileEditorManager.getInstance(initialProject).isFileOpen(file))) {
                    return;
                }
                disconnect(claim.uri(), false);
            }
            // As when an editor closes, a server started only for this request then follows the
            // idle-shutdown policy. Stopping takes this wrapper's monitor before documentsLock,
            // so it runs after the lock is released.
            if (!isDisposed()) {
                maybeShutdown();
            }
        }, ModalityState.any());
    }

    private CompletableFuture<@Nullable LanguageServer> connectDocument(@NotNull VirtualFile file,
                                                                        @NotNull LSPFileConnectionInfo fileConnectionInfo,
                                                                        @Nullable DocumentConnectionClaim claim,
                                                                        @Nullable Integer editorCloses) {
        removeStopTimer();

        URI fileUri = toUri(file);
        if (fileUri == null) {
            // Invalid file uri
            return CompletableFuture.completedFuture(null);
        }

        boolean waitForDidOpen = fileConnectionInfo.waitForDidOpen();
        DocumentContentSynchronizer connected;
        synchronized (documentsLock) {
            if (!isCurrent(claim) || isEditorClosedSince(file, editorCloses)) {
                return CompletableFuture.completedFuture(null);
            }
            if (claim == null) {
                promoteConnection(fileUri);
            }
            connected = getConnectedSynchronizer(fileUri);
        }
        if (connected != null) {
            // After the lock: the didOpen can need the synchronized start(), and stop() takes that monitor
            // before documentsLock
            return getLanguageServerWhenDidOpen(connected, waitForDidOpen);
        }

        // start language server if needed, and take the start attempt that the connection is for
        InitializingContext attempt;
        CompletableFuture<Void> initialized;
        synchronized (this) {
            start();
            attempt = currentInitializingContext;
            initialized = this.initializeFuture;
        }

        if (initialized == null) {
            // The "initialize" future is not initialized, return null as language server.
            return CompletableFuture.completedFuture(null);
        }

        return initialized.thenComposeAsync(theVoid -> {
            // Here, the "initialize" future is initialized

            // A stop, as by stopAndDisable(), can replace the attempt before this runs: the connection is then
            // dropped, so that it neither starts the server again nor connects the document to a stopped server
            if (currentInitializingContext != attempt) {
                return CompletableFuture.completedFuture(null);
            }

            // Here the file is not already connected
            // To connect the file, we need the document instance to add LSP document listener to manage didOpen, didChange, etc.
            Document optionalDocument = fileConnectionInfo.document();
            Document document = optionalDocument != null ? optionalDocument : LSPIJUtils.getDocument(file);
            if (document == null) {
                return CompletableFuture.completedFuture(null);
            }

            // Compute workspace folder BEFORE synchronized block to avoid slow I/O operations in critical section
            WorkspaceFolder folderToNotify = null;
            if (workspaceFolderNotificationManager != null) {
                folderToNotify = workspaceFolderNotificationManager.computeWorkspaceFolderToNotify(file);
            }

            // Before the lock: getInitializedServer() calls the synchronized start(), and stop() takes that
            // monitor before documentsLock. Under that monitor, a stop comes before the check or after the call, so
            // that the call does not start the server again.
            CompletableFuture<LanguageServer> initializedServer = null;
            if (folderToNotify != null) {
                synchronized (this) {
                    if (currentInitializingContext != attempt) {
                        return CompletableFuture.completedFuture(null);
                    }
                    initializedServer = getInitializedServer();
                }
            }
            DocumentContentSynchronizer synchronizer;
            synchronized (documentsLock) {
                // A stop which replaced the attempt after this check disconnects the document once this lock is
                // released, as it disconnects every opened document. So does a close of the file's last editor.
                if (!isCurrent(claim) || currentInitializingContext != attempt
                        || isEditorClosedSince(file, editorCloses)) {
                    return CompletableFuture.completedFuture(null);
                }
                if (claim == null) {
                    promoteConnection(fileUri);
                }
                // Remove from closed documents and check if already opened
                var closedDocument = closedDocuments.remove(fileUri);
                boolean droppedErrors = closedDocument != null && closedDocument.hasErrors();

                // Check again if file is already opened (within synchronized block)
                var alreadyConnected = getConnectedSynchronizer(fileUri);
                if (alreadyConnected != null) {
                    synchronizer = alreadyConnected;
                    reportProblemOfDroppedDocument(file, droppedErrors);
                } else {

                    // Mark the workspace folder as notified and queue its notification before the document's
                    // didOpen, so that the server knows the folder first. Queuing does not wait.
                    if (folderToNotify != null && workspaceFolderNotificationManager.markFolderAsNotified(folderToNotify)) {
                        sendNotification(initializedServer, workspaceFolderNotificationManager.folderAddedNotification(folderToNotify));
                    }

                    synchronizer = createDocumentContentSynchronizer(
                            toUriString(fileUri), file, document, fileConnectionInfo);
                    // Synchronizer usually is disposed when document editor is closed.
                    // But in case of closing the project, it does not close all editors,
                    // so to ensure synchronizer is disposed we also register it here
                    Disposer.register(this, synchronizer);
                    document.addDocumentListener(synchronizer, synchronizer);
                    OpenedDocument data = new OpenedDocument(new LanguageServerItem(languageServer, this), file, synchronizer);
                    // It replaces an opened document which held only the diagnostics published before the connection
                    var replaced = LanguageServerWrapper.this.openedDocuments.put(fileUri, data);
                    invalidateWorkspaceDiagnosticCache(fileUri);
                    reportProblemOfDroppedDocument(file, droppedErrors || (replaced != null && replaced.hasErrors()));
                    if (!waitForDidOpen) {
                        return CompletableFuture.completedFuture(languageServer);
                    }
                }
            }
            // After the lock: the didOpen can need the synchronized start(), and stop() takes that monitor
            // before documentsLock
            return getLanguageServerWhenDidOpen(synchronizer, waitForDidOpen);
        }, connectionExecutor);
    }

    /**
     * Reports the file to the problems of the Project view again when the connection of the file dropped a document of
     * it which had errors: its closed document, or an opened document which held only published diagnostics. The file
     * is a problem file for those errors, and no update of the new opened document reports a change unless that one
     * gets errors. The report, which runs later, reads what the language servers hold for the file, so it is made once
     * the new opened document is installed.
     *
     * @param file          the file.
     * @param droppedErrors whether a dropped document of the file had errors.
     */
    private void reportProblemOfDroppedDocument(@NotNull VirtualFile file, boolean droppedErrors) {
        if (droppedErrors && getClientFeatures().getDiagnosticFeature().canReportProblem(file)) {
            LSPDiagnosticUtils.reportProblem(file, null, getProject());
        }
    }

    /**
     * Returns the synchronizer of the document if it is already connected, and null otherwise. The caller holds
     * documentsLock.
     */
    private @Nullable DocumentContentSynchronizer getConnectedSynchronizer(@Nullable URI fileUri) {
        if (fileUri == null) {
            return null;
        }
        var openedDocument = openedDocuments.get(fileUri);
        return openedDocument != null ? openedDocument.getSynchronizer() : null;
    }

    /**
     * Returns the language server once the connected document can be used with it. The caller must not hold
     * documentsLock: asking for the didOpen can start the server, in the synchronized start(), and stop() takes that
     * monitor before documentsLock.
     */
    private CompletableFuture<LanguageServer> getLanguageServerWhenDidOpen(@NotNull DocumentContentSynchronizer synchronizer,
                                                                          boolean waitForDidOpen) {
        if (!waitForDidOpen) {
            // The caller can hold a write action, so wait only for the changes already sent, which needs
            // no read access. LanguageServiceAccessor sends the pending ones before it connects.
            var changesSent = synchronizer.getLastChangeSent();
            return changesSent.isDone() ? CompletableFuture.completedFuture(languageServer) : whenChangesSent(changesSent);
        }
        // The file is already connected.
        // returns the language server instance when didOpen, and the changes made since, have been sent
        return getLanguageServerWhenDidOpen(synchronizer);
    }

    private CompletableFuture<LanguageServer> getLanguageServerWhenDidOpen(@NotNull DocumentContentSynchronizer synchronizer) {
        var didOpenFuture = synchronizer.getDidOpenFuture();
        if (didOpenFuture.isDone() && !didOpenFuture.isCompletedExceptionally()) {
            // The didOpen has happened, no need to wait for the didOpen
            // to return the language server, only for the changes made since
            var changesSent = synchronizer.getChangesSent();
            if (changesSent.isDone()) {
                return CompletableFuture.completedFuture(languageServer);
            }
            return whenChangesSent(changesSent);
        }
        // The didOpen has not happened, or it failed, as for a document closed before it was opened:
        // wait for the end of didOpen to return the language server, or its failure
        return didOpenFuture
                .thenCompose(ls -> whenChangesSent(synchronizer.getChangesSent()));
    }

    /**
     * Returns the language server once the given changes have been dispatched, so that a request sent
     * with it reaches the server after them. A change that could not be sent does not fail the request.
     */
    private CompletableFuture<LanguageServer> whenChangesSent(@NotNull CompletableFuture<Void> changesSent) {
        // Continue on a pooled thread, not on the dispatcher that completed the changes
        return changesSent.handleAsync((result, error) -> languageServer);
    }

    /**
     * Allocates a document version on the current connection, unique across documents and reopen.
     * File opens and document notifications can originate on different threads.
     */
    int nextDocumentVersion() {
        return documentVersion.updateAndGet(Math::incrementExact);
    }

    @NotNull
    private DocumentContentSynchronizer createDocumentContentSynchronizer(@NotNull String fileUri,
                                                                          @NotNull VirtualFile file,
                                                                          @NotNull Document document,
                                                                          @NotNull LSPFileConnectionInfo connectionInfo) {
        return new DocumentContentSynchronizer(this, fileUri, file, document,
                connectionInfo.documentText(), connectionInfo.documentModificationStamp(), connectionInfo.languageId());
    }

    public @NotNull TextDocumentSyncOptions getSyncOptions() {
        if (syncOptions != null) {
            return syncOptions;
        }
        syncOptions = createSyncOptions();
        return syncOptions;
    }

    public boolean isSaveSupported() {
        if (this.serverCapabilities == null) {
            // Language server is not started, save is not supported
            return false;
        }
        var saveOptions = getSyncOptions().getSave();
        if (saveOptions == null) {
            return false;
        }
        return (saveOptions.isLeft() && saveOptions.getLeft()) || (saveOptions.isRight() && saveOptions.getRight() != null);
    }

    private @NotNull TextDocumentSyncOptions createSyncOptions() {
        Either<TextDocumentSyncKind, TextDocumentSyncOptions> syncOptions = initializeFuture == null ? null
                : this.serverCapabilities.getTextDocumentSync();
        if (syncOptions != null) {
            if (syncOptions.isLeft()) {
                TextDocumentSyncKind change = syncOptions.getLeft();
                TextDocumentSyncOptions newSyncOptions = new TextDocumentSyncOptions();
                newSyncOptions.setOpenClose(true);
                newSyncOptions.setChange(change);
                newSyncOptions.setSave(true);
                return newSyncOptions;
            } else if (syncOptions.isRight()) {
                return syncOptions.getRight();
            }
        }
        return DEFAULT_SYNC_OPTIONS;
    }

    void disconnect(@NotNull VirtualFile file, boolean stopIfNoOpenedFiles) {
        URI fileUri = toUri(file);
        disconnect(fileUri, stopIfNoOpenedFiles);
    }

    void disconnect(@Nullable URI fileUri, boolean stopIfNoOpenedFiles) {
        if (fileUri == null) {
            return;
        }
        synchronized (documentsLock) {
            scopedDocuments.remove(fileUri);
            OpenedDocument openedDocument = this.openedDocuments.remove(fileUri);
            if (openedDocument != null) {
                // Remove the listener from the old document stored in synchronizer
                DocumentContentSynchronizer synchronizer = openedDocument.getSynchronizer();
                if (synchronizer != null) {
                    Disposer.dispose(synchronizer);
                }
                clearProblem(Collections.singleton(fileUri), getClientFeatures(), getProject());
                invalidateWorkspaceDiagnosticCache(fileUri);
            }
        }
        if (stopIfNoOpenedFiles) {
            maybeShutdown();
        }
    }

    private void maybeShutdown() {
        if (!keepAlive()) {
            if (this.serverDefinition.getLastDocumentDisconnectedTimeout() != 0 && !ApplicationManager.getApplication().isUnitTestMode()) {
                removeStopTimer();
                startStopTimer();
            } else {
                stop();
            }
        }
    }

    private boolean keepAlive() {
        return getClientFeatures().keepServerAlive() || !this.openedDocuments.isEmpty() || this.keepAliveCounter.get() > 0;
    }

    void incrementKeepAlive() {
        keepAliveCounter.incrementAndGet();
        // A lease taken while the last document's stop is pending keeps the server, as a document that connects does
        removeStopTimer();
    }

    void decrementKeepAlive() {
        keepAliveCounter.decrementAndGet();
        maybeShutdown();
    }

    /**
     * checks if the wrapper is already connected to the document at the given path
     */
    public boolean isConnectedTo(URI fileUri) {
        return openedDocuments.containsKey(fileUri);
    }

    /**
     * Returns the document opened in the editor for the given file URI.
     * <p>
     * If the document is already open, it is returned immediately otherwise it returns null
     *
     * @param fileUri the file URI
     * @return the {@link OpenedDocument} representing the opened editor document, or {@code null} if not found
     */
    public @Nullable OpenedDocument getOpenedDocument(@Nullable URI fileUri) {
        return getOpenedDocument(fileUri, false);
    }

    /**
     * Returns the document opened in the editor for the given file URI.
     * <p>
     * If the document is already open, it is returned immediately. If not, and {@code force} is
     * {@code true}, this method will try to locate the file and open it in the context of the language server.
     *
     * @param fileUri the file URI
     * @param force   if {@code true}, attempt to open the document if it is not already opened
     * @return the {@link OpenedDocument} representing the opened editor document, or {@code null} if not found
     */
    public @Nullable OpenedDocument getOpenedDocument(@Nullable URI fileUri, boolean force) {
        var openedDocument = fileUri != null ? openedDocuments.get(fileUri) : null;
        if (openedDocument != null) {
            // Document is already opened (with or without synchronization with the LSP server when content changes)
            return openedDocument;
        }
        if (!force) {
            return null;
        }
        String uri = getClientFeatures().toString(fileUri, false);
        if (uri != null) {
            VirtualFile file = getClientFeatures().findFileByUri(uri);
            if (file != null) {
                openedDocument = new OpenedDocument(new LanguageServerItem(languageServer, this), file, null);
                openedDocuments.put(fileUri, openedDocument);
                invalidateWorkspaceDiagnosticCache(fileUri);
            }
        }
        return openedDocument;
    }

    private void invalidateWorkspaceDiagnosticCache(@NotNull URI fileUri) {
        var client = languageClient;
        if (client != null) {
            client.invalidateWorkspaceDiagnosticCache(fileUri);
        }
    }

    /**
     * Returns all LSP files connected to this language server.
     *
     * @return all LSP files connected to this language server.
     */
    public Collection<OpenedDocument> getOpenedDocuments() {
        // Create a new array list to avoid ConcurrentModificationException
        return new ArrayList<>(openedDocuments.values());
    }

    /**
     * Returns the LSP file data coming from this language server for the given file uri.
     *
     * @param fileUri the file Uri.
     * @return the LSP file data coming from this language server for the given file uri.
     */
    public @Nullable ClosedDocument getClosedDocument(URI fileUri, boolean force) {
        var closedDocument = closedDocuments.get(fileUri);
        if (closedDocument == null && force) {
            synchronized (documentsLock) {
                closedDocument = closedDocuments.get(fileUri);
                if (closedDocument == null) {
                    closedDocument = new ClosedDocument();
                    closedDocuments.put(fileUri, closedDocument);
                }
            }
        }
        return closedDocument;
    }

    public LanguageServer getLanguageServer() {
        return languageServer;
    }

    /**
     * Returns the listener that sends the file operations and the watched file changes to this server.
     */
    @ApiStatus.Internal
    public @NotNull AbstractLSPFileListener getFileListener() {
        return fileListener;
    }

    /**
     * Starts the language server and returns a CompletableFuture waiting for the
     * server to be initialized. If done in the UI stream, a job will be created
     * displaying that the server is being initialized
     */
    @NotNull
    public CompletableFuture<LanguageServer> getInitializedServer() {
        try {
            start();
        } catch (LanguageServerException ex) {
            // The language server cannot be started, return a null language server
            return CompletableFuture.completedFuture(null);
        }
        var initialized = initializeFuture;
        if (initialized != null) {
            // Also once it is done: a failed initialization then reaches the caller, instead of a
            // server that never initialized
            return initialized.thenApply(r -> this.languageServer);
        }
        return CompletableFuture.completedFuture(this.languageServer);
    }

    /**
     * Sends a notification to the wrapped language server
     *
     * @param fn LS notification to send
     * @return
     */
    public CompletableFuture<LanguageServer> sendNotification(@NotNull Function<LanguageServer, LanguageServer> fn) {
        // Enqueues a notification on the dispatch thread associated with the wrapped language server. This
        // ensures the interleaving of document updates and other requests in the UI is mirrored in the
        // order in which they get dispatched to the server
        return sendNotification(getInitializedServer(), fn);
    }

    /**
     * Sends a notification to the given initialized server, after the notifications sent before it.
     * Unlike {@link #sendNotification(Function)}, this neither starts the server nor takes any lock other
     * than the one ordering the notifications, so a caller can hold its own lock while sending.
     *
     * @param initializedServer the server, as returned by {@link #getInitializedServer()}
     * @param fn                LS notification to send
     */
    CompletableFuture<LanguageServer> sendNotification(@NotNull CompletableFuture<LanguageServer> initializedServer,
                                                       @NotNull Function<LanguageServer, LanguageServer> fn) {
        synchronized (notificationLock) {
            // Each notification is dispatched after the previous one. Waiting for the initialized
            // server alone does not keep their order: the dependents of one future complete in
            // reverse order of registration, so notifications queued during initialization would
            // reach the dispatcher last to first. A failed notification does not hold back the next.
            CompletableFuture<LanguageServer> notification = lastNotification
                    .handle((result, error) -> null)
                    .thenCompose(previous -> initializedServer)
                    .thenApplyAsync(fn, this.dispatcher);
            lastNotification = notification;
            return notification;
        }
    }

    /**
     * Returns the server capabilities if it is ready and null otherwise.
     *
     * @return the server capabilities if it is ready and null otherwise.
     */
    @Nullable
    public ServerCapabilities getServerCapabilitiesSync() {
        return serverCapabilities;
    }

    /**
     * Updates workspace diagnostic providers from this server's capabilities.
     * Does nothing until a language client is attached.
     */
    @ApiStatus.Internal
    public void updateWorkspaceDiagnosticProviders() {
        var client = languageClient;
        if (client != null) {
            client.updateWorkspaceDiagnosticProviders();
        }
    }

    public void registerCapability(RegistrationParams params) {
        registerCapability(params, null);
    }

    /**
     * Registers the capabilities that the server of the given client asks for, unless that client's connection has
     * stopped.
     *
     * @param params the registrations
     * @param client the client that received them, and null not to check which connection they come from
     */
    public void registerCapability(RegistrationParams params, @Nullable LanguageClientImpl client) {
        var registrationContext = currentInitializingContext;
        var initialization = initializeFuture;
        if (initialization == null || registrationContext == null
                || client != null && registrationContext.languageClient != client) {
            // A registration from a connection that has stopped, as after a restart
            return;
        }
        initialization.thenRun(() -> {
            params.getRegistrations().forEach(reg -> {
                if (LSPNotificationConstants.WORKSPACE_DID_CHANGE_WORKSPACE_FOLDERS.equals(reg.getMethod())) {
                    // register 'workspace/didChangeWorkspaceFolders' capability
                    assert serverCapabilities != null :
                            "Dynamic capability registration failed! Server not yet initialized?"; //$NON-NLS-1$
                    if (initiallySupportsWorkspaceFolders) {
                        // Can treat this as a NOP since nothing can disable it dynamically if it was
                        // enabled on initialization.
                    } else if (supportsWorkspaceFolders(serverCapabilities)) {
                        LOGGER.warn(
                                "Dynamic registration of 'workspace/didChangeWorkspaceFolders' ignored. It was already enabled before"); //$NON-NLS-1$);
                    } else {
                        addRegistration(reg, () -> setWorkspaceFoldersEnablement(false));
                        setWorkspaceFoldersEnablement(true);
                    }
                } else if (LSPNotificationConstants.WORKSPACE_DID_CHANGE_WATCHED_FILES.equals(reg.getMethod())) {
                    // register 'workspace/didChangeWatchedFiles' capability
                    try {
                        DidChangeWatchedFilesRegistrationOptions options = JSONUtils.getLsp4jGson()
                                .fromJson((JsonObject) reg.getRegisterOptions(),
                                        DidChangeWatchedFilesRegistrationOptions.class);
                        synchronized (LanguageServerWrapper.this) {
                            // The callback can run once its server has stopped, and stop() has dropped its watchers
                            if (isDisposed() || registrationContext != currentInitializingContext) {
                                return;
                            }
                            fileListener.registerFileSystemWatchers(reg.getId(), options.getWatchers());
                            addRegistration(reg, () -> fileListener.unregisterFileSystemWatchers(reg.getId()));
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while getting 'workspace/didChangeWatchedFiles' capability", e);
                    }
                } else if (LSPRequestConstants.WORKSPACE_TEXT_DOCUMENT_CONTENT.equals(reg.getMethod())) {
                    // register 'workspace/textDocumentContent' capability
                    try {
                        TextDocumentContentRegistrationOptions options = JSONUtils.getLsp4jGson()
                                .fromJson((JsonObject) reg.getRegisterOptions(),
                                        TextDocumentContentRegistrationOptions.class);
                        List<String> schemes = options.getSchemes();
                        if (schemes != null && !schemes.isEmpty()) {
                            synchronized (LanguageServerWrapper.this) {
                                // The callback can run once its server has stopped, whose schemes must not
                                // reach the capabilities of the next connection
                                if (isDisposed() || registrationContext != currentInitializingContext) {
                                    return;
                                }
                                addRegistration(reg, () -> unregisterDocumentContentSchemes(schemes));
                                registerDocumentContentSchemes(schemes);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while getting 'workspace/textDocumentContent' capability", e);
                    }
                } else if (LSPRequestConstants.WORKSPACE_EXECUTE_COMMAND.equals(reg.getMethod())) {
                    // register 'workspace/executeCommand' capability
                    try {
                        ExecuteCommandOptions executeCommandOptions = JSONUtils.getLsp4jGson()
                                .fromJson((JsonObject) reg.getRegisterOptions(),
                                        ExecuteCommandOptions.class);
                        List<String> newCommands = executeCommandOptions.getCommands();
                        if (newCommands != null && !newCommands.isEmpty()) {
                            addRegistration(reg, () -> unregisterCommands(newCommands));
                            registerCommands(newCommands);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while getting 'workspace/executeCommand' capability", e);
                    }
                } else {
                    String method = reg.getMethod();
                    try {
                        final TextDocumentServerCapabilityRegistry<? extends TextDocumentRegistrationOptions> registry = getClientFeatures().getCapabilityRegistry(method);
                        if (registry != null) {
                            // register 'textDocument/*' capability
                            JsonObject registerOptions = (JsonObject) reg.getRegisterOptions();
                            if (registerOptions == null) {
                                // ex:
                                // {
                                //  "registrations": [
                                //    {
                                //      "id": "5d54639d-a478-422f-bfb8-112c4b8a93ee",
                                //      "method": "textDocument/hover"
                                //    }
                                //  ]
                                //}
                                registerOptions = new JsonObject();
                            }
                            final TextDocumentRegistrationOptions options = registry.registerCapability(registerOptions);
                            addRegistration(reg, () -> registry.unregisterCapability(options));
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while getting '{}' capability", method, e);
                    }
                }
            });
        });
    }

    private void addRegistration(@NotNull Registration reg, @NotNull Runnable unregistrationHandler) {
        String regId = reg.getId();
        synchronized (dynamicRegistrations) {
            assert !dynamicRegistrations.containsKey(regId) : "Registration id is not unique"; //$NON-NLS-1$
            dynamicRegistrations.put(regId, unregistrationHandler);
        }
    }

    synchronized void setWorkspaceFoldersEnablement(boolean enable) {
        if (serverCapabilities == null) {
            this.serverCapabilities = new ServerCapabilities();
        }
        WorkspaceServerCapabilities workspace = serverCapabilities.getWorkspace();
        if (workspace == null) {
            workspace = new WorkspaceServerCapabilities();
            serverCapabilities.setWorkspace(workspace);
        }
        WorkspaceFoldersOptions folders = workspace.getWorkspaceFolders();
        if (folders == null) {
            folders = new WorkspaceFoldersOptions();
            workspace.setWorkspaceFolders(folders);
        }
        folders.setSupported(enable);
    }

    synchronized void registerCommands(@NotNull List<String> newCommands) {
        ServerCapabilities serverCapabilities = this.getServerCapabilitiesSync();
        if (serverCapabilities != null) {
            ExecuteCommandOptions commandProvider = serverCapabilities.getExecuteCommandProvider();
            if (commandProvider == null) {
                commandProvider = new ExecuteCommandOptions(new ArrayList<>());
                serverCapabilities.setExecuteCommandProvider(commandProvider);
            }
            List<String> existingCommands = commandProvider.getCommands();
            for (String newCmd : newCommands) {
                assert !existingCommands.contains(newCmd) : "Command already registered '" + newCmd + "'"; //$NON-NLS-1$ //$NON-NLS-2$
                existingCommands.add(newCmd);
            }
        }
    }

    public void unregisterCapability(UnregistrationParams params) {
        params.getUnregisterations().forEach(reg -> {
            String id = reg.getId();
            Runnable unregisters;
            synchronized (dynamicRegistrations) {
                unregisters = dynamicRegistrations.get(id);
                dynamicRegistrations.remove(id);
            }
            if (unregisters != null) {
                unregisters.run();
            }
        });
    }

    void unregisterCommands(@NotNull List<String> commands) {
        ServerCapabilities serverCapabilities = this.getServerCapabilitiesSync();
        if (serverCapabilities != null) {
            ExecuteCommandOptions commandProvider = serverCapabilities.getExecuteCommandProvider();
            if (commandProvider != null) {
                List<String> existingCommands = commandProvider.getCommands();
                existingCommands.removeAll(commands);
            }
        }
    }

    /**
     * Adds the URI schemes of a dynamic 'workspace/textDocumentContent' registration to the server capabilities, which
     * the document content provider reads them from.
     */
    synchronized void registerDocumentContentSchemes(@NotNull List<String> schemes) {
        ServerCapabilities serverCapabilities = this.getServerCapabilitiesSync();
        if (serverCapabilities != null) {
            WorkspaceServerCapabilities workspace = serverCapabilities.getWorkspace();
            if (workspace == null) {
                workspace = new WorkspaceServerCapabilities();
                serverCapabilities.setWorkspace(workspace);
            }
            TextDocumentContentRegistrationOptions content = workspace.getTextDocumentContent();
            if (content == null) {
                content = new TextDocumentContentRegistrationOptions(new ArrayList<>());
                workspace.setTextDocumentContent(content);
            }
            List<String> existingSchemes = new ArrayList<>(content.getSchemes() != null ? content.getSchemes() : List.of());
            existingSchemes.addAll(schemes);
            content.setSchemes(existingSchemes);
        }
    }

    /**
     * Removes the URI schemes of a dynamic 'workspace/textDocumentContent' registration, and retires the documents
     * of the schemes the server no longer provides.
     */
    void unregisterDocumentContentSchemes(@NotNull List<String> schemes) {
        synchronized (this) {
            ServerCapabilities serverCapabilities = this.getServerCapabilitiesSync();
            WorkspaceServerCapabilities workspace = serverCapabilities != null ? serverCapabilities.getWorkspace() : null;
            TextDocumentContentRegistrationOptions content = workspace != null ? workspace.getTextDocumentContent() : null;
            if (content != null && content.getSchemes() != null) {
                // Once per registered scheme: another registration can provide the same scheme
                List<String> remainingSchemes = new ArrayList<>(content.getSchemes());
                schemes.forEach(remainingSchemes::remove);
                content.setSchemes(remainingSchemes);
            }
        }
        // Outside this monitor: retiring reads the schemes, and closes editors later on the EDT
        getClientFeatures().retireUnprovidedDocumentContent();
    }

    /**
     * Returns the root of this language server instance, and null if one instance serves the whole project.
     *
     * @return the root of this language server instance, and null if one instance serves the whole project.
     */
    public @Nullable WorkspaceFolder getInstanceRoot() {
        return instanceRoot;
    }

    /**
     * Returns the name of this language server instance for the user: the path of its root relative to the project's
     * directory, the name of its root if that is the project's directory, or its full path outside the project.
     *
     * @return the name of this language server instance, and null if one instance serves the whole project.
     */
    @ApiStatus.Internal
    public @Nullable String getInstanceLabel() {
        var root = instanceRoot;
        if (root == null) {
            return null;
        }
        var label = instanceLabel;
        if (label == null) {
            var directory = LSPIJUtils.findResourceFor(root.getUri());
            if (directory == null) {
                label = root.getName();
            } else {
                var projectDirectory = ProjectUtil.guessProjectDir(initialProject);
                var relativePath = projectDirectory != null && !projectDirectory.equals(directory)
                        // With the separator of the full path that a root outside the project gets
                        ? VfsUtilCore.getRelativePath(directory, projectDirectory, File.separatorChar)
                        : null;
                if (relativePath != null) {
                    label = relativePath;
                } else if (directory.equals(projectDirectory)) {
                    label = directory.getName();
                } else {
                    label = FileUtil.getLocationRelativeToUserHome(directory.getPresentableUrl());
                }
            }
            instanceLabel = label;
        }
        return label;
    }

    /**
     * Returns true if the language server got the given file with didOpen and follows its changes. Unlike
     * {@link #isConnectedTo(URI)}, this doesn't count an entry that only keeps what the language server reported about
     * a file that is open in the editor.
     *
     * @param file the file.
     * @return true if the language server got the given file with didOpen.
     */
    @ApiStatus.Internal
    public boolean isSynchronizing(@NotNull VirtualFile file) {
        var fileUri = toUri(file);
        var openedDocument = fileUri != null ? openedDocuments.get(fileUri) : null;
        return openedDocument != null && openedDocument.getSynchronizer() != null;
    }

    /**
     * Returns true if the language server provides the given document through workspace/textDocumentContent. Such a
     * document isn't part of a folder, so no workspace folder strategy can tell which instance it belongs to.
     *
     * @param file the file.
     * @return true if the language server provides the given document.
     */
    @ApiStatus.Internal
    public boolean providesDocument(@NotNull VirtualFile file) {
        return file instanceof DocumentContentFile document
                && getClientFeatures().findFileByUri(document.getUri().toString()) == file;
    }

    public boolean canOperate(@NotNull VirtualFile file) {
        if (this.isConnectedTo(toUri(file))) {
            return true;
        }
        if (this.openedDocuments.isEmpty()) {
            return true;
        }
        if (file.exists()) {
            return true;
        }
        return serverDefinition.isSingleton();
    }

    private @NotNull LanguageServerLifecycleManager getLanguageServerLifecycleManager() {
        Project project = initialProject;
        if (project.isDisposed()) {
            return NullLanguageServerLifecycleManager.INSTANCE;
        }
        var manager = LanguageServerLifecycleManager.getInstance(project);
        return manager == null ? NullLanguageServerLifecycleManager.INSTANCE : manager;
    }

    /**
     * Returns the current process id and null otherwise.
     *
     * @return the current process id and null otherwise.
     */
    public Long getCurrentProcessId() {
        return currentProcessId;
    }

    public List<String> getCurrentProcessCommandLine() {
        return currentProcessCommandLines;
    }

    /**
     * Returns the server status.
     *
     * @return the server status.
     */
    public ServerStatus getServerStatus() {
        return serverStatus;
    }

// ------------------ Current Process information.

    public LanguageServerException getServerError() {
        return serverError;
    }

    public int getNumberOfRestartAttempts() {
        return numberOfRestartAttempts;
    }

// ------------------ Server status information .

    public int getMaxNumberOfRestartAttempts() {
        return MAX_NUMBER_OF_RESTART_ATTEMPTS;
    }

    /**
     * Returns the language server definition.
     *
     * @return the language server definition.
     */
    @NotNull
    public LanguageServerDefinition getServerDefinition() {
        return serverDefinition;
    }

    /**
     * Returns true if the given file support the 'workspace/willCreateFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/willCreateFiles' and false otherwise.
     */
    public boolean isWillCreateFilesSupported(@NotNull VirtualFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        var uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return false;
        }
        return fileOperationsManager.canWillCreateFiles(uri, file.isDirectory());
    }
    
    /**
     * Returns true if the given file support the 'workspace/didCreateFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/didCreateFiles' and false otherwise.
     */
    public boolean isDidCreateFilesSupported(@NotNull VirtualFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        var uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return false;
        }
        return fileOperationsManager.canDidCreateFiles(uri, file.isDirectory());
    }

    /**
     * Returns true if the given file support the 'workspace/willDeleteFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/willDeleteFiles' and false otherwise.
     */
    public boolean isWillDeleteFilesSupported(@NotNull VirtualFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        var uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return false;
        }
        return fileOperationsManager.canWillDeleteFiles(uri, file.isDirectory());
    }
    
    /**
     * Returns true if the given file support the 'workspace/didDeleteFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/didDeleteFiles' and false otherwise.
     */
    public boolean isDidDeleteFilesSupported(@NotNull VirtualFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        var uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return false;
        }
        return fileOperationsManager.canDidDeleteFiles(uri, file.isDirectory());
    }

    /**
     * Returns true if the given file support the 'workspace/willRenameFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/willRenameFiles' and false otherwise.
     */
    public boolean isWillRenameFilesSupported(@NotNull VirtualFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        var uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return false;
        }
        return fileOperationsManager.canWillRenameFiles(uri, file.isDirectory());
    }

    /**
     * Returns true if the given file support the 'workspace/willRenameFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/willRenameFiles' and false otherwise.
     */
    public boolean isWillRenameFilesSupported(@NotNull PsiFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        var uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return false;
        }
        return fileOperationsManager.canWillRenameFiles(uri, file.isDirectory());
    }

    /**
     * Returns true if the given file support the 'workspace/didRenameFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/didRenameFiles' and false otherwise.
     */
    public boolean isDidRenameFilesSupported(@NotNull VirtualFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        var uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return false;
        }
        return fileOperationsManager.canDidRenameFiles(uri, file.isDirectory());
    }

    /**
     * Returns true if the given file support the 'workspace/didRenameFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/didRenameFiles' and false otherwise.
     */
    public boolean isDidRenameFilesSupported(@NotNull PsiFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        var uri = LSPIJUtils.toUri(file);
        if (uri == null) {
            return false;
        }
        return fileOperationsManager.canDidRenameFiles(uri, file.isDirectory());
    }

    public boolean canCreateFilesSupported() {
        return fileOperationsManager != null && (fileOperationsManager.canWillCreateFiles() || fileOperationsManager.canDidCreateFiles());
    }

    public boolean canDeleteFilesSupported() {
        return fileOperationsManager != null && (fileOperationsManager.canWillDeleteFiles() || fileOperationsManager.canDidDeleteFiles());
    }

    public boolean canRenameFilesSupported() {
        return fileOperationsManager != null && (fileOperationsManager.canWillRenameFiles() || fileOperationsManager.canDidRenameFiles());
    }

    /**
     * Updates a file's cached diagnostics for one provider.
     *
     * @param file        the file whose diagnostic cache is updated
     * @param identifier  the diagnostic provider's cache identifier
     * @param diagnostics the replacement diagnostics; an empty list clears this
     *                    provider's diagnostics
     * @param version     the report's document version, or {@code null} if
     *                    absent
     */
    public void updateDiagnostics(@NotNull VirtualFile file,
                                  @NotNull String identifier,
                                  @NotNull List<Diagnostic> diagnostics,
                                  @Nullable Integer version) {
        var clientFeatures = getClientFeatures();
        var fileUri = FileUriSupport.getFileUri(file, clientFeatures);
        if (fileUri == null) {
            return;
        }
        boolean isOpen = getOpenedDocument(fileUri, false) != null
                || FileEditorManager.getInstance(getProject()).isFileOpen(file);
        final LSPDocumentBase openedOrClosedDocument = isOpen ? getOpenedDocument(fileUri, true) : getClosedDocument(fileUri, true);
        if (openedOrClosedDocument == null) {
            return;
        }
        updateDiagnostics(file, openedOrClosedDocument, identifier, diagnostics, version);
    }

    /**
     * Clears a removed workspace provider's diagnostics from cached documents.
     * Retains other providers and open-document diagnostics owned by an active
     * document pull provider with the same identifier.
     *
     * @param identifier the provider identifier, or {@code null} for the
     *                   default pull provider
     */
    @ApiStatus.Internal
    public void clearDocumentDiagnostics(@Nullable String identifier) {
        clearClosedDocumentDiagnostics(identifier);
        String cacheIdentifier = identifier != null ? identifier : LSPDocumentBase.PULL_DIAGNOSTIC_IDENTIFIER;
        for (var document : getOpenedDocuments()) {
            var synchronizer = document.getSynchronizer();
            if (synchronizer != null && synchronizer.isPullDiagnosticsSupported()
                    && Objects.equals(identifier, getClientFeatures().getDiagnosticFeature().getDiagnosticIdentifier())) {
                continue;
            }
            updateDiagnostics(document.getFile(), document, cacheIdentifier, List.of(), null);
        }
    }

    /**
     * Clears one provider's diagnostics from all cached closed documents.
     * Diagnostics from other providers are retained.
     *
     * @param identifier the provider identifier, or {@code null} for the
     *                   default pull provider
     */
    @ApiStatus.Internal
    public void clearClosedDocumentDiagnostics(@Nullable String identifier) {
        String cacheIdentifier = identifier != null ? identifier : LSPDocumentBase.PULL_DIAGNOSTIC_IDENTIFIER;
        for (var entry : closedDocuments.entrySet()) {
            clearClosedDocumentDiagnostics(entry.getKey(), entry.getValue(), cacheIdentifier);
        }
    }

    /**
     * Clears one provider's diagnostics from a cached closed document.
     * Retains other providers, other files and open-document diagnostics.
     *
     * @param identifier the provider identifier, or {@code null} for the
     *                   default pull provider
     * @param uri        the URI of the closed document to clear
     */
    @ApiStatus.Internal
    public void clearClosedDocumentDiagnostics(@Nullable String identifier, @NotNull String uri) {
        var features = getClientFeatures();
        VirtualFile file = FileUriSupport.findFileByUri(uri, features);
        URI fileUri = file != null ? FileUriSupport.getFileUri(file, features) : URI.create(uri);
        var document = fileUri != null ? getClosedDocument(fileUri, false) : null;
        if (document != null) {
            String cacheIdentifier = identifier != null ? identifier : LSPDocumentBase.PULL_DIAGNOSTIC_IDENTIFIER;
            clearClosedDocumentDiagnostics(fileUri, document, cacheIdentifier);
        }
    }

    private void clearClosedDocumentDiagnostics(URI fileUri, ClosedDocument document, String cacheIdentifier) {
        var features = getClientFeatures();
        String uri = FileUriSupport.toString(fileUri, features);
        VirtualFile file = uri != null ? FileUriSupport.findFileByUri(uri, features) : null;
        if (file != null && file.isValid() && !getProject().isDisposed()) {
            updateDiagnostics(file, document, cacheIdentifier, List.of(), null);
        } else {
            document.updateDiagnostics(cacheIdentifier, List.of(), null);
        }
    }

    private void updateDiagnostics(@NotNull VirtualFile file, @NotNull LSPDocumentBase openedOrClosedDocument,
                                   @NotNull String identifier, @NotNull List<Diagnostic> diagnostics,
                                   @Nullable Integer version) {
        var clientFeatures = getClientFeatures();
        boolean hasErrors;
        synchronized (openedOrClosedDocument) {
            hasErrors = openedOrClosedDocument.hasErrors();
            openedOrClosedDocument.updateDiagnostics(identifier, diagnostics, version);
            try {
                // Notify in cache-update order, using the accepted/merged cache
                // rather than the incoming report (which may have been stale).
                clientFeatures.getDiagnosticFeature().diagnosticsUpdated(file,
                        List.copyOf(openedOrClosedDocument.getDiagnostics()));
            } catch (Exception e) {
                LOGGER.warn("Failed to notify diagnostic update for {}", file, e);
            }
        }

        if ((hasErrors != openedOrClosedDocument.hasErrors()) && clientFeatures.getDiagnosticFeature().canReportProblem(file)) {
            // Report problem in the Project View if the opened/closed document
            // has at least one diagnosis with a severity of error
            LSPDiagnosticUtils.reportProblem(file, openedOrClosedDocument, getProject());
        }
    }

    @NotNull
    public LSPClientFeatures getClientFeatures() {
        if (clientFeatures == null) {
            clientFeatures = getOrCreateClientFeatures();
        }
        return clientFeatures;
    }

    @NotNull
    private synchronized LSPClientFeatures getOrCreateClientFeatures() {
        if (clientFeatures != null) {
            return clientFeatures;
        }
        LSPClientFeatures clientFeatures = getServerDefinition().createClientFeatures();
        clientFeatures.setServerWrapper(this);
        return clientFeatures;
    }

    /**
     * Converts the given {@link VirtualFile} to a {@link URI}, taking into account client-specific features.
     *
     * @param file the virtual file to convert, must not be {@code null}
     * @return the corresponding URI, or {@code null} if the conversion fails
     */
    @Nullable
    public URI toUri(@NotNull VirtualFile file) {
        return FileUriSupport.getFileUri(file, getClientFeatures());
    }

    /**
     * Converts the given {@link URI} to its string representation using client-specific formatting rules.
     *
     * @param uri the URI to convert, must not be {@code null}
     * @return the string representation of the URI
     */
    public String toUriString(@NotNull URI uri) {
        return FileUriSupport.toString(uri, getClientFeatures());
    }

    /**
     * Converts the given {@link VirtualFile} to its URI string representation using client-specific formatting rules.
     *
     * @param file the virtual file to convert, must not be {@code null}
     * @return the URI string representation of the virtual file
     */
    public String toUriString(@NotNull VirtualFile file) {
        return FileUriSupport.toString(file, getClientFeatures());
    }

    /**
     * Returns the language server wrapper's modification tracker.
     *
     * @return the modification tracker
     */
    @NotNull
    ModificationTracker getModificationTracker() {
        return modificationTracker;
    }

    /**
     * Increments the language server wrapper's modification tracker.
     */
    @ApiStatus.Internal
    public void incrementModificationCount() {
        modificationTracker.incModificationCount();
    }

    /**
     * Add LSP trace in the cached LSP traces.
     *
     * @param message         the LSP request/response message to display in the LSP consle.
     * @param messageConsumer the message consumer.
     * @return true if trace has been add in the LSP cache traces and false otherwise.
     */
    public boolean addTrace(@NotNull Message message,
                            @NotNull MessageConsumer messageConsumer) {
        ServerTrace serverTrace = getServerTrace();
        if (serverTrace == ServerTrace.off) {
            return false;
        }
        initLSPTracesIfNeeded();
        if (traces == null || tracing == null) {
            // This case can occur when language server is restarted
            return false;
        }
        traces.add(new LSPTrace(message, messageConsumer, serverTrace, tracing));
        return true;
    }

    private void initLSPTracesIfNeeded() {
        if (traces == null || tracing == null) {
            synchronized (this) {
                if (traces == null) {
                    traces = new ConcurrentLinkedQueue<>();
                }
                if (tracing == null) {
                    tracing = new TracingMessageConsumer();
                }
            }
        }
    }

    /**
     * Returns the LSP cached traces.
     *
     * @return the LSP cached traces.
     */
    public ConcurrentLinkedQueue<LSPTrace> getTraces() {
        initLSPTracesIfNeeded();
        return traces;
    }

    // ------------- LSP Traces

    /**
     * Returns the configured server trace.
     *
     * @return the configured server trace.
     */
    public @NotNull ServerTrace getServerTrace() {
        ServerTrace serverTrace = null;
        ProjectLanguageServerSettings.LanguageServerDefinitionSettings settings =
                ProjectLanguageServerSettings.getInstance(getProject()).getLanguageServerSettings(getServerDefinition().getId());
        if (settings != null) {
            serverTrace = settings.getServerTrace();
        }
        return serverTrace != null ? serverTrace : ServerTrace.getDefaultValue();
    }

    public Alarm getTraceFlushAlarm() {
        if (traceFlushAlarm == null) {
            synchronized (this) {
                if (traceFlushAlarm == null) {
                    traceFlushAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
                }
            }
        }
        return traceFlushAlarm.isDisposed() ? null : traceFlushAlarm;
    }

    /**
     * Installs the language server using the provided {@link ServerInstallationContext}.
     * <p>
     * If a {@link ServerInstaller} is available, this method will:
     * <ul>
     *     <li>Stop the language server if it is currently starting or started,</li>
     *     <li>Reset the installer state,</li>
     *     <li>Execute the installation asynchronously,</li>
     *     <li>Restart the language server after installation if it was running beforehand.</li>
     * </ul>
     * If no installer is configured, the method assumes the server is already installed and
     * immediately returns {@link ServerInstallationStatus#INSTALLED}.
     * </p>
     *
     * @param context the context used to control or customize the installation.
     * @return a {@link CompletableFuture} that completes with the {@link ServerInstallationStatus}
     * once the installation process is done.
     */
    public @NotNull CompletableFuture<ServerInstallationStatus> install(@NotNull ServerInstallationContext context) {
        // Get the configured server installer, if any
        var serverInstaller = getClientFeatures().getServerInstaller();
        if (serverInstaller != null) {
            // Determine whether the server was already running before installation
            boolean wasStarted = getServerStatus() == ServerStatus.starting || getServerStatus() == ServerStatus.started;

            // Stop the current language server if it's running
            return stop()
                    .thenCompose((unused) -> {
                        // Reset the installer internal state before reinstallation
                        serverInstaller.reset();

                        // Perform the installation asynchronously
                        var checkInstallation = serverInstaller.checkInstallation(context);

                        // If the server wasn't started before, no need to restart it afterwards
                        if (!wasStarted) {
                            return checkInstallation;
                        }

                        // If the server was running, restart it after installation completes
                        return checkInstallation.thenApply(status -> {
                            start(); // Restart the server
                            return status;
                        });
                    });

        }

        // If no installer is available, assume the server is already installed
        return CompletableFuture.completedFuture(ServerInstallationStatus.INSTALLED);
    }

    /**
     * Returns true if the language server is stopping and false otherwise.
     *
     * @return true if the language server is stopping and false otherwise.
     */
    public boolean isStopping() {
        return getServerStatus() == ServerStatus.stopping;
    }

    public synchronized CompletableFuture<Void> stop() {
        return stop(isStopping());
    }

    private synchronized CompletableFuture<Void> stop(boolean alreadyStopping) {
        if (alreadyStopping && currentInitializingContext == null) {
            return runningStop;
        }
        return stop(currentInitializingContext);
    }

    private synchronized CompletableFuture<Void> stop(@Nullable InitializingContext initializingContext) {
        @Nullable var languageClient = initializingContext != null ? initializingContext.languageClient : null;
        @Nullable var languageServer = initializingContext != null ? initializingContext.languageServer : null;
        @Nullable var lspStreamProvider = initializingContext != null ? initializingContext.provider : null;
        @Nullable var launcherFuture = initializingContext != null ? initializingContext.launcherFuture : null;
        boolean current = initializingContext != null && initializingContext.equals(currentInitializingContext);
        try {
            updateStatus(ServerStatus.stopping);
            removeStopTimer();
            if (languageClient != null) {
                languageClient.dispose();
            }
            if (current) {
                currentInitializingContext = null;
                if (this.initializeFuture != null) {
                    CancellationSupport.cancel(this.initializeFuture);
                    this.initializeFuture = null;
                }
                this.serverCapabilities = null;
                this.dynamicRegistrations.clear();
                // Dropping the registrations does not run their unregistration, so drop the watchers they added
                fileListener.clearFileSystemWatchers();
                if (clientFeatures != null) {
                    clientFeatures.getWorkspaceFolderFeature().reset();
                }
            }

            if (isDisposed()) {
                // When project is closing we shutdown everything in synch mode
                shutdownAll(languageServer, lspStreamProvider, launcherFuture);
                return CompletableFuture.completedFuture(null);
            } else {
                // We need to shutdown, kill and stop the process in a thread to avoid for instance
                // stopping the new process created with a new start.
                // Use the IntelliJ application pool instead of ForkJoinPool.commonPool() to avoid
                // ForkJoinPool.helpAsyncBlocker() executing the blocking shutdown (up to 5 seconds)
                // in the current thread when stop() is called from a ReadAction.
                // See: https://github.com/redhat-developer/lsp4ij/issues/1672
                runningStop = CompletableFuture.runAsync(() -> {
                    shutdownAll(languageServer, lspStreamProvider, launcherFuture);
                    boolean delayedCurrent = currentInitializingContext == null || currentInitializingContext.equals(initializingContext);
                    if (delayedCurrent) {
                        updateStatus(ServerStatus.stopped);
                    }
                }, AppExecutorUtil.getAppExecutorService());
                return runningStop;
            }
        } finally {
            if (current) {
                this.launcherFuture = null;
                this.lspStreamProvider = null;
                synchronized (documentsLock) {
                    scopedDocuments.clear();
                }
                disposeFileSupports(this.openedDocuments.values().stream()
                        .map(OpenedDocument::getFile)
                        .toList());
                while (!this.openedDocuments.isEmpty()) {
                    disconnect(this.openedDocuments.keySet().iterator().next(), false);
                }
                // Old replies are refused by connection identity, so the new connection can start afresh.
                documentVersion.set(0);
                if (!closedDocuments.isEmpty()) {
                    Set<URI> fileUris = new HashSet<>(closedDocuments.keySet());
                    closedDocuments.clear();
                    clearProblem(fileUris, getClientFeatures(), getProject());
                }
                this.languageServer = null;
                this.languageClient = null;

                if (messageBusConnection != null) {
                    messageBusConnection.disconnect();
                    messageBusConnection = null;
                }
            }
        }
    }

    /**
     * Disposes the LSP file support of the given files, which were connected to the stopped language server: it
     * cancels their pending LSP requests and drops their cached results. LSP4J does not complete the requests which are
     * still pending when the connection closes, so without this they would never complete, and each file would return
     * them from its cache, even after a restart, until it is modified.
     *
     * @param files the files which were connected to the stopped language server.
     */
    private void disposeFileSupports(@NotNull List<VirtualFile> files) {
        Project project = getProject();
        if (files.isEmpty() || project.isDisposed()) {
            return;
        }
        // stop() holds the monitor of this wrapper, so it must not wait for a read action to find the Psi files
        ReadAction.nonBlocking(() -> {
                    for (VirtualFile file : files) {
                        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
                        if (psiFile != null && LSPFileSupport.hasSupport(psiFile)) {
                            LSPFileSupport.getSupport(psiFile).dispose();
                        }
                    }
                })
                .expireWith(project)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private void shutdownAll(LanguageServer languageServerInstance, StreamConnectionProvider provider, Future<?> serverFuture) {
        if (languageServerInstance != null && provider != null && provider.isAlive()) {
            // The LSP language server instance and the process which starts the language server is alive. Process
            // - shutdown
            // - exit

            // shutdown the language server
            if (provider.isAlive()) {
                try {
                    shutdownLanguageServerInstance(languageServerInstance);
                } catch (Exception ex) {
                    getLanguageServerLifecycleManager().onError(this, ex);
                }

                // exit the language server
                // Consume language server exit() before cancelling launcher future (serverFuture.cancel())
                // to avoid having error like "The pipe is being closed".
                try {
                    exitLanguageServerInstance(languageServerInstance);
                } catch (Exception ex) {
                    getLanguageServerLifecycleManager().onError(this, ex);
                }
            }
        }

        CancellationSupport.cancel(serverFuture);

        if (provider != null) {
            provider.stop();
        }

    }

    private void shutdownLanguageServerInstance(LanguageServer languageServerInstance) throws Exception {
        CompletableFuture<Object> shutdown = languageServerInstance.shutdown();
        if (!isDisposed()) {
            try {
                shutdown.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (TimeoutException ex) {
                String message = "Timeout error while shutdown the language server '" + serverDefinition.getId() + "'";
                LOGGER.warn(message, ex);
                throw new Exception(message, ex);
            } catch (Exception ex) {
                String message = "Error while shutdown the language server '" + serverDefinition.getId() + "'";
                LOGGER.warn(message, ex);
                throw new Exception(message, ex);
            }
        }
    }

    private void exitLanguageServerInstance(LanguageServer languageServerInstance) throws Exception {
        try {
            languageServerInstance.exit();
        } catch (Exception ex) {
            String message = "Error while exit the language server '" + serverDefinition.getId() + "'";
            LOGGER.error(message, ex);
            throw new Exception(message, ex);
        }
    }

    @Override
    public void dispose() {
        dispose(false);
    }

    public void dispose(boolean refreshEditorFeature) {
        if (disposed) {
            return;
        }
        this.disposed = true;
        try {
            fileListener.disposeFileSystemWatchers();
            try {
                // A language server that never started has nothing to stop, and no status to report
                if (currentInitializingContext != null || getServerStatus() != ServerStatus.none) {
                    stopAndRefreshEditorFeature(refreshEditorFeature, false);
                }
            } finally {
                // Also when the stop fails, as when the connection fails to stop: the executors would keep their threads
                stopDispatcher();
                if (clientFeatures != null) {
                    clientFeatures.dispose();
                    clientFeatures = null;
                }
            }
        } finally {
            // Also when the stop or the cleanup fails: a language server that is disposed is never disposed again
            var notification = errorNotification;
            if (notification != null) {
                // Its actions would keep this language server, which no longer starts
                errorNotification = null;
                notification.expire();
            }
            getLanguageServerLifecycleManager().onDisposed(this);
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Carries the didOpen text and its editor stamp together across asynchronous connection setup.
     * A stamp of -1 means no stamp was captured; when text is null, both are read at didOpen.
     */
    record LSPFileConnectionInfo(@Nullable Document document, @Nullable String documentText,
                                long documentModificationStamp, @Nullable String languageId, boolean waitForDidOpen) {
        LSPFileConnectionInfo(@Nullable Document document, @Nullable String documentText,
                              @Nullable String languageId, boolean waitForDidOpen) {
            this(document, documentText, -1, languageId, waitForDidOpen);
        }
    }

    public LanguageServerWrapper.@NotNull LSPFileConnectionInfo createFileConnectionInfo(@NotNull VirtualFile file,
                                                                                         @NotNull Document document,
                                                                                         boolean waitForDidOpen) {
        // Capture both under one read action. Using a later stamp with this text could authorize
        // an edit for content the server never saw. The consumer must not reread text during a VFS rename.
        if (ApplicationManager.getApplication().isReadAccessAllowed()) {
            return captureFileConnectionInfo(file, document, waitForDidOpen);
        }
        // The capture has no side effects, so a cancellable read can retry it after a pending write action.
        return runCancellableReadAction(() -> captureFileConnectionInfo(file, document, waitForDidOpen), this);
    }

    private @NotNull LSPFileConnectionInfo captureFileConnectionInfo(@NotNull VirtualFile file,
                                                                   @NotNull Document document,
                                                                   boolean waitForDidOpen) {
        String text = document.getText();
        long modificationStamp = document.getModificationStamp();
        String languageId = getServerDefinition().getLanguageId(file, getProject());
        return new LSPFileConnectionInfo(document, text, modificationStamp, languageId, waitForDidOpen);
    }

    // LSP traces
    public record LSPTrace(@NotNull Message message,
                           @NotNull MessageConsumer messageConsumer,
                           @NotNull ServerTrace serverTrace,
                           @NotNull TracingMessageConsumer tracing) {
        public String toMessage() {
            return tracing.log(message, messageConsumer, serverTrace);
        }
    }

    private class InitializingContext {

        public StreamConnectionProvider provider;
        public LanguageClientImpl languageClient;
        public LanguageServer languageServer;
        public Future<?> launcherFuture;
        public InitializeResult initializeResult;
    }


}
