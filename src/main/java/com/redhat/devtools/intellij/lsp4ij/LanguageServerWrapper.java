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
package com.redhat.devtools.intellij.lsp4ij;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter;
import com.intellij.util.messages.MessageBusConnection;
import com.redhat.devtools.intellij.lsp4ij.client.LanguageClientImpl;
import com.redhat.devtools.intellij.lsp4ij.internal.SupportedFeatures;
import com.redhat.devtools.intellij.lsp4ij.lifecycle.LanguageServerLifecycleManager;
import com.redhat.devtools.intellij.lsp4ij.lifecycle.NullLanguageServerLifecycleManager;
import com.redhat.devtools.intellij.lsp4ij.server.*;
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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Language server wrapper.
 */
public class LanguageServerWrapper implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageServerWrapper.class);//$NON-NLS-1$
    private static final String CLIENT_NAME = "IntelliJ";
    private static final int MAX_NUMBER_OF_RESTART_ATTEMPTS = 20; // TODO move this max value in settings

    class Listener implements FileEditorManagerListener, VirtualFileListener {

        @Override
        public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
            if (initialProject != null && !Objects.equals(source.getProject(), initialProject)) {
                // The file has been closed from another project,don't send textDocument/didClose
                return;
            }
            // Manage textDocument/didClose
            URI uri = LSPIJUtils.toUri(file);
            if (uri != null) {
                try {
                    // Disconnect the given file from the current language servers
                    disconnect(uri, !isDisposed());
                } catch (Exception e) {
                    LOGGER.warn("Error while disconnecting the file '" + uri + "' from all language servers", e);
                }
            }
        }

        @Override
        public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
            if (event.getPropertyName().equals(VirtualFile.PROP_NAME) && event.getOldValue() instanceof String) {
                // A file (Test1.java) has been renamed (to Test2.java) by using Refactor / Rename from IJ

                // 1. Send a textDocument/didClose for the renamed file (Test1.java)
                URI oldFileUri = didClose(event.getFile().getParent(), (String) event.getOldValue());
                URI newFileUri = LSPIJUtils.toUri(event.getFile());
                // 2. Send a workspace/didChangeWatchedFiles
                didChangeWatchedFiles(fe(oldFileUri, FileChangeType.Deleted),
                        fe(newFileUri, FileChangeType.Created));
            }
        }

        @Override
        public void contentsChanged(@NotNull VirtualFileEvent event) {
            URI uri = LSPIJUtils.toUri(event.getFile());
            if (uri != null) {
                LSPVirtualFileData documentListener = connectedDocuments.get(uri);
                if (documentListener != null) {
                    // 1. Send a textDocument/didSave for the saved file
                    documentListener.getSynchronizer().documentSaved();
                }
                // 2. Send a workspace/didChangeWatchedFiles
                didChangeWatchedFiles(fe(uri, FileChangeType.Changed));
            }
        }

        @Override
        public void fileCreated(@NotNull VirtualFileEvent event) {
            URI uri = LSPIJUtils.toUri(event.getFile());
            if (uri != null) {
                // 2. Send a workspace/didChangeWatchedFiles
                didChangeWatchedFiles(fe(uri, FileChangeType.Created));
            }
        }

        @Override
        public void fileDeleted(@NotNull VirtualFileEvent event) {
            URI uri = LSPIJUtils.toUri(event.getFile());
            if (uri != null) {
                // 2. Send a workspace/didChangeWatchedFiles
                didChangeWatchedFiles(fe(uri, FileChangeType.Deleted));
            }
        }

        @Override
        public void fileMoved(@NotNull VirtualFileMoveEvent event) {
            // A file (foo.Test1.java) has been moved (to bar1.Test1.java)

            // 1. Send a textDocument/didClose for the moved file (foo.Test1.java)
            URI oldFileUri = didClose(event.getOldParent(), event.getFileName());
            URI newFileUri = LSPIJUtils.toUri(event.getFile());
            // 2. Send a workspace/didChangeWatchedFiles
            didChangeWatchedFiles(fe(oldFileUri, FileChangeType.Deleted),
                    fe(newFileUri, FileChangeType.Created));
        }

        private FileEvent fe(URI uri, FileChangeType type) {
            return new FileEvent(uri.toASCIIString(), type);
        }

        private @NotNull URI didClose(VirtualFile virtualParentFile, String fileName) {
            File parent = VfsUtilCore.virtualToIoFile(virtualParentFile);
            URI uri = LSPIJUtils.toUri(new File(parent, fileName));
            if (isConnectedTo(uri)) {
                disconnect(uri, false);
            }
            return uri;
        }

        private void didChangeWatchedFiles(FileEvent... changes) {
            LanguageServerWrapper.this.sendNotification(ls -> {
                DidChangeWatchedFilesParams params = new DidChangeWatchedFilesParams(Arrays.asList(changes));
                ls.getWorkspaceService()
                        .didChangeWatchedFiles(params);
            });
        }
    }

    private Listener fileBufferListener = new Listener();
    private MessageBusConnection messageBusConnection;

    @NotNull
    public final LanguageServersRegistry.LanguageServerDefinition serverDefinition;
    @Nullable
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
    private @NotNull
    Map<String, Runnable> dynamicRegistrations = new HashMap<>();
    private boolean initiallySupportsWorkspaceFolders = false;

    /* Backwards compatible constructor */
    public LanguageServerWrapper(@NotNull Project project, @NotNull LanguageServersRegistry.LanguageServerDefinition serverDefinition) {
        this(project, serverDefinition, null);
    }

    public LanguageServerWrapper(@NotNull LanguageServersRegistry.LanguageServerDefinition serverDefinition, @Nullable URI initialPath) {
        this(null, serverDefinition, initialPath);
    }

    /**
     * Unified private constructor to set sensible defaults in all cases
     */
    private LanguageServerWrapper(@Nullable Project project, @NotNull LanguageServersRegistry.LanguageServerDefinition serverDefinition,
                                  @Nullable URI initialPath) {
        this.initialProject = project;
        this.initialPath = initialPath;
        this.serverDefinition = serverDefinition;
        this.connectedDocuments = new HashMap<>();
        String projectName = sanitize((project != null && project.getName() != null && !serverDefinition.isSingleton) ? ("@" + project.getName()) : "");  //$NON-NLS-1$//$NON-NLS-2$
        String dispatcherThreadNameFormat ="LS-" + serverDefinition.id + projectName + "#dispatcher"; //$NON-NLS-1$ //$NON-NLS-2$
        this.dispatcher = Executors
                .newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat(dispatcherThreadNameFormat).build());

        // Executor service passed through to the LSP4j layer when we attempt to start the LS. It will be used
        // to create a listener that sits on the input stream and processes inbound messages (responses, or server-initiated
        // requests).
        String listenerThreadNameFormat ="LS-" + serverDefinition.id + projectName + "#listener-%d"; //$NON-NLS-1$ //$NON-NLS-2$
        this.listener = Executors
                .newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat(listenerThreadNameFormat).build());
        udateStatus(ServerStatus.none);
        if (project != null) {
            // When project is disposed, we dispose the language server
            // But the language server should be disposed before because when project is closing
            // We do that to be sure that language server is disposed.
            Disposer.register(project, this);
        }
    }

    /**
     * Removes '%' from the given name
     */
    private static String sanitize(String name) {
        return name.replace("%","");
    }

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
        this.serverDefinition.setEnabled(enabled);
    }

    public boolean isEnabled() {
        return serverDefinition.isEnabled();
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
        final var filesToReconnect = new ArrayList<URI>();
        if (this.languageServer != null) {
            if (isActive()) {
                return;
            } else {
                for (Map.Entry<URI, LSPVirtualFileData> entry : this.connectedDocuments.entrySet()) {
                    filesToReconnect.add(entry.getKey());
                }
                stop();
            }
        }

        if (this.initializeFuture == null) {
            final URI rootURI = getRootURI();
            this.launcherFuture = new CompletableFuture<>();
            this.initializeFuture = CompletableFuture.supplyAsync(() -> {
                        this.lspStreamProvider = serverDefinition.createConnectionProvider(initialProject);
                        initParams.setInitializationOptions(this.lspStreamProvider.getInitializationOptions(rootURI));

                        // Starting process...
                        udateStatus(ServerStatus.starting);
                        getLanguageServerLifecycleManager().onStatusChanged(this);
                        this.currentProcessId = null;
                        this.currentProcessCommandLines = null;
                        lspStreamProvider.start();

                        // As process can be stopped, we loose pid and command lines information
                        // when server is stopped, we store them here.
                        // to display them in the Language server explorer even if process is killed.
                        if (lspStreamProvider instanceof ProcessStreamConnectionProvider) {
                            ProcessStreamConnectionProvider provider = (ProcessStreamConnectionProvider) lspStreamProvider;
                            this.currentProcessId = provider.getPid();
                            this.currentProcessCommandLines = provider.getCommands();
                        }

                        // Throws the CannotStartProcessException exception if process is not alive.
                        // This usecase comes for instance when the start process command fails (not a valid start command)
                        lspStreamProvider.ensureIsAlive();
                        return null;
                    }).thenRun(() -> {
                        languageClient = serverDefinition.createLanguageClient(initialProject);
                        initParams.setProcessId(getParentProcessId());

                        if (rootURI != null) {
                            initParams.setRootUri(rootURI.toString());
                            initParams.setRootPath(rootURI.getPath());
                        }

                        UnaryOperator<MessageConsumer> wrapper = consumer -> (message -> {
                            logMessage(message, consumer);
                            try {
                                // To avoid having some lock problem when message is written in the stream output
                                // (when there are a lot of messages to write it)
                                // we consume the message in async mode
                                CompletableFuture.runAsync(() -> consumer.consume(message));
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
                    }).thenRun(() -> {
                        this.languageServer.initialized(new InitializedParams());
                    }).thenRun(() -> {
                        final List<URI> toReconnect = filesToReconnect;
                        initializeFuture.thenRunAsync(() -> {
                            for (URI fileToReconnect : toReconnect) {
                                try {
                                    connect(fileToReconnect);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });

                        messageBusConnection = ApplicationManager.getApplication().getMessageBus().connect();
                        messageBusConnection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileBufferListener);
                        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, new BulkVirtualFileListenerAdapter(fileBufferListener));

                        udateStatus(ServerStatus.started);
                        getLanguageServerLifecycleManager().onStatusChanged(this);
                    }).exceptionally(e -> {
                        if (e instanceof CompletionException) {
                            e = e.getCause();
                        }
                        if (e instanceof CannotStartProcessException) {
                            serverError = (CannotStartProcessException) e;
                        } else {
                            serverError = new CannotStartServerException("Error while starting language server '" + serverDefinition.id + "' (pid=" + getCurrentProcessId() + ")", e);
                        }
                        initializeFuture.completeExceptionally(serverError);
                        getLanguageServerLifecycleManager().onError(this, e);
                        stop(false);
                        return null;
                    });
        }
    }

    private CompletableFuture<InitializeResult> initServer(final URI rootURI) {

        final var workspaceClientCapabilities = SupportedFeatures.getWorkspaceClientCapabilities();
        final var textDocumentClientCapabilities = SupportedFeatures.getTextDocumentClientCapabilities();

        WindowClientCapabilities windowClientCapabilities = SupportedFeatures.getWindowClientCapabilities();
        initParams.setCapabilities(new ClientCapabilities(
                workspaceClientCapabilities,
                textDocumentClientCapabilities,
                windowClientCapabilities,
                lspStreamProvider.getExperimentalFeaturesPOJO()));
        initParams.setClientInfo(getClientInfo());
        initParams.setTrace(this.lspStreamProvider.getTrace(rootURI));

        if (initialProject != null) {
            var folders = Arrays.asList(LSPIJUtils.toWorkspaceFolder(initialProject));
            initParams.setWorkspaceFolders(folders);
        }

        // no then...Async future here as we want this chain of operation to be sequential and "atomic"-ish
        return languageServer.initialize(initParams);
    }

    private ClientInfo getClientInfo() {
        ApplicationInfo applicationInfo = ApplicationInfo.getInstance();
        String versionName = applicationInfo.getVersionName();
        String buildNumber = applicationInfo.getBuild().asString();

        String intellijVersion = versionName + " (build " + buildNumber + ")";
        return new ClientInfo(CLIENT_NAME, intellijVersion);
    }

    @Nullable
    private URI getRootURI() {
        final Project project = this.initialProject;
        if (project != null && !project.isDisposed()) {
            return LSPIJUtils.toUri(project);
        }

        final URI path = this.initialPath;
        if (path != null) {
            File projectDirectory = new File(initialPath);
            if (projectDirectory.isFile()) {
                projectDirectory = projectDirectory.getParentFile();
            }
            return LSPIJUtils.toUri(projectDirectory);
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
                udateStatus(ServerStatus.started);
                getLanguageServerLifecycleManager().onStatusChanged(this);
            }
        }
    }

    private void udateStatus(ServerStatus serverStatus) {
        this.serverStatus = serverStatus;
    }

    private void startStopTimer() {
        timer = new Timer("Stop Language Server Timer"); //$NON-NLS-1$
        udateStatus(ServerStatus.stopping);
        getLanguageServerLifecycleManager().onStatusChanged(this);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    stop();
                } catch (Throwable t) {
                    //Need to catch time task exceptions, or it will cancel the timer
                    LOGGER.error("Failed to stop language server "+LanguageServerWrapper.this.serverDefinition.id, t);
                }
            }
        }, TimeUnit.SECONDS.toMillis(this.serverDefinition.lastDocumentDisconnectedTimeout));
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
            udateStatus(ServerStatus.stopping);
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
                    udateStatus(ServerStatus.stopped);
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
            String message = "Timeout error while shutdown the language server '" + serverDefinition.id + "'";
            LOGGER.warn(message, ex);
            throw new Exception(message, ex);
        } catch (Exception ex) {
            String message = "Error while shutdown the language server '" + serverDefinition.id + "'";
            LOGGER.warn(message, ex);
            throw new Exception(message, ex);
        }
    }

    private void exitLanguageServerInstance(LanguageServer languageServerInstance) throws Exception {
        try {
            languageServerInstance.exit();
        } catch (Exception ex) {
            String message = "Error while exit the language server '" + serverDefinition.id + "'";
            LOGGER.error(message, ex);
            throw new Exception(message, ex);
        }
    }

    /**
     * Connect the given file to the language server.
     *
     * @param file the file to connect to the language server
     * @return null if not connection has happened, a future tracking the connection state otherwise
     * @throws IOException
     */
    public CompletableFuture<@Nullable LanguageServer> connect(VirtualFile file) throws IOException {
        if (file != null && file.exists()) {
            return connect(LSPIJUtils.toUri(file));
        }
        return CompletableFuture.completedFuture(null);
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

        return serverDefinition.isSingleton;
    }

    /**
     * To make public when we support non IFiles
     *
     * @return null if not connection has happened, a future that completes when file is initialized otherwise
     * @noreference internal so far
     */
    private CompletableFuture<LanguageServer> connect(@NotNull URI fileUri) throws IOException {
        removeStopTimer(false);

        if (this.connectedDocuments.containsKey(fileUri)) {
            return CompletableFuture.completedFuture(languageServer);
        }
        start();
        if (this.initializeFuture == null) {
            return CompletableFuture.completedFuture(null);
        }
        VirtualFile file = LSPIJUtils.findResourceFor(fileUri);
        if (file == null) {
            return CompletableFuture.completedFuture(null);
        }
        return initializeFuture.thenComposeAsync(theVoid -> {
            synchronized (connectedDocuments) {
                if (this.connectedDocuments.containsKey(fileUri)) {
                    return CompletableFuture.completedFuture(null);
                }
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

                Document document = LSPIJUtils.getDocument(file);
                DocumentContentSynchronizer synchronizer = new DocumentContentSynchronizer(this, fileUri, document, syncKind);
                document.addDocumentListener(synchronizer);

                LSPVirtualFileData data = new LSPVirtualFileData(this, file, synchronizer);
                LanguageServerWrapper.this.connectedDocuments.put(fileUri, data);

                return synchronizer.didOpenFuture;
            }
        }).thenApply(theVoid -> languageServer);
    }

    private void disconnect(URI path) {
        disconnect(path, true);
    }

    private void disconnect(URI path, boolean stopIfNoOpenedFiles) {
        LSPVirtualFileData data = this.connectedDocuments.remove(path);
        if (data != null) {
            // Remove the listener from the old document stored in synchronizer
            DocumentContentSynchronizer synchronizer = data.getSynchronizer();
            synchronizer.getDocument().removeDocumentListener(synchronizer);
            synchronizer.documentClosed();
        }
        if (stopIfNoOpenedFiles && this.connectedDocuments.isEmpty()) {
            if (this.serverDefinition.lastDocumentDisconnectedTimeout != 0 && !ApplicationManager.getApplication().isUnitTestMode()) {
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

    public @Nullable LSPVirtualFileData getLSPVirtualFileData(URI fileUri) {
        return connectedDocuments.get(fileUri);
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
        CompletableFuture<LanguageServer> languagServerFuture = getInitializedServer();
        if (ApplicationManager.getApplication().isDispatchThread()) { // UI Thread
            return this.languageServer;
        } else {
            return languagServerFuture.join();
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
            /*if (ApplicationManager.getApplication().isDispatchThread()) { // UI Thread
                try {
                    ProgressManager.getInstance().run(new Task.WithResult<Void, Exception>(null, Messages.initializeLanguageServer_job, false) {
                        @Override
                        protected Void compute(@NotNull ProgressIndicator indicator) throws Exception {
                            indicator.setText("Waiting for server " + LanguageServerWrapper.this.serverDefinition.id + " to be started");
                            initializeFuture.join();
                            return null;
                        }
                    });
                } catch (Exception e) {
                    LOGGER.error(e.getLocalizedMessage(), e);
                }
            }*/
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
     * @return The language ID that this wrapper is dealing with if defined in the
     * content type mapping for the language server
     */
    @Nullable
    public String getLanguageId(Language language) {
        while (language != null) {
            String languageId = serverDefinition.languageIdMappings.get(language);
            if (languageId != null) {
                return languageId;
            }
            language = language.getBaseLanguage();
        }
        return null;
    }

    public void registerCapability(RegistrationParams params) {
        initializeFuture.thenRun(() -> {
            params.getRegistrations().forEach(reg -> {
                if ("workspace/didChangeWorkspaceFolders".equals(reg.getMethod())) { //$NON-NLS-1$
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
                } else if ("workspace/executeCommand".equals(reg.getMethod())) { //$NON-NLS-1$
                    Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
                    ExecuteCommandOptions executeCommandOptions = gson.fromJson((JsonObject) reg.getRegisterOptions(),
                            ExecuteCommandOptions.class);
                    List<String> newCommands = executeCommandOptions.getCommands();
                    if (!newCommands.isEmpty()) {
                        addRegistration(reg, () -> unregisterCommands(newCommands));
                        registerCommands(newCommands);
                    }
                } else if ("textDocument/formatting".equals(reg.getMethod())) { //$NON-NLS-1$
                    final Either<Boolean, DocumentFormattingOptions> documentFormattingProvider = serverCapabilities.getDocumentFormattingProvider();
                    if (documentFormattingProvider == null || documentFormattingProvider.isLeft()) {
                        serverCapabilities.setDocumentFormattingProvider(Boolean.TRUE);
                        addRegistration(reg, () -> serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider));
                    } else {
                        serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider.getRight());
                        addRegistration(reg, () -> serverCapabilities.setDocumentFormattingProvider(documentFormattingProvider));
                    }
                } else if ("textDocument/rangeFormatting".equals(reg.getMethod())) { //$NON-NLS-1$
                    final Either<Boolean, DocumentRangeFormattingOptions> documentRangeFormattingProvider = serverCapabilities.getDocumentRangeFormattingProvider();
                    if (documentRangeFormattingProvider == null || documentRangeFormattingProvider.isLeft()) {
                        serverCapabilities.setDocumentRangeFormattingProvider(Boolean.TRUE);
                        addRegistration(reg, () -> serverCapabilities.setDocumentRangeFormattingProvider(documentRangeFormattingProvider));
                    } else {
                        serverCapabilities.setDocumentRangeFormattingProvider(documentRangeFormattingProvider.getRight());
                        addRegistration(reg, () -> serverCapabilities.setDocumentRangeFormattingProvider(documentRangeFormattingProvider));
                    }
                } else if ("textDocument/codeAction".equals(reg.getMethod())) { //$NON-NLS-1$
                    final Either<Boolean, CodeActionOptions> beforeRegistration = serverCapabilities.getCodeActionProvider();
                    serverCapabilities.setCodeActionProvider(Boolean.TRUE);
                    addRegistration(reg, () -> serverCapabilities.setCodeActionProvider(beforeRegistration));
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
            URI uri = LSPIJUtils.toUri(file);
            if (uri != null) {
                LSPVirtualFileData data = connectedDocuments.get(LSPIJUtils.toUri(file));
                if (data != null) {
                    var synchronizer = data.getSynchronizer();
                    if (synchronizer != null) {
                        return synchronizer.getVersion();
                    }
                }
            }
        }
        return -1;
    }

    public boolean canOperate(@NotNull VirtualFile file) {
        if (this.isConnectedTo(LSPIJUtils.toUri(file))) {
            return true;
        }
        if (this.initialProject == null && this.connectedDocuments.isEmpty()) {
            return true;
        }
        if (file != null && file.exists()) {
            return true;
        }
        return serverDefinition.isSingleton;
    }

    private LanguageServerLifecycleManager getLanguageServerLifecycleManager() {
        Project project = initialProject;
        if (project == null || project.isDisposed()) {
            return NullLanguageServerLifecycleManager.INSTANCE;
        }
        return LanguageServerLifecycleManager.getInstance(project);
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
}