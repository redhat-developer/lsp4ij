/*******************************************************************************
 * Copyright (c) 2026 Contributors to the LSP4IJ project.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.openapi.Disposable;
import com.redhat.devtools.lsp4ij.JSONUtils;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Workspace diagnostic pull loops for one language server connection.
 * Provider state and report callbacks are serialized on the owned executor.
 * Successful pulls are reissued; streamed reports retain their provider's
 * order.
 */
@ApiStatus.Internal
public final class WorkspaceDiagnosticManager implements Disposable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceDiagnosticManager.class);
    private static final long RETRIGGER_DELAY_MS = 1000;

    private final WorkspaceService server;
    private final ScheduledExecutorService executor;
    private final Function<String, CompletableFuture<Boolean>> matches;
    private final BiPredicate<String, WorkspaceDocumentDiagnosticReport> reports;
    private final Consumer<String> clearProvider;
    private final BiConsumer<String, String> clearFile;
    private final Map<String, Provider> providers = new HashMap<>();
    private volatile boolean disposed;

    /**
     * Creates a manager for a server's workspace diagnostic pulls.
     * Callbacks run on the executor and must return promptly.
     *
     * @param server        the service used to request workspace diagnostics
     * @param executor      the scheduled executor that serializes tasks, owned
     *                      and shut down by this manager
     * @param matches       the URI matcher returning a non-null future with
     *                      {@code true} for files that accept workspace reports
     * @param reports       the provider/report predicate returning {@code true}
     *                      when a result ID may be retained; a {@code null}
     *                      identifier denotes the default provider
     * @param clearProvider the callback clearing a removed provider's
     *                      diagnostics, accepting a {@code null} identifier
     *                      for the default provider
     * @param clearFile     the provider/URI callback clearing diagnostics
     *                      for a closed file that no longer matches; a
     *                      {@code null} identifier denotes the default provider
     */
    public WorkspaceDiagnosticManager(WorkspaceService server, ScheduledExecutorService executor,
                                      Function<String, CompletableFuture<Boolean>> matches,
                                      BiPredicate<String, WorkspaceDocumentDiagnosticReport> reports,
                                      Consumer<String> clearProvider,
                                      BiConsumer<String, String> clearFile) {
        this.server = server;
        this.executor = executor;
        this.matches = matches;
        this.reports = reports;
        this.clearProvider = clearProvider;
        this.clearFile = clearFile;
    }

    /**
     * Reconciles workspace diagnostic pulls with the supplied providers.
     * Queues cancellation and cleanup for removed providers and starts pulls
     * for newly added providers.
     *
     * @param identifiers the active provider identifiers; a {@code null} entry
     *                    denotes the default provider
     */
    public void updateProviders(Set<String> identifiers) {
        var copy = new HashSet<>(identifiers); // null denotes the default diagnostic provider
        execute(() -> {
            var iterator = providers.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (!copy.contains(entry.getKey())) {
                    cancel(entry.getValue());
                    iterator.remove();
                    clearProvider.accept(entry.getKey());
                }
            }
            for (String identifier : copy) {
                if (!providers.containsKey(identifier)) {
                    var provider = new Provider(identifier);
                    providers.put(identifier, provider);
                    pull(provider);
                }
            }
        });
    }

    /**
     * Queues replacement pulls for all active diagnostic providers.
     * Pending pulls and matches are cancelled while accepted result IDs are
     * retained.
     */
    public void refresh() {
        execute(() -> {
            for (var provider : providers.values()) {
                cancel(provider);
                pull(provider);
            }
        });
    }

    /**
     * Queues a partial diagnostic report for the pull matching its token.
     * Notifications without a matching active token are ignored.
     *
     * @param params the progress notification containing the token and report
     */
    public void notifyProgress(ProgressParams params) {
        if (params.getToken() == null || params.getValue() == null || !params.getValue().isRight()) {
            return;
        }
        execute(() -> {
            for (var provider : providers.values()) {
                if (!provider.responseComplete && params.getToken().isLeft()
                        && Objects.equals(provider.token, params.getToken().getLeft())) {
                    var gson = JSONUtils.getLsp4jGson();
                    var report = gson.fromJson(gson.toJsonTree(params.getValue().getRight()), WorkspaceDiagnosticReport.class);
                    accept(provider, report);
                    break;
                }
            }
        });
    }

    private void pull(Provider provider) {
        if (disposed) {
            return;
        }
        provider.scheduled = null;
        String token = UUID.randomUUID().toString();
        provider.token = token;
        var params = new WorkspaceDiagnosticParams(provider.resultIds.entrySet().stream()
                .map(entry -> new PreviousResultId(entry.getKey(), entry.getValue())).toList());
        params.setIdentifier(provider.identifier);
        params.setPartialResultToken(token);
        try {
            provider.request = server.diagnostic(params);
            provider.request.whenComplete((report, error) -> execute(() -> {
                if (!Objects.equals(provider.token, token)) {
                    return; // a refreshed/unregistered request completed late
                }
                provider.request = null;
                if (error == null) {
                    provider.responseComplete = true;
                    accept(provider, report);
                } else {
                    cancel(provider);
                    if (shouldRetrigger(error)) {
                        schedule(provider);
                    }
                }
            }));
        } catch (RuntimeException error) {
            provider.token = null;
            provider.request = null;
            LOGGER.warn("Unable to request workspace diagnostics", error);
        }
    }

    private void accept(Provider provider, WorkspaceDiagnosticReport report) {
        if (report != null && report.getItems() != null) {
            provider.pendingReports.addAll(report.getItems());
        }
        applyNextReport(provider);
    }

    /**
     * Processes a provider's queued reports without blocking on matching.
     * Preserves their arrival order.
     *
     * @param provider the provider whose pending reports are processed
     */
    private void applyNextReport(Provider provider) {
        if (disposed || provider.matching != null) {
            return;
        }
        while (!provider.pendingReports.isEmpty()) {
            var item = provider.pendingReports.removeFirst();
            String uri = item.isLeft() ? item.getLeft().getUri() : item.getRight().getUri();
            if (item.isRight() && !provider.resultIds.containsKey(uri)) {
                continue; // no cached full report to retain
            }
            CompletableFuture<Boolean> matching;
            try {
                matching = Objects.requireNonNull(matches.apply(uri));
            } catch (RuntimeException error) {
                LOGGER.warn("Unable to match workspace diagnostic file {}", uri, error);
                provider.resultIds.remove(uri);
                continue;
            }
            provider.matching = matching;
            String token = provider.token;
            matching.whenComplete((matched, error) -> execute(() -> {
                if (!Objects.equals(provider.token, token) || provider.matching != matching) {
                    return; // refresh/unregistration invalidated this match, even if cancellation was refused
                }
                provider.matching = null;
                boolean accepted = false;
                if (error != null) {
                    LOGGER.warn("Unable to match workspace diagnostic file {}", uri, error);
                } else if (Boolean.TRUE.equals(matched)) {
                    try {
                        accepted = reports.test(provider.identifier, item);
                    } catch (RuntimeException failure) {
                        LOGGER.warn("Unable to apply workspace diagnostics for {}", uri, failure);
                    }
                } else if (Boolean.FALSE.equals(matched)) {
                    try {
                        clearFile.accept(provider.identifier, uri);
                    } catch (RuntimeException failure) {
                        LOGGER.warn("Unable to clear workspace diagnostics for {}", uri, failure);
                    }
                }
                String resultId = item.isLeft() ? item.getLeft().getResultId() : item.getRight().getResultId();
                if (accepted && resultId != null) {
                    provider.resultIds.put(uri, resultId);
                } else {
                    provider.resultIds.remove(uri);
                }
                applyNextReport(provider);
            }));
            return;
        }
        if (provider.responseComplete) {
            provider.responseComplete = false;
            provider.token = null;
            schedule(provider);
        }
    }

    private boolean shouldRetrigger(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        if (error instanceof ResponseErrorException response
                && response.getResponseError().getCode() == ResponseErrorCode.ServerCancelled.getValue()) {
            var data = response.getResponseError().getData();
            if (data == null) {
                return true;
            }
            var gson = JSONUtils.getLsp4jGson();
            var cancellation = gson.fromJson(gson.toJsonTree(data), DiagnosticServerCancellationData.class);
            return cancellation == null || cancellation.isRetriggerRequest();
        }
        if (!(error instanceof CancellationException)) {
            // Do not repeatedly hammer servers which advertise but fail this method.
            // A later refresh or registration can restart the loop.
            LOGGER.warn("Workspace diagnostics stopped", error);
        }
        return false;
    }

    private void schedule(Provider provider) {
        if (!disposed) {
            provider.scheduled = executor.schedule(() -> pull(provider), RETRIGGER_DELAY_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void cancel(Provider provider) {
        provider.token = null;
        provider.responseComplete = false;
        provider.pendingReports.clear();
        if (provider.matching != null) {
            provider.matching.cancel(true);
            provider.matching = null;
        }
        if (provider.request != null) {
            provider.request.cancel(true);
            provider.request = null;
        }
        if (provider.scheduled != null) {
            provider.scheduled.cancel(false);
            provider.scheduled = null;
        }
    }

    private void execute(Runnable task) {
        if (disposed) {
            return;
        }
        try {
            executor.execute(() -> {
                if (!disposed) {
                    task.run();
                }
            });
        } catch (RejectedExecutionException e) {
            if (!disposed) {
                throw e;
            }
        }
    }

    /**
     * Stops this manager and schedules cancellation of its outstanding work.
     * Shuts down the owned executor without waiting for pending callbacks.
     * Repeated calls have no effect.
     */
    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        // Do not wait for a report consumer/read action while stopping a server.
        executor.execute(() -> {
            providers.values().forEach(this::cancel);
            providers.clear();
        });
        executor.shutdown();
    }

    private static final class Provider {
        final String identifier;
        final Map<String, String> resultIds = new HashMap<>();
        final Deque<WorkspaceDocumentDiagnosticReport> pendingReports = new ArrayDeque<>();
        String token;
        boolean responseComplete;
        CompletableFuture<Boolean> matching;
        CompletableFuture<WorkspaceDiagnosticReport> request;
        ScheduledFuture<?> scheduled;

        Provider(String identifier) {
            this.identifier = identifier;
        }
    }
}
