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
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBusConnection;
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.lsp4ij.features.files.operations.FileOperationsManager;
import com.redhat.devtools.lsp4ij.internal.ClientCapabilitiesFactory;
import com.redhat.devtools.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.lifecycle.NullLanguageServerLifecycleManager;
import com.redhat.devtools.lsp4ij.server.*;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.redhat.devtools.lsp4ij.internal.IntelliJPlatformUtils.getClientInfo;

/**
 * Language server wrapper.
 */
public class LanguageServerWrapper implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerWrapper.class);

    private static final int MAX_NUMBER_OF_RESTART_ATTEMPTS = 20; // TODO move this max value in settings

    private MessageBusConnection messageBusConnection;

    @NotNull
    private final LanguageServerDefinition serverDefinition;
    @NotNull
    protected final Project initialProject;
    @NotNull
    protected Map<URI, LSPVirtualFileData> connectedDocuments;
    @Nullable
    protected final URI initialPath;
    protected final InitializeParams initParams = new InitializeParams();

    protected StreamConnectionProvider lspStreamProvider;
    private Future<?> launcherFuture;

    private int numberOfRestartAttempts;
    private CompletableFuture<Void> initializeFuture;
    private LanguageServer languageServer;
    private LanguageClientImpl languageClient;
    private ServerCapabilities serverCapabilities;
    private Timer timer;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    private ServerStatus serverStatus;

    private boolean disposed;

    private LanguageServerException serverError;

    private Long currentProcessId;

    private List<String> currentProcessCommandLines;

    private final ExecutorService dispatcher;

    private final ExecutorService listener;

    /**
     * Map containing unregistration handlers for dynamic capability registrations.
     */
    private final @NotNull
    Map<String, Runnable> dynamicRegistrations = new HashMap<>();
    private boolean initiallySupportsWorkspaceFolders = false;
    private final LSPFileListener fileListener = new LSPFileListener(this);

    private FileOperationsManager fileOperationsManager;

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
        this.connectedDocuments = new HashMap<>();
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
        setEnabled(false);
        stop();
    }

    public synchronized void restart() {
        numberOfRestartAttempts = 0;
        setEnabled(true);
        stop();
        start();
    }

    private void setEnabled(boolean enabled) {
        this.serverDefinition.setEnabled(enabled, initialProject);
    }

    public boolean isEnabled() {
        return serverDefinition.isEnabled(initialProject);
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
                for (Map.Entry<URI, LSPVirtualFileData> entry : this.connectedDocuments.entrySet()) {
                    filesToReconnect.add(entry.getValue().getFile());
                }
                stop();
            }
        }

        if (this.initializeFuture == null) {
            final VirtualFile rootURI = getRootURI();
            this.launcherFuture = new CompletableFuture<>();
            this.initializeFuture = CompletableFuture.supplyAsync(() -> {
                        this.lspStreamProvider = serverDefinition.createConnectionProvider(initialProject);
                        initParams.setInitializationOptions(this.lspStreamProvider.getInitializationOptions(rootURI));

                        // Starting process...
                        updateStatus(ServerStatus.starting);
                        getLanguageServerLifecycleManager().onStatusChanged(this);
                        this.currentProcessId = null;
                        this.currentProcessCommandLines = null;
                        lspStreamProvider.start();

                        // As process can be stopped, we loose pid and command lines information
                        // when server is stopped, we store them here.
                        // to display them in the Language server explorer even if process is killed.
                        if (lspStreamProvider instanceof ProcessStreamConnectionProvider provider) {
                            this.currentProcessId = provider.getPid();
                            this.currentProcessCommandLines = provider.getCommands();
                        }

                        // Throws the CannotStartProcessException exception if process is not alive.
                        // This use case comes for instance when the start process command fails (not a valid start command)
                        lspStreamProvider.ensureIsAlive();
                        return null;
                    }).thenRun(() -> {
                        languageClient = serverDefinition.createLanguageClient(initialProject);
                        initParams.setProcessId(getParentProcessId());

                        if (rootURI != null) {
                            initParams.setRootUri(LSPIJUtils.toUriAsString(rootURI));
                            initParams.setRootPath(rootURI.getPath());
                        }

                        UnaryOperator<MessageConsumer> wrapper = consumer -> (message -> {
                            logMessage(message, consumer);
                            try {
                                // To avoid having some lock problem when message is written in the stream output
                                // (when there are a lot of messages to write it)
                                // we consume the message in async mode
                                CompletableFuture.runAsync(() -> consumer.consume(message))
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
                                currentConnectionProvider.handleMessage(message, this.languageServer, rootURI);
                            }
                        });
                        Launcher<LanguageServer> launcher = serverDefinition.createLauncherBuilder() //
                                .setLocalService(languageClient)//
                                .setRemoteInterface(serverDefinition.getServerInterface())//
                                .setInput(lspStreamProvider.getInputStream())//
                                .setOutput(lspStreamProvider.getOutputStream())//
                                .setExecutorService(listener)//
                                .wrapMessages(wrapper)//
                                .create();
                        this.languageServer = launcher.getRemoteProxy();
                        languageClient.connect(languageServer, this);
                        this.launcherFuture = launcher.startListening();
                    })
                    .thenCompose(unused -> initServer(rootURI))
                    .thenAccept(res -> {
                        serverError = null;
                        serverCapabilities = res.getCapabilities();
                        this.initiallySupportsWorkspaceFolders = supportsWorkspaceFolders(serverCapabilities);
                    }).thenRun(() -> this.languageServer.initialized(new InitializedParams())).thenRun(() -> {
                        initializeFuture.thenRunAsync(() -> {
                            for (VirtualFile fileToReconnect : filesToReconnect) {
                                connect(LSPIJUtils.toUri(fileToReconnect), fileToReconnect, null);
                            }
                        });

                        messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
                        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileListener);
                        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(fileListener));

                        fileOperationsManager = new FileOperationsManager(this);
                        fileOperationsManager.setServerCapabilities(serverCapabilities);

                        updateStatus(ServerStatus.started);
                        getLanguageServerLifecycleManager().onStatusChanged(this);
                    }).exceptionally(e -> {
                        if (e instanceof CompletionException) {
                            e = e.getCause();
                        }
                        if (e instanceof CannotStartProcessException) {
                            serverError = (CannotStartProcessException) e;
                        } else {
                            serverError = new CannotStartServerException("Error while starting language server '" + serverDefinition.getId() + "' (pid=" + getCurrentProcessId() + ")", e);
                        }
                        initializeFuture.completeExceptionally(serverError);
                        getLanguageServerLifecycleManager().onError(this, e);
                        stop(false);
                        return null;
                    });
        }
    }

    private CompletableFuture<InitializeResult> initServer(final VirtualFile rootURI) {
        initParams.setCapabilities(ClientCapabilitiesFactory
                .create(lspStreamProvider.getExperimentalFeaturesPOJO()));
        initParams.setClientInfo(getClientInfo());
        initParams.setTrace(this.lspStreamProvider.getTrace(rootURI));

        var folders = LSPIJUtils.toWorkspaceFolders(initialProject);
        initParams.setWorkspaceFolders(folders);

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

    private static boolean supportsWorkspaceFolders(ServerCapabilities serverCapabilities) {
        return serverCapabilities != null && serverCapabilities.getWorkspace() != null
                && serverCapabilities.getWorkspace().getWorkspaceFolders() != null
                && Boolean.TRUE.equals(serverCapabilities.getWorkspace().getWorkspaceFolders().getSupported());
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
                getLanguageServerLifecycleManager().onStatusChanged(this);
            }
        }
    }

    private void updateStatus(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
        if (languageClient != null) {
            languageClient.handleServerStatusChanged(serverStatus);
        }
    }

    private void startStopTimer() {
        timer = new Timer("Stop Language Server Timer"); //$NON-NLS-1$
        updateStatus(ServerStatus.stopping);
        getLanguageServerLifecycleManager().onStatusChanged(this);
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
        this.disposed = true;
        stop();
        stopDispatcher();
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

    public synchronized void stop() {
        final boolean alreadyStopping = this.stopping.getAndSet(true);
        stop(alreadyStopping);
    }

    public synchronized void stop(boolean alreadyStopping) {
        try {
            if (alreadyStopping) {
                return;
            }
            updateStatus(ServerStatus.stopping);
            getLanguageServerLifecycleManager().onStatusChanged(this);

            removeStopTimer(true);
            if (this.languageClient != null) {
                this.languageClient.dispose();
            }

            if (this.initializeFuture != null) {
                this.initializeFuture.cancel(true);
                this.initializeFuture = null;
            }

            this.serverCapabilities = null;
            this.dynamicRegistrations.clear();

            if (isDisposed()) {
                // When project is closing we shutdown everything in synch mode
                shutdownAll(languageServer, lspStreamProvider, launcherFuture);
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
                    getLanguageServerLifecycleManager().onStatusChanged(this);
                };
                CompletableFuture.runAsync(shutdownKillAndStopFutureAndProvider);
            }
        } finally {
            this.launcherFuture = null;
            this.lspStreamProvider = null;

            while (!this.connectedDocuments.isEmpty()) {
                disconnect(this.connectedDocuments.keySet().iterator().next(), false);
            }
            this.languageServer = null;
            this.languageClient = null;

            if (messageBusConnection != null) {
                messageBusConnection.disconnect();
            }
        }
    }

    private void shutdownAll(LanguageServer languageServerInstance, StreamConnectionProvider provider, Future<?> serverFuture) {
        if (languageServerInstance != null && provider != null && provider.isAlive()) {
            // The LSP language server instance and the process which starts the language server is alive. Process
            // - shutdown
            // - exit

            // shutdown the language server
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

        if (serverFuture != null) {
            serverFuture.cancel(true);
        }

        if (provider != null) {
            provider.stop();
        }
    }

    private void shutdownLanguageServerInstance(LanguageServer languageServerInstance) throws Exception {
        CompletableFuture<Object> shutdown = languageServerInstance.shutdown();
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
     * Connect the given file Uri to the language server and returns the language server instance when:
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
     * @param file     the file to connect to the language server
     * @param document the document of the file and null otherwise. In the null case, the document will be retrieved from the file
     *                 by using a blocking read action.
     * @return the completable future with the language server instance or null.
     */
    CompletableFuture<@Nullable LanguageServer> connect(@NotNull VirtualFile file,
                                                        @Nullable Document document) {
        if (file != null && file.exists()) {
            return connect(LSPIJUtils.toUri(file), file, document);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Connect the given file Uri to the language server and returns the language server instance when:
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
     * @param fileUri          the file Uri to connect to the language server
     * @param optionalFile     the file which matches the non-nullable file uri and null otherwise. In the null case, the file will be retrieved by the file Uri.
     * @param optionalDocument the document of the file and null otherwise. In the null case, the document will be retrieved from the file
     *                         by using a blocking read action.
     * @return the completable future with the language server instance or null.
     */
    private CompletableFuture<LanguageServer> connect(@NotNull URI fileUri,
                                                      @Nullable VirtualFile optionalFile,
                                                      @Nullable Document optionalDocument) {
        removeStopTimer(false);

        var ls = getLanguageServerWhenDidOpen(fileUri);
        if (ls != null) {
            return ls;
        }

        // start language server if needed
        start();

        if (this.initializeFuture == null) {
            // The "initialize" future is not initialized, return null as language server.
            return CompletableFuture.completedFuture(null);
        }

        if (optionalFile == null) {
            optionalFile = LSPIJUtils.findResourceFor(fileUri);
        }
        if (optionalFile == null) {
            // The file cannot be retrieved by the given uri, return null as language server.
            return CompletableFuture.completedFuture(null);
        }

        VirtualFile fileToConnect = optionalFile;
        return initializeFuture.thenComposeAsync(theVoid -> {
            // Here, the "initialize" future is initialized

            // Check if file is already opened (without synchronized block)
            var ls2 = getLanguageServerWhenDidOpen(fileUri);
            if (ls2 != null) {
                return ls2;
            }

            // Here the file is not already connected
            // To connect the file, we need the document instance to add LSP document listener to manage didOpen, didChange, etc.
            Document document = optionalDocument != null ? optionalDocument : LSPIJUtils.getDocument(fileToConnect);

            synchronized (connectedDocuments) {
                // Check again if file is already opened (within synchronized block)
                ls2 = getLanguageServerWhenDidOpen(fileUri);
                if (ls2 != null) {
                    return ls2;
                }

                DocumentContentSynchronizer synchronizer = createDocumentContentSynchronizer(fileUri, document);
                document.addDocumentListener(synchronizer);
                LSPVirtualFileData data = new LSPVirtualFileData(new LanguageServerItem(languageServer, this), fileToConnect, synchronizer);
                LanguageServerWrapper.this.connectedDocuments.put(fileUri, data);

                return getLanguageServerWhenDidOpen(synchronizer.didOpenFuture);
            }
        });
    }

    @Nullable
    private CompletableFuture<LanguageServer> getLanguageServerWhenDidOpen(@NotNull URI fileUri) {
        var existingData = connectedDocuments.get(fileUri);
        if (existingData != null) {
            // The file is already connected.
            // returns the language server instance when didOpen happened
            var didOpenFuture = existingData.getSynchronizer().didOpenFuture;
            return getLanguageServerWhenDidOpen(didOpenFuture);
        }
        return null;
    }

    private CompletableFuture<LanguageServer> getLanguageServerWhenDidOpen(CompletableFuture<Void> didOpenFuture) {
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
    private DocumentContentSynchronizer createDocumentContentSynchronizer(@NotNull URI fileUri,
                                                                          @NotNull Document document) {
        Either<TextDocumentSyncKind, TextDocumentSyncOptions> syncOptions = initializeFuture == null ? null
                : this.serverCapabilities.getTextDocumentSync();
        TextDocumentSyncKind syncKind = null;
        if (syncOptions != null) {
            if (syncOptions.isRight()) {
                syncKind = syncOptions.getRight().getChange();
            } else if (syncOptions.isLeft()) {
                syncKind = syncOptions.getLeft();
            }
        }
        return new DocumentContentSynchronizer(this, fileUri, document, syncKind);
    }

    public void disconnect(URI path, boolean stopIfNoOpenedFiles) {
        LSPVirtualFileData data = this.connectedDocuments.remove(path);
        if (data != null) {
            // Remove the listener from the old document stored in synchronizer
            DocumentContentSynchronizer synchronizer = data.getSynchronizer();
            synchronizer.getDocument().removeDocumentListener(synchronizer);
            synchronizer.documentClosed();
        }
        if (stopIfNoOpenedFiles && this.connectedDocuments.isEmpty()) {
            if (this.serverDefinition.getLastDocumentDisconnectedTimeout() != 0 && !ApplicationManager.getApplication().isUnitTestMode()) {
                removeStopTimer(true);
                startStopTimer();
            } else {
                stop();
            }
        }
    }

    /**
     * checks if the wrapper is already connected to the document at the given path
     *
     * @noreference test only
     */
    public boolean isConnectedTo(URI location) {
        return connectedDocuments.containsKey(location);
    }

    /**
     * Returns the LSP file data coming from this language server for the given file uri.
     *
     * @param fileUri the file Uri.
     * @return the LSP file data coming from this language server for the given file uri.
     */
    public @Nullable LSPVirtualFileData getLSPVirtualFileData(URI fileUri) {
        return connectedDocuments.get(fileUri);
    }

    /**
     * Returns all LSP files connected to this language server.
     *
     * @return all LSP files connected to this language server.
     */
    public Collection<LSPVirtualFileData> getConnectedFiles() {
        return Collections.unmodifiableCollection(connectedDocuments.values());
    }

    /**
     * Starts and returns the language server, regardless of if it is initialized.
     * If not in the UI Thread, will wait to return the initialized server.
     *
     * @deprecated use {@link #getInitializedServer()} instead.
     */
    @Deprecated
    @Nullable
    public LanguageServer getServer() {
        CompletableFuture<LanguageServer> languageServerFuture = getInitializedServer();
        if (ApplicationManager.getApplication().isDispatchThread()) { // UI Thread
            return this.languageServer;
        } else {
            return languageServerFuture.join();
        }
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
     */
    public void sendNotification(@NotNull Consumer<LanguageServer> fn) {
        // Enqueues a notification on the dispatch thread associated with the wrapped language server. This
        // ensures the interleaving of document updates and other requests in the UI is mirrored in the
        // order in which they get dispatched to the server
        getInitializedServer().thenAcceptAsync(fn, this.dispatcher);
    }

    /**
     * Warning: this is a long running operation
     *
     * @return the server capabilities, or null if initialization job didn't
     * complete
     */
    @Nullable
    public ServerCapabilities getServerCapabilities() {
        try {
            getInitializedServer().get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOGGER.warn("LanguageServer not initialized after 10s", e); //$NON-NLS-1$
        } catch (ExecutionException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
        } catch (CancellationException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
        } catch (InterruptedException e) {
            LOGGER.warn(e.getLocalizedMessage(), e);
            Thread.currentThread().interrupt();
        }

        return this.serverCapabilities;
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
     * @return The language ID that this wrapper is dealing with if defined in the
     * language mapping for the language server
     */
    @Nullable
    public String getLanguageId(@Nullable Language language) {
        while (language != null) {
            String languageId = serverDefinition.getLanguageId(language);
            if (languageId != null) {
                return languageId;
            }
            language = language.getBaseLanguage();
        }
        return null;
    }

    /**
     * @return The language ID that this wrapper is dealing with if defined in the
     * file type mapping for the language server
     */
    @Nullable
    public String getLanguageId(@Nullable FileType fileType) {
        if (fileType == null) {
            return null;
        }
        return serverDefinition.getLanguageId(fileType);
    }

    /**
     * @return The language ID that this wrapper is dealing with if defined in the
     * file type mapping for the language server
     */
    @Nullable
    public String getLanguageId(@NotNull String filename) {
        return serverDefinition.getLanguageId(filename);
    }

    public void registerCapability(RegistrationParams params) {
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
                        if (!newCommands.isEmpty()) {
                            addRegistration(reg, () -> unregisterCommands(newCommands));
                            registerCommands(newCommands);
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while getting 'workspace/executeCommand' capability", e);
                    }
                } else if (LSPRequestConstants.TEXT_DOCUMENT_FORMATTING.equals(reg.getMethod())) {
                    // register 'textDocument/formatting' capability
                    final Either<Boolean, DocumentFormattingOptions> documentFormattingProvider = serverCapabilities.getDocumentFormattingProvider();
                    if (documentFormattingProvider == null || documentFormattingProvider.isLeft()) {
                        serverCapabilities.setDocumentFormattingProvider(Boolean.TRUE);
                        addRegistration(reg, () -> serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider));
                    } else {
                        serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider.getRight());
                        addRegistration(reg, () -> serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider));
                    }
                } else if (LSPRequestConstants.TEXT_DOCUMENT_RANGE_FORMATTING.equals(reg.getMethod())) {
                    // register 'textDocument/rangeFormatting' capability
                    final Either<Boolean, DocumentRangeFormattingOptions> documentRangeFormattingProvider = serverCapabilities.getDocumentRangeFormattingProvider();
                    if (documentRangeFormattingProvider == null || documentRangeFormattingProvider.isLeft()) {
                        serverCapabilities.setDocumentRangeFormattingProvider(Boolean.TRUE);
                        addRegistration(reg, () -> serverCapabilities.setDocumentRangeFormattingProvider(documentRangeFormattingProvider));
                    } else {
                        serverCapabilities.setDocumentRangeFormattingProvider(documentRangeFormattingProvider.getRight());
                        addRegistration(reg, () -> serverCapabilities.setDocumentRangeFormattingProvider(documentRangeFormattingProvider));
                    }
                } else if (LSPRequestConstants.TEXT_DOCUMENT_CODE_ACTION.equals(reg.getMethod())) {
                    try {
                        // Get old 'textDocument/codeAction' capability
                        final Either<Boolean, CodeActionOptions> beforeRegistration = serverCapabilities.getCodeActionProvider();

                        // Register new 'textDocument/codeAction' capability
                        CodeActionRegistrationOptions options = JSONUtils.getLsp4jGson()
                                .fromJson((JsonObject) reg.getRegisterOptions(),
                                        CodeActionRegistrationOptions.class);
                        CodeActionOptions codeActionOptions = new CodeActionOptions();
                        codeActionOptions.setCodeActionKinds(options.getCodeActionKinds());
                        codeActionOptions.setResolveProvider(options.getResolveProvider());
                        // TODO: manage CodeActionRegistrationOptions#getDocumentSelector()
                        serverCapabilities.setCodeActionProvider(Either.forRight(codeActionOptions));

                        // Add registration handler to:
                        // - unregister the new 'textDocument/codeAction' capability
                        // - register the old 'textDocument/codeAction' capability
                        addRegistration(reg, () -> serverCapabilities.setCodeActionProvider(beforeRegistration));
                    } catch (Exception e) {
                        LOGGER.error("Error while getting 'textDocument/codeAction' capability", e);
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

    synchronized void registerCommands(List<String> newCommands) {
        ServerCapabilities caps = this.getServerCapabilities();
        if (caps != null) {
            ExecuteCommandOptions commandProvider = caps.getExecuteCommandProvider();
            if (commandProvider == null) {
                commandProvider = new ExecuteCommandOptions(new ArrayList<>());
                caps.setExecuteCommandProvider(commandProvider);
            }
            List<String> existingCommands = commandProvider.getCommands();
            for (String newCmd : newCommands) {
                assert !existingCommands.contains(newCmd) : "Command already registered '" + newCmd + "'"; //$NON-NLS-1$ //$NON-NLS-2$
                existingCommands.add(newCmd);
            }
        } else {
            throw new IllegalStateException("Dynamic command registration failed! Server not yet initialized?"); //$NON-NLS-1$
        }
    }

    public void unregisterCapability(UnregistrationParams params) {
        params.getUnregisterations().forEach(reg -> {
            String id = reg.getId();
            Runnable unregistrator;
            synchronized (dynamicRegistrations) {
                unregistrator = dynamicRegistrations.get(id);
                dynamicRegistrations.remove(id);
            }
            if (unregistrator != null) {
                unregistrator.run();
            }
        });
    }

    void unregisterCommands(List<String> cmds) {
        ServerCapabilities caps = this.getServerCapabilities();
        if (caps != null) {
            ExecuteCommandOptions commandProvider = caps.getExecuteCommandProvider();
            if (commandProvider != null) {
                List<String> existingCommands = commandProvider.getCommands();
                existingCommands.removeAll(cmds);
            }
        }
    }

    int getVersion(VirtualFile file) {
        if (file != null) {
            LSPVirtualFileData data = connectedDocuments.get(LSPIJUtils.toUri(file));
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
        if (this.isConnectedTo(LSPIJUtils.toUri(file))) {
            return true;
        }
        if (this.connectedDocuments.isEmpty()) {
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
     * Returns the parent process id (process id of Intellij).
     *
     * @return the parent process id (process id of Intellij).
     */
    private static int getParentProcessId() {
        return (int) ProcessHandle.current().pid();
    }

    // ------------------ Current Process information.

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

    // ------------------ Server status information .

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
        return fileOperationsManager.canWillRenameFiles(LSPIJUtils.toUri(file), file.isDirectory());
    }

    /**
     * Returns true if the given file support the 'workspace/didRenameFiles' and false otherwise.
     *
     * @param file the file.
     * @return true if the given file support the 'workspace/didRenameFiles' and false otherwise.
     */
    public boolean isDidRenameFilesSupported(PsiFile file) {
        if (fileOperationsManager == null) {
            return false;
        }
        return fileOperationsManager.canDidRenameFiles(LSPIJUtils.toUri(file), file.isDirectory());
    }

    /**
     * Returns true if the given character is defined as "completion trigger" in the server capability of the language server and false otherwise.
     *
     * @param charTyped the current typed character.
     * @return true if the given character is defined as "completion trigger" in the server capability of the language server and false otherwise.
     */
    public boolean isCompletionTriggerCharactersSupported(String charTyped) {
        if (serverCapabilities == null) {
            return false;
        }
        var triggerCharacters = serverCapabilities.getCompletionProvider() != null ? serverCapabilities.getCompletionProvider().getTriggerCharacters() : null;
        if (triggerCharacters == null || triggerCharacters.isEmpty()) {
            return false;
        }
        return triggerCharacters.contains(charTyped);
    }

    /**
     * Returns true if the given character is defined as "signature trigger" in the server capability of the language server and false otherwise.
     *
     * @param charTyped the current typed character.
     * @return true if the given character is defined as "signature trigger" in the server capability of the language server and false otherwise.
     */
    public boolean isSignatureTriggerCharactersSupported(String charTyped) {
        if (serverCapabilities == null) {
            return false;
        }

        var triggerCharacters = serverCapabilities.getSignatureHelpProvider() != null ? serverCapabilities.getSignatureHelpProvider().getTriggerCharacters() : null;
        if (triggerCharacters == null || triggerCharacters.isEmpty()) {
            return false;
        }
        return triggerCharacters.contains(charTyped);
    }
}