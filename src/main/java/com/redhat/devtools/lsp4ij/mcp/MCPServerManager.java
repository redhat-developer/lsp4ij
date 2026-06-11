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
package com.redhat.devtools.lsp4ij.mcp;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.redhat.devtools.lsp4ij.mcp.settings.MCPSettings;
import com.redhat.devtools.lsp4ij.mcp.settings.MCPTrace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application-level service to manage the lifecycle of the MCP server.
 *
 * This service provides:
 * - Start/stop server lifecycle management (shared across all projects)
 * - Port configuration
 * - Server status monitoring
 * - Event notifications for UI updates
 * - Project routing for tool calls
 */
@Service(Service.Level.APP)
public final class MCPServerManager implements Disposable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MCPServerManager.class);
    private static final long TRACE_FLUSH_DELAY_MS = 500L;

    private LSP4IJMCPServer mcpServer;
    private ServerStatus status = ServerStatus.STOPPED;
    private final List<ServerStatusListener> listeners = new CopyOnWriteArrayList<>();
    private final List<TraceListener> traceListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<TraceMessage> traceQueue = new ConcurrentLinkedQueue<>();
    private Alarm traceFlushAlarm;
    private final Clock clock = Clock.systemDefaultZone();
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(clock.getZone());
    private final Map<String, RequestMetadata> sentRequests = new ConcurrentHashMap<>();

    /**
     * Metadata for tracking request timing.
     */
    private static class RequestMetadata {
        final String method;
        final Instant start;

        RequestMetadata(String method, Instant start) {
            this.method = method;
            this.start = start;
        }
    }

    public enum ServerStatus {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR
    }

    public interface ServerStatusListener {
        void onStatusChanged(ServerStatus newStatus, String message);
    }

    public interface TraceListener {
        void onTrace(String message);
        void onRequest(String method, String params);
        void onResponse(String method, String result);
    }

    /**
     * Internal class to represent a trace message.
     */
    private static class TraceMessage {
        enum Type { TRACE, REQUEST, RESPONSE }
        final Type type;
        final String content;

        TraceMessage(Type type, String content) {
            this.type = type;
            this.content = content;
        }
    }

    public MCPServerManager() {
        traceFlushAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    }

    public static MCPServerManager getInstance() {
        return ApplicationManager.getApplication().getService(MCPServerManager.class);
    }

    /**
     * Start the MCP server on the configured port.
     */
    public void start() {
        if (status == ServerStatus.RUNNING || status == ServerStatus.STARTING) {
            LOGGER.warn("MCP server already running or starting");
            return;
        }

        int currentPort = getPort();
        setStatus(ServerStatus.STARTING, "Starting MCP server on port " + currentPort);

        try {
            mcpServer = new LSP4IJMCPServer(this);
            mcpServer.start();
            setStatus(ServerStatus.RUNNING, "MCP server running on port " + currentPort);
            notifyTrace("MCP server started on port " + currentPort);
            LOGGER.info("MCP server started successfully on port {}", currentPort);
        } catch (Exception e) {
            LOGGER.error("Failed to start MCP server", e);
            setStatus(ServerStatus.ERROR, "Failed to start: " + e.getMessage());
            notifyTrace("Failed to start MCP server: " + e.getMessage());
            mcpServer = null;
        }
    }

    /**
     * Stop the MCP server.
     */
    public void stop() {
        if (status == ServerStatus.STOPPED) {
            return;
        }

        setStatus(ServerStatus.STOPPED, "Stopping MCP server");

        try {
            if (mcpServer != null) {
                mcpServer.stop();
                mcpServer = null;
            }
            setStatus(ServerStatus.STOPPED, "MCP server stopped");
            notifyTrace("MCP server stopped");
            LOGGER.info("MCP server stopped");
        } catch (Exception e) {
            LOGGER.error("Error stopping MCP server", e);
            setStatus(ServerStatus.ERROR, "Error stopping: " + e.getMessage());
            notifyTrace("Error stopping MCP server: " + e.getMessage());
        }
    }

    /**
     * Restart the MCP server.
     */
    public void restart() {
        stop();
        start();
    }

    /**
     * Get the current server status.
     */
    public ServerStatus getStatus() {
        return status;
    }

    /**
     * Get the configured port.
     */
    public int getPort() {
        return MCPSettings.getInstance().getPort();
    }

    /**
     * Set the port (server must be stopped).
     */
    public void setPort(int port) {
        if (status != ServerStatus.STOPPED) {
            throw new IllegalStateException("Cannot change port while server is running");
        }
        MCPSettings.getInstance().setPort(port);
    }

    /**
     * Get the trace level from settings.
     */
    public MCPTrace getTrace() {
        return MCPSettings.getInstance().getTrace();
    }

    /**
     * Set the trace level in settings.
     */
    public void setTrace(MCPTrace trace) {
        MCPSettings.getInstance().setTrace(trace);
    }

    /**
     * Check if the server is running.
     */
    public boolean isRunning() {
        return status == ServerStatus.RUNNING && mcpServer != null && mcpServer.isStarted();
    }

    /**
     * Add a status listener.
     */
    public void addStatusListener(ServerStatusListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a status listener.
     */
    public void removeStatusListener(ServerStatusListener listener) {
        listeners.remove(listener);
    }

    /**
     * Add a trace listener.
     */
    public void addTraceListener(TraceListener listener) {
        traceListeners.add(listener);
    }

    /**
     * Remove a trace listener.
     */
    public void removeTraceListener(TraceListener listener) {
        traceListeners.remove(listener);
    }

    /**
     * Notify trace listeners.
     */
    public void notifyTrace(String message) {
        MCPTrace trace = getTrace();
        if (trace == MCPTrace.off) {
            return; // Don't trace if disabled
        }
        traceQueue.add(new TraceMessage(TraceMessage.Type.TRACE, message));
        scheduleFlushTraces();
    }

    /**
     * Notify request trace.
     */
    public void notifyRequest(String method, String params) {
        MCPTrace trace = getTrace();
        if (trace == MCPTrace.off) {
            return; // Don't trace if disabled
        }

        Instant now = clock.instant();
        String timestamp = dateTimeFormatter.format(now);

        // Track request start time for latency calculation
        sentRequests.put(method, new RequestMetadata(method, now));

        String message;
        if (trace == MCPTrace.messages) {
            // Messages only - no params
            message = String.format("[Trace - %s] Sending request '%s'", timestamp, method);
        } else {
            // Verbose - include params
            message = String.format("[Trace - %s] Sending request '%s'\nParams: %s\n\n", timestamp, method, params);
        }
        traceQueue.add(new TraceMessage(TraceMessage.Type.REQUEST, message));
        scheduleFlushTraces();
    }

    /**
     * Notify response trace.
     */
    public void notifyResponse(String method, String result) {
        MCPTrace trace = getTrace();
        if (trace == MCPTrace.off) {
            return; // Don't trace if disabled
        }

        Instant now = clock.instant();
        String timestamp = dateTimeFormatter.format(now);

        // Calculate latency if we tracked the request
        RequestMetadata requestMetadata = sentRequests.remove(method);
        String latency = "";
        if (requestMetadata != null) {
            long latencyMs = now.toEpochMilli() - requestMetadata.start.toEpochMilli();
            latency = String.format(" in %dms", latencyMs);
        }

        String message;
        if (trace == MCPTrace.messages) {
            // Messages only - no result, but show latency
            message = String.format("[Trace - %s] Received response '%s'%s", timestamp, method, latency);
        } else {
            // Verbose - include result and latency
            message = String.format("[Trace - %s] Received response '%s'%s\nResult: %s\n\n", timestamp, method, latency, result);
        }
        traceQueue.add(new TraceMessage(TraceMessage.Type.RESPONSE, message));
        scheduleFlushTraces();
    }

    /**
     * Schedule flushing of batched traces.
     */
    private void scheduleFlushTraces() {
        if (traceFlushAlarm == null || traceFlushAlarm.isDisposed()) {
            return;
        }
        traceFlushAlarm.addRequest(() -> {
            if (traceQueue.isEmpty() || traceListeners.isEmpty()) {
                return;
            }

            // Collect all pending traces
            StringBuilder batch = new StringBuilder();
            TraceMessage trace;
            while ((trace = traceQueue.poll()) != null) {
                batch.append(trace.content).append("\n\n");
            }

            // Notify listeners with batched content
            if (!batch.isEmpty()) {
                String batchContent = batch.toString();
                for (TraceListener listener : traceListeners) {
                    try {
                        listener.onTrace(batchContent);
                    } catch (Exception e) {
                        LOGGER.error("Error notifying trace listener", e);
                    }
                }
            }
        }, TRACE_FLUSH_DELAY_MS);
    }

    private void setStatus(ServerStatus newStatus, String message) {
        this.status = newStatus;
        for (ServerStatusListener listener : listeners) {
            try {
                listener.onStatusChanged(newStatus, message);
            } catch (Exception e) {
                LOGGER.error("Error notifying status listener", e);
            }
        }
    }

    /**
     * Get the MCP server instance (for internal use).
     */
    public LSP4IJMCPServer getMcpServer() {
        return mcpServer;
    }

    @Override
    public void dispose() {
        stop();
        listeners.clear();
        traceListeners.clear();
    }
}
