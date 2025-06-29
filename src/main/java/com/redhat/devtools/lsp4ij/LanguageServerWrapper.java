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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.messages.MessageBusConnection;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.console.explorer.TracingMessageConsumer;
import com.redhat.devtools.lsp4ij.features.diagnostics.LSPDiagnosticUtils;
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
import com.redhat.devtools.lsp4ij.server.definition.ClientConfigurableLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.settings.ProjectLanguageServerSettings;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.IdentifiableMessage;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.UnaryOperator;

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
    @Nullable
    protected final URI initialPath;
    protected final InitializeParams initParams = new InitializeParams();
    @NotNull
    private final LanguageServerDefinition serverDefinition;
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final ExecutorService dispatcher;
    private final ExecutorService listener;
    private final SimpleModificationTracker modificationTracker = new SimpleModificationTracker();
    /**
     * Map containing unregistration handlers for dynamic capability registrations.
     */
    private final @NotNull
    Map<String, Runnable> dynamicRegistrations = new HashMap<>();
    private final LSPFileListener fileListener = new LSPFileListener(this);
    private final AtomicInteger keepAliveCounter = new AtomicInteger();
    protected StreamConnectionProvider lspStreamProvider;
    private MessageBusConnection messageBusConnection;
    private Future<?> launcherFuture;
    private int numberOfRestartAttempts;
    private @Nullable CompletableFuture<Void> initializeFuture;
    private LanguageServer languageServer;
    private LanguageClientImpl languageClient;
    private ServerCapabilities serverCapabilities;
    private Timer timer;
    private ServerStatus serverStatus;
    private boolean disposed;
    private LanguageServerException serverError;
    private Long currentProcessId;
    private List<String> currentProcessCommandLines;
    private boolean initiallySupportsWorkspaceFolders = false;
    private FileOperationsManager fileOperationsManager;

    private LSPClientFeatures clientFeatures;
    // error notification displayed when server start fails.
    private @Nullable Notification errorNotification;
    private @Nullable TracingMessageConsumer tracing;
    private volatile @Nullable ConcurrentLinkedQueue<LSPTrace> traces;
    private @Nullable Alarm traceFlushAlarm;

    /* Backwards compatible constructor */
    public LanguageServerWrapper(@NotNull Project project, @NotNull LanguageServerDefinition serverDefinition) {
        this(project, serverDefinition, null);
    }

    /**
     * Unified private constructor to set sensible defaults in all cases
     */
    public LanguageServerWrapper(@NotNull Project project,
                                 @NotNull LanguageServerDefinition serverDefinition,
                                 @Nullable URI initialPath) {
        this.initialProject = project;
        this.initialPath = initialPath;
        this.serverDefinition = serverDefinition;
        this.openedDocuments = new HashMap<>();
        this.closedDocuments = new HashMap<>();
        String projectName = sanitize(!serverDefinition.isSingleton() ? ("@" + project.getName()) : "");  //$NON-NLS-1$//$NON-NLS-2$
        String dispatcherThreadNameFormat = "LS-" + serverDefinition.getId() + projectName + "#dispatcher"; //$NON-NLS-1$ //$NON-NLS-2$
        this.dispatcher = Executors
                .newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(dispatcherThreadNameFormat).build());

        // Executor service passed through to the LSP4j layer when we attempt to start the LS. It will be used
        // to create a listener that sits on the input stream and processes inbound messages (responses, or server-initiated
        // requests).
        String listenerThreadNameFormat = "LS-" + serverDefinition.getId() + projectName + "#listener-%d"; //$NON-NLS-1$ //$NON-NLS-2$
        this.listener = Executors
                .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(listenerThreadNameFormat).build());
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

        // Only really needed for testing - the listener (an instance of ConcurrentMessageProcessor) should exit
        // as soon as the input stream from the LS is closed, and a cached thread pool will recycle idle
        // threads after a 60 second timeout - or immediately in response to JVM shutdown.
        // If we don't do this then a full test run will generate a lot of threads because we create new
        // instances of this class for each test
        this.listener.shutdownNow();
    }

    public synchronized void stopAndDisable() {
        stopAndRefreshEditorFeature(true, true);
    }

    /**
     * Stop the language server, disable it and
     * remove IJ code vision, inlay hints, folding
     * for all opened editors which edit the files associated to the language server.
     */
    private synchronized void stopAndRefreshEditorFeature(boolean refreshEditorFeature, boolean disable) {
        // Collect opened files before stopping the language server
        List<VirtualFile> openedFiles = refreshEditorFeature ? openedDocuments.entrySet()
                .stream()
                .map(c -> c.getValue().getFile())
                .toList() : Collections.emptyList();

        // Disable the language server
        if (disable) {
            setEnabled(false);
        }

        // Stop the language server
        stop();

        if (!openedFiles.isEmpty()) {
            // remove IJ code vision, inlay hints, folding
            // for all opened editors which edit the files associated to the language server.
            for (var file : openedFiles) {
                // refresh IJ code visions, inlay hints, folding
                EditorFeatureManager.getInstance(getProject())
                        .refreshEditorFeature(file, EditorFeatureType.ALL, true, new VirtualFileCancelChecker(file));
            }
        }
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
                    LanguageServiceAccessor.getInstance(getProject()).
                            sendDidOpenAndRefreshEditorFeatureForOpenedFiles(serverDefinition, getProject());
                });
    }

    public boolean isEnabled() {
        return !isDisposed() && serverDefinition.isEnabled(initialProject);
    }

    private void setEnabled(boolean enabled) {
        this.serverDefinition.setEnabled(enabled, initialProject);
    }

    /**
     * Starts a language server and triggers initialization. If language server is
     * started and active, does nothing. If language server is inactive, restart it.
     *
     * @throws LanguageServerException thrown when the language server cannot be started
     */
    public synchronized void start() throws LanguageServerException {
        if (serverError != null) {
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
            this.initializeFuture = CompletableFuture.supplyAsync(() -> {

                        // Initialize LSP traces
                        if (tracing != null) {
                            tracing = null;
                        }

                        var oldProvider = this.lspStreamProvider;
                        if (oldProvider != null) {
                            // Ensure process is stopped before starting a new process
                            oldProvider.stop();
                        }
                        var provider = this.lspStreamProvider = serverDefinition.createConnectionProvider(initialProject);
                        initParams.setInitializationOptions(provider.getInitializationOptions(rootURI));

                        // Add error log
                        provider.addLogErrorHandler(error -> ServerMessageHandler.logMessage(this.getServerDefinition(), new MessageParams(MessageType.Error, error), getProject()));
                        provider.addUnexpectedServerStopHandler(() -> {
                            // There is an unexpected stop of the connection
                            // 1. the process was killed outside IntelliJ
                            // 2. the start command takes some times and fails
                            // -->
                            // Stop the language server
                            stop();
                            // Show a notification error with "The server was stopped unexpectedly." error message.
                            serverError = new ServerWasStoppedException("The server was stopped unexpectedly.");
                            showNotificationStartServerError();
                        });

                        // Starting process...
                        updateStatus(ServerStatus.starting);
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

                        // Throws the CannotStartProcessException exception if process is not alive.
                        // This use case comes for instance when the start process command fails (not a valid start command)
                        provider.ensureIsAlive();
                        return null;
                    }).thenRun(() -> {
                        languageClient = serverDefinition.createLanguageClient(initialProject);
                        languageClient.setServerWrapper(this);
                        initParams.setProcessId(getParentProcessId());

                        if (rootURI != null) {
                            initParams.setRootUri(toUriString(rootURI));
                            initParams.setRootPath(rootURI.getPath());
                        }

                        // Get the useIntegerIds setting for this language server
                        boolean useIntegerIds = false;
                        if (serverDefinition instanceof ClientConfigurableLanguageServerDefinition clientConfigurableDefinition) {
                            var clientConfiguration = clientConfigurableDefinition.getLanguageServerClientConfiguration();
                            if (clientConfiguration != null) {
                                useIntegerIds = clientConfiguration.jsonRpc.useIntegerIds;
                            }
                        }


                        final boolean finalUseIntegerIds = useIntegerIds;
                        UnaryOperator<MessageConsumer> wrapper = consumer -> (message -> {
                            if (isDisposed()) {
                                return;
                            }

                            // Create a copy of the message with integer ID if enabled
                            final Message processedMessage = createProcessedMessage(message, finalUseIntegerIds);


                            logMessage(processedMessage, consumer);
                            try {
                                // To avoid having some lock problem when message is written in the stream output
                                // (when there are a lot of messages to write it)
                                // we consume the message in async mode
                                CompletableFuture.runAsync(() -> consumer.consume(processedMessage))
                                        .exceptionally(e -> {
                                            // Log in the LSP console the error
                                            getLanguageServerLifecycleManager().onError(this, e);
                                            return null;
                                        });
                            } catch (Throwable e) {
                                // Log in the LSP console the error
                                getLanguageServerLifecycleManager().onError(this, e);
                                throw e;
                            }
                            final StreamConnectionProvider currentConnectionProvider = this.lspStreamProvider;
                            if (currentConnectionProvider != null && isActive()) {
                                currentConnectionProvider.handleMessage(processedMessage, this.languageServer, rootURI);
                            }
                        });

                        Launcher<LanguageServer> launcher = serverDefinition.createLauncherBuilder(false) //
                                .setLocalService(languageClient)//
                                .setRemoteInterface(serverDefinition.getServerInterface())//
                                .setInput(lspStreamProvider.getInputStream())//
                                .setOutput(lspStreamProvider.getOutputStream())//
                                .setExecutorService(listener)//
                                .wrapMessages(wrapper)//
                                .create();
                        this.languageServer = launcher.getRemoteProxy();
                        languageClient.connect(languageServer);
                        this.launcherFuture = launcher.startListening();
                    })
                    .thenCompose(unused -> initServer(rootURI))
                    .thenAccept(res -> {
                        serverError = null;
                        if (errorNotification != null) {
                            // Close the current error notification
                            // displayed when server start fails.
                            errorNotification.expire();
                            errorNotification = null;
                        }
                        serverCapabilities = res.getCapabilities();
                        getClientFeatures().setServerCapabilities(serverCapabilities);
                        this.initiallySupportsWorkspaceFolders = supportsWorkspaceFolders(serverCapabilities);
                    }).thenRun(() -> this.languageServer.initialized(new InitializedParams())).thenRun(() -> {
                        initializeFuture.thenRunAsync(() -> {
                            for (VirtualFile fileToReconnect : filesToReconnect) {
                                connect(fileToReconnect, new LSPFileConnectionInfo(null, null, null, true));
                            }
                        });

                        messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
                        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileListener);
                        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(fileListener));

                        fileOperationsManager = new FileOperationsManager(this);
                        fileOperationsManager.setServerCapabilities(serverCapabilities);

                        updateStatus(ServerStatus.started);
                    }).exceptionally(e -> {
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
                        stop(false);
                        return null;
                    });
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
            errorNotification = showNotificationError(this.getServerDefinition(), "Cannot start server", serverError, this.getProject());
        }
    }

    private CompletableFuture<InitializeResult> initServer(final VirtualFile rootURI) {
        initParams.setCapabilities(ClientCapabilitiesFactory
                .create(lspStreamProvider.getExperimentalFeaturesPOJO()));
        initParams.setClientInfo(getClientInfo());
        initParams.setTrace(this.lspStreamProvider.getTrace(rootURI));

        var folders = LSPIJUtils.toWorkspaceFolders(initialProject, getClientFeatures());
        initParams.setWorkspaceFolders(folders);

        // Customize initialize params if needed
        getClientFeatures().initializeParams(initParams);

        // no then...Async future here as we want this chain of operation to be sequential and "atomic"-ish
        return languageServer.initialize(initParams);
    }

    @Nullable
    private VirtualFile getRootURI() {
        var roots = LSPIJUtils.getRoots(getProject());
        if (roots.size() == 1) {
            return roots.iterator().next();
        }
        return null;
    }

    private void logMessage(Message message, MessageConsumer consumer) {
        getLanguageServerLifecycleManager().logLSPMessage(message, consumer, this);
    }

    private void removeStopTimer(boolean stopping) {
        if (timer != null) {
            timer.cancel();
            timer = null;
            if (!stopping) {
                updateStatus(ServerStatus.started);
            }
        }
    }

    public void updateStatus(@NotNull ServerStatus serverStatus) {
        boolean statusChanged = (this.serverStatus != serverStatus) && (serverStatus != ServerStatus.none);
        this.serverStatus = serverStatus;
        // If this is an "interesting" status change, increment the project-level modification tracker and the wrapper's
        // modification tracker before firing events
        if (statusChanged) {
            LanguageServiceAccessor.getInstance(getProject()).incrementModificationCount();
            incrementModificationCount();
            getLanguageServerLifecycleManager().onStatusChanged(this);
            if (languageClient != null) {
                languageClient.handleServerStatusChanged(serverStatus);
            }
            getClientFeatures().handleServerStatusChanged(serverStatus);
        }
    }

    private void startStopTimer() {
        timer = new Timer("Stop Language Server Timer"); //$NON-NLS-1$
        updateStatus(ServerStatus.stopping);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    stop();
                } catch (Throwable t) {
                    //Need to catch time task exceptions, or it will cancel the timer
                    LOGGER.error("Failed to stop language server " + LanguageServerWrapper.this.serverDefinition.getId(), t);
                }
            }
        }, TimeUnit.SECONDS.toMillis(this.serverDefinition.getLastDocumentDisconnectedTimeout()));
    }

    /**
     * @return whether the underlying connection to language server is still active
     */
    public boolean isActive() {
        return this.launcherFuture != null && !this.launcherFuture.isDone() && !this.launcherFuture.isCancelled();
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
        stopAndRefreshEditorFeature(refreshEditorFeature, false);
        stopDispatcher();
        if (clientFeatures != null) {
            clientFeatures.dispose();
            clientFeatures = null;
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Returns true if the language server is stopping and false otherwise.
     *
     * @return true if the language server is stopping and false otherwise.
     */
    public boolean isStopping() {
        return this.stopping.get();
    }

    public synchronized CompletableFuture<Void> stop() {
        final boolean alreadyStopping = this.stopping.getAndSet(true);
        return stop(alreadyStopping);
    }

    public synchronized CompletableFuture<Void> stop(boolean alreadyStopping) {
        try {
            if (alreadyStopping) {
                return CompletableFuture.completedFuture(null);
            }
            updateStatus(ServerStatus.stopping);

            removeStopTimer(true);
            if (this.languageClient != null) {
                this.languageClient.dispose();
            }

            if (this.initializeFuture != null) {
                CancellationSupport.cancel(this.initializeFuture);
                this.initializeFuture = null;
            }

            this.serverCapabilities = null;
            this.dynamicRegistrations.clear();

            if (isDisposed()) {
                // When project is closing we shutdown everything in synch mode
                shutdownAll(languageServer, lspStreamProvider, launcherFuture);
                return CompletableFuture.completedFuture(null);
            } else {
                // We need to shutdown, kill and stop the process in a thread to avoid for instance
                // stopping the new process created with a new start.
                final Future<?> serverFuture = this.launcherFuture;
                final StreamConnectionProvider provider = this.lspStreamProvider;
                final LanguageServer languageServerInstance = this.languageServer;

                Runnable shutdownKillAndStopFutureAndProvider = () -> {
                    shutdownAll(languageServerInstance, provider, serverFuture);
                    this.stopping.set(false);
                    updateStatus(ServerStatus.stopped);
                };
                return CompletableFuture.runAsync(shutdownKillAndStopFutureAndProvider);
            }
        } finally {
            this.launcherFuture = null;
            this.lspStreamProvider = null;
            if (this.initializeFuture != null) {
                CancellationSupport.cancel(this.initializeFuture);
                this.initializeFuture = null;
            }

            while (!this.openedDocuments.isEmpty()) {
                disconnect(this.openedDocuments.keySet().iterator().next(), false);
            }
            while (!this.closedDocuments.isEmpty()) {
                URI fileUri = closedDocuments.keySet().iterator().next();
                closedDocuments.remove(fileUri);
                clearProblem(fileUri);
            }
            this.languageServer = null;
            this.languageClient = null;

            if (messageBusConnection != null) {
                messageBusConnection.disconnect();
            }
        }
    }

    private void clearProblem(@NotNull URI fileUri) {
        VirtualFile file = FileUriSupport.findFileByUri(fileUri.toASCIIString(), getClientFeatures());
        if (file != null && clientFeatures.getDiagnosticFeature().canReportProblem(file)) {
            LSPDiagnosticUtils.clearProblem(file, getProject());
        }
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
        removeStopTimer(false);

        URI fileUri = toUri(file);
        if (fileUri == null) {
            // Invalid file uri
            return CompletableFuture.completedFuture(null);
        }

        boolean waitForDidOpen = fileConnectionInfo.waitForDidOpen();
        var ls = getLanguageServerWhenDidOpen(fileUri, waitForDidOpen);
        if (ls != null) {
            return ls;
        }

        // start language server if needed
        start();

        if (this.initializeFuture == null) {
            // The "initialize" future is not initialized, return null as language server.
            return CompletableFuture.completedFuture(null);
        }

        return initializeFuture.thenComposeAsync(theVoid -> {
            // Here, the "initialize" future is initialized

            // Check if file is already opened (without synchronized block)
            var ls2 = getLanguageServerWhenDidOpen(fileUri, waitForDidOpen);
            if (ls2 != null) {
                return ls2;
            }

            // Here the file is not already connected
            // To connect the file, we need the document instance to add LSP document listener to manage didOpen, didChange, etc.
            Document optionalDocument = fileConnectionInfo.document();
            Document document = optionalDocument != null ? optionalDocument : LSPIJUtils.getDocument(file);
            if (document == null) {
                return CompletableFuture.completedFuture(null);
            }

            synchronized (closedDocuments) {
                closedDocuments.remove(fileUri);
            }
            synchronized (openedDocuments) {
                // Check again if file is already opened (within synchronized block)
                ls2 = getLanguageServerWhenDidOpen(fileUri, waitForDidOpen);
                if (ls2 != null) {
                    return ls2;
                }

                DocumentContentSynchronizer synchronizer = createDocumentContentSynchronizer(toUriString(fileUri), file, document, fileConnectionInfo.documentText(), fileConnectionInfo.languageId());
                document.addDocumentListener(synchronizer);
                OpenedDocument data = new OpenedDocument(new LanguageServerItem(languageServer, this), file, synchronizer);
                LanguageServerWrapper.this.openedDocuments.put(fileUri, data);

                if (waitForDidOpen) {
                    return getLanguageServerWhenDidOpen(synchronizer.getDidOpenFuture());
                }
                return CompletableFuture.completedFuture(languageServer);
            }
        });
    }

    @Nullable
    private CompletableFuture<LanguageServer> getLanguageServerWhenDidOpen(@Nullable URI fileUri, boolean waitForDidOpen) {
        if (fileUri == null) {
            return null;
        }
        var openedDocument = openedDocuments.get(fileUri);
        if (openedDocument != null) {
            if (!waitForDidOpen) {
                return CompletableFuture.completedFuture(languageServer);
            }
            // The file is already connected.
            // returns the language server instance when didOpen happened
            var didOpenFuture = openedDocument.getSynchronizer().getDidOpenFuture();
            return getLanguageServerWhenDidOpen(didOpenFuture);
        }
        return null;
    }

    private CompletableFuture<LanguageServer> getLanguageServerWhenDidOpen(@NotNull CompletableFuture<LanguageServer> didOpenFuture) {
        if (didOpenFuture.isDone()) {
            // The didOpen has happened, no need to wait for the didOpen
            // to return the language server
            return CompletableFuture.completedFuture(languageServer);
        }
        // The didOpen has not happened, wait for the end of didOpen
        // to return the language server
        return didOpenFuture
                .thenApplyAsync(theVoid -> languageServer);
    }

    @NotNull
    private DocumentContentSynchronizer createDocumentContentSynchronizer(@NotNull String fileUri,
                                                                          @NotNull VirtualFile file,
                                                                          @NotNull Document document,
                                                                          @Nullable String documentText,
                                                                          @Nullable String languageId) {
        TextDocumentSyncOptions syncOptions = createSyncOptions();
        return new DocumentContentSynchronizer(this, fileUri, file, document, documentText, languageId, syncOptions);
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
                TextDocumentSyncOptions newSyncOptions = syncOptions.getRight();
                if (newSyncOptions.getSave() == null) {
                    newSyncOptions.setSave(true);
                }
                return newSyncOptions;
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
        OpenedDocument openedDocument = this.openedDocuments.remove(fileUri);
        if (openedDocument != null) {
            // Remove the listener from the old document stored in synchronizer
            DocumentContentSynchronizer synchronizer = openedDocument.getSynchronizer();
            synchronizer.getDocument().removeDocumentListener(synchronizer);
            synchronizer.dispose();
            clearProblem(fileUri);
        }
        if (stopIfNoOpenedFiles) {
            maybeShutdown();
        }
    }

    private void maybeShutdown() {
        if (!keepAlive()) {
            if (this.serverDefinition.getLastDocumentDisconnectedTimeout() != 0 && !ApplicationManager.getApplication().isUnitTestMode()) {
                removeStopTimer(true);
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
     * Returns the LSP file data coming from this language server for the given file uri.
     *
     * @param fileUri the file Uri.
     * @return the LSP file data coming from this language server for the given file uri.
     */
    public @Nullable OpenedDocument getOpenedDocument(URI fileUri) {
        return openedDocuments.get(fileUri);
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
            synchronized (closedDocuments) {
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
        if (initializeFuture != null && !this.initializeFuture.isDone()) {
            return initializeFuture.thenApply(r -> this.languageServer);
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
        return getInitializedServer()
                .thenApplyAsync(fn, this.dispatcher);
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

    public void registerCapability(RegistrationParams params) {
        if (initializeFuture == null) {
            return;
        }
        initializeFuture.thenRun(() -> {
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
                        fileListener.registerFileSystemWatchers(reg.getId(), options.getWatchers());
                        addRegistration(reg, () -> fileListener.unregisterFileSystemWatchers(reg.getId()));
                    } catch (Exception e) {
                        LOGGER.error("Error while getting 'workspace/didChangeWatchedFiles' capability", e);
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
                        LOGGER.error("Error while getting '" + method + "' capability", e);
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

    int getVersion(VirtualFile file) {
        if (file != null) {
            OpenedDocument data = openedDocuments.get(LSPIJUtils.toUri(file));
            if (data != null) {
                var synchronizer = data.getSynchronizer();
                if (synchronizer != null) {
                    return synchronizer.getVersion();
                }
            }
        }
        return -1;
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

// ------------------ Current Process information.

    /**
     * Returns the server status.
     *
     * @return the server status.
     */
    public ServerStatus getServerStatus() {
        return serverStatus;
    }

    public LanguageServerException getServerError() {
        return serverError;
    }

// ------------------ Server status information .

    public int getNumberOfRestartAttempts() {
        return numberOfRestartAttempts;
    }

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
     * Returns true if the given file support the 'workspace/willRenameFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/willRenameFiles' and false otherwise.
     */
    public boolean isWillRenameFilesSupported(PsiFile file) {
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

    /**
     * Update diagnostics for the given file URi.
     *
     * @param file        the virtual file.
     * @param identifier  the diagnostic identifier (lsp4ij.publish, lsp4ij.push, custom identifier).
     * @param diagnostics the diagnostics to update.
     */
    public void updateDiagnostics(@NotNull VirtualFile file,
                                  @NotNull String identifier,
                                  @NotNull List<Diagnostic> diagnostics) {
        var clientFeatures = getClientFeatures();
        var fileUri = FileUriSupport.getFileUri(file, clientFeatures);
        LSPDocumentBase openedOrClosedDocument = null;
        openedOrClosedDocument = getOpenedDocument(fileUri);
        if (openedOrClosedDocument != null) {
            // Update diagnostics for opened file
            synchronized (openedOrClosedDocument) {
                openedOrClosedDocument.updateDiagnostics(identifier, diagnostics);
            }
        } else {
            // Update diagnostics for closed file
            openedOrClosedDocument = getClosedDocument(fileUri, true);
            synchronized (openedOrClosedDocument) {
                openedOrClosedDocument.updateDiagnostics(identifier, diagnostics);
            }
        }
        if (openedOrClosedDocument == null) {
            return;
        }

        if (clientFeatures.getDiagnosticFeature().canReportProblem(file)) {
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
    URI toUri(@NotNull VirtualFile file) {
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

    // ------------- LSP Traces

    /**
     * Returns the LSP cached traces.
     *
     * @return the LSP cached traces.
     */
    public ConcurrentLinkedQueue<LSPTrace> getTraces() {
        initLSPTracesIfNeeded();
        return traces;
    }

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

    record LSPFileConnectionInfo(@Nullable Document document, @Nullable String documentText,
                                 @Nullable String languageId, boolean waitForDidOpen) {
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

    /**
     * Creates a processed message with integer ID if useIntegerIds is enabled.
     * Returns a copy of the message with converted ID, or the original message if no conversion is needed.
     */
    private Message createProcessedMessage(Message message, boolean useIntegerIds) {
        if (!useIntegerIds || !(message instanceof IdentifiableMessage identifiableMessage)) {
            return message;
        }

        String id = identifiableMessage.getId();
        if (id == null) {
            return message;
        }

        try {
            int intId = Integer.parseInt(id);
            // Create a new message with integer ID instead of modifying the original
            if (message instanceof RequestMessage original) {
                RequestMessage copy = new RequestMessage();
                copy.setMethod(original.getMethod());
                copy.setParams(original.getParams());
                copy.setId(intId);
                return copy;
            } else if (message instanceof ResponseMessage original) {
                ResponseMessage copy = new ResponseMessage();
                copy.setResult(original.getResult());
                copy.setError(original.getError());
                copy.setId(intId);
                return copy;
            }
        } catch (NumberFormatException e) {
            // Keep original message if conversion fails
        }

        return message;
    }

}
