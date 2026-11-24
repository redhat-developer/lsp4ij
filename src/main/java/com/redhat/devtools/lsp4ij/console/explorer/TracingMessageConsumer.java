/******************************************************************************
 * Copyright (c) 2016-2019 TypeFox and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0,
 * or the Eclipse Distribution License v. 1.0 which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.console.explorer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LSPRequestConstants;
import com.redhat.devtools.lsp4ij.settings.ServerTrace;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.MessageIssueException;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.json.StreamMessageConsumer;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.MessageIssue;
import org.eclipse.lsp4j.jsonrpc.messages.NotificationMessage;
import org.eclipse.lsp4j.jsonrpc.messages.RequestMessage;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Outputs logs in a format that can be parsed by the LSP Inspector.
 * https://microsoft.github.io/language-server-protocol/inspector/
 * <p>
 * This class is a copy/paste of https://github.com/eclipse-lsp4j/lsp4j/blob/main/org.eclipse.lsp4j.jsonrpc/src/main/java/org/eclipse/lsp4j/jsonrpc/TracingMessageConsumer.java
 * adapted for IJ.
 */
public class TracingMessageConsumer {

    private static MessageJsonHandler toStringInstance;

    private final Map<String, RequestMetadata> sentRequests;
    private final Map<String, RequestMetadata> receivedRequests;
    private final Clock clock;
    private final DateTimeFormatter dateTimeFormatter;

    public TracingMessageConsumer() {
        this.sentRequests = new ConcurrentHashMap<>();
        this.receivedRequests = new ConcurrentHashMap<>();
        this.clock = Clock.systemDefaultZone();
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(clock.getZone());
    }

    /**
     * Perform JSON serialization of the given object using the default configuration of JSON-RPC messages
     * enhanced with the pretty printing option.
     */
    public static String toJsonString(Object object) {
        if (toStringInstance == null) {
            toStringInstance = new MessageJsonHandler(Collections.emptyMap(), gsonBuilder -> {
                JSONUtils.configureCompatibilityAdapters(gsonBuilder);
                gsonBuilder.setPrettyPrinting();
            });
        }
        return toStringInstance.getGson().toJson(object);
    }

    /**
     * Constructs a log string for a given {@link Message}. The type of the {@link MessageConsumer}
     * determines if we're sending or receiving a message. The type of the @{link Message} determines
     * if it is a request, response, or notification.
     */
    public String log(Message message, MessageConsumer messageConsumer, ServerTrace serverTrace) throws MessageIssueException, JsonRpcException {
        return log(message, messageConsumer, serverTrace, clock.instant());
    }

    /**
     * Constructs a log string for a given {@link Message} that LSP4IJ sent or received at the given time, which the
     * log string shows, and from which it computes the latency of a response.
     */
    public String log(Message message, MessageConsumer messageConsumer, ServerTrace serverTrace, Instant now) throws MessageIssueException, JsonRpcException {
        final String date = dateTimeFormatter.format(now);

        if (messageConsumer instanceof StreamMessageConsumer) {
            return consumeMessageSending(message, now, date, serverTrace);
        } else if (messageConsumer instanceof RemoteEndpoint) {
            return consumeMessageReceiving(message, now, date, serverTrace);
        } else {
            return String.format("Unknown MessageConsumer type: %s", messageConsumer);
        }
    }

    /**
     * Constructs a log string for a given {@link Message} that LSP4IJ received at the given time and that LSP4J could
     * not parse, with the issues that LSP4J found in it. LSP4J hands such a message to its issue handler instead of to
     * the message consumers, which trace the messages that it parses (#584).
     * <p>
     * The message has what LSP4J read of it: its method and id, and its params or result only if LSP4J kept them.
     *
     * @param message     the message, as far as LSP4J parsed it.
     * @param issues      the issues that LSP4J found in the message.
     * @param serverTrace the server trace.
     * @param now         the time when LSP4IJ received the message.
     * @return the log string.
     */
    public String logIssues(Message message, List<MessageIssue> issues, ServerTrace serverTrace, Instant now) {
        final String date = dateTimeFormatter.format(now);
        final String received;
        // The params, result or error that LSP4J kept, which it couldn't read into their type
        final String content;
        if (message instanceof RequestMessage requestMessage) {
            String method = getTracedMethod(requestMessage);
            String id = requestMessage.getId();
            // The error response that LSP4J sends to the request shows its method
            receivedRequests.put(id, new RequestMetadata(method, now));
            received = String.format("request '%s - (%s)'", method, id);
            content = getContentTrace("Params", requestMessage.getParams());
        } else if (message instanceof ResponseMessage responseMessage) {
            String id = responseMessage.getId();
            RequestMetadata requestMetadata = sentRequests.remove(id);
            received = String.format("response '%s - (%s)' in %sms", getMethod(requestMetadata), id,
                    getLatencyMillis(requestMetadata, now));
            content = responseMessage.getError() != null ?
                    getContentTrace("Error", responseMessage.getError()) :
                    getContentTrace("Result", responseMessage.getResult());
        } else if (message instanceof NotificationMessage notificationMessage) {
            received = String.format("notification '%s'", notificationMessage.getMethod());
            content = getContentTrace("Params", notificationMessage.getParams());
        } else {
            received = "message";
            content = "";
        }
        String issuesTrace = issues.stream()
                .map(TracingMessageConsumer::getIssueTrace)
                .collect(Collectors.joining("\n"));
        String trace = String.format("[Trace - %s] Received %s that could not be parsed.\n%s\n", date, received, issuesTrace);
        if (serverTrace == ServerTrace.messages) {
            return trace;
        }
        return String.format("%s%s\n\n", trace, content);
    }

    private static String getContentTrace(String label, @Nullable Object content) {
        return content != null ? String.format("%s: %s\n", label, toJsonString(content)) : "";
    }

    /**
     * Returns the trace of the given issue: its text, and the message of its cause, which says what LSP4J couldn't
     * read, as {@code Expected an int but was 4294967295 at line 1 column 236 path $.params.diagnostics[0].range.end.character}.
     */
    private static String getIssueTrace(MessageIssue issue) {
        Exception cause = issue.getCause();
        return cause != null && cause.getMessage() != null ?
                String.format("Issue: %s %s", issue.getText(), cause.getMessage()) :
                String.format("Issue: %s", issue.getText());
    }

    private String consumeMessageSending(Message message, Instant now, String date, ServerTrace serverTrace) {
        if (message instanceof RequestMessage) {
            RequestMessage requestMessage = (RequestMessage) message;
            String id = requestMessage.getId();
            String method = getTracedMethod(requestMessage);
            RequestMetadata requestMetadata = new RequestMetadata(method, now);
            sentRequests.put(id, requestMetadata);
            if (serverTrace == ServerTrace.messages) {
                String format = "[Trace - %s] Sending request '%s - (%s)'.\n";
                return String.format(format, date, method, id);
            }
            Object params = requestMessage.getParams();
            String paramsJson = toJsonString(params);
            String format = "[Trace - %s] Sending request '%s - (%s)'.\nParams: %s\n\n\n";
            return String.format(format, date, method, id, paramsJson);
        } else if (message instanceof ResponseMessage) {
            ResponseMessage responseMessage = (ResponseMessage) message;
            String id = responseMessage.getId();
            RequestMetadata requestMetadata = receivedRequests.remove(id);
            String method = getMethod(requestMetadata);
            String latencyMillis = getLatencyMillis(requestMetadata, now);
            if (serverTrace == ServerTrace.messages) {
                String format = "[Trace - %s] Sending response '%s - (%s)'. Processing request took %sms\n";
                return String.format(format, date, method, id, latencyMillis);
            }
            Object result = responseMessage.getResult();
            String resultJson = toJsonString(result);
            String resultTrace = getResultTrace(resultJson, null);
            String format =
                    "[Trace - %s] Sending response '%s - (%s)'. Processing request took %sms\n%s\n\n\n";
            return String.format(format, date, method, id, latencyMillis, resultTrace);
        } else if (message instanceof NotificationMessage) {
            NotificationMessage notificationMessage = (NotificationMessage) message;
            String method = notificationMessage.getMethod();
            if (serverTrace == ServerTrace.messages) {
                String format = "[Trace - %s] Sending notification '%s'\n";
                return String.format(format, date, method);
            }
            Object params = notificationMessage.getParams();
            String paramsJson = toJsonString(params);
            String format = "[Trace - %s] Sending notification '%s'\nParams: %s\n\n\n";
            return String.format(format, date, method, paramsJson);
        } else {
            return String.format("Unknown message type: %s", message);
        }
    }

    private String consumeMessageReceiving(Message message, Instant now, String date, ServerTrace serverTrace) {
        if (message instanceof RequestMessage) {
            RequestMessage requestMessage = (RequestMessage) message;
            String method = getTracedMethod(requestMessage);
            String id = requestMessage.getId();
            RequestMetadata requestMetadata = new RequestMetadata(method, now);
            receivedRequests.put(id, requestMetadata);
            if (serverTrace == ServerTrace.messages) {
                String format = "[Trace - %s] Received request '%s - (%s)'.\n";
                return String.format(format, date, method, id);
            }
            Object params = requestMessage.getParams();
            String paramsJson = toJsonString(params);
            String format = "[Trace - %s] Received request '%s - (%s)'\nParams: %s\n\n\n";
            return String.format(format, date, method, id, paramsJson);
        } else if (message instanceof ResponseMessage) {
            ResponseMessage responseMessage = (ResponseMessage) message;
            String id = responseMessage.getId();
            RequestMetadata requestMetadata = sentRequests.remove(id);
            String method = getMethod(requestMetadata);
            String latencyMillis = getLatencyMillis(requestMetadata, now);
            if (serverTrace == ServerTrace.messages) {
                String format = "[Trace - %s] Received response '%s - (%s)' in %sms.\n";
                return String.format(format, date, method, id, latencyMillis);
            }
            Object result = responseMessage.getResult();
            String resultJson = toJsonString(result);
            Object error = responseMessage.getError();
            String errorJson = toJsonString(error);
            String resultTrace = getResultTrace(resultJson, errorJson);
            String format = "[Trace - %s] Received response '%s - (%s)' in %sms.\n%s\n\n\n";
            return String.format(format, date, method, id, latencyMillis, resultTrace);
        } else if (message instanceof NotificationMessage) {
            NotificationMessage notificationMessage = (NotificationMessage) message;
            String method = notificationMessage.getMethod();
            if (serverTrace == ServerTrace.messages) {
                String format = "[Trace - %s] Received notification '%s'\n";
                return String.format(format, date, method);
            }
            Object params = notificationMessage.getParams();
            String paramsJson = toJsonString(params);
            String format = "[Trace - %s] Received notification '%s'\nParams: %s\n\n\n";
            return String.format(format, date, method, paramsJson);
        } else {
            return String.format("Unknown message type: %s", message);
        }
    }

    private static String getResultTrace(String resultJson, String errorJson) {
        StringBuilder result = new StringBuilder();
        if (resultJson != null && !"null".equals(resultJson)) {
            result.append("Result: ");
            result.append(resultJson);
        } else {
            result.append("No response returned.");
        }
        if (errorJson != null && !"null".equals(errorJson)) {
            result.append("\nError: ");
            result.append(errorJson);
        }
        return result.toString();
    }

    /**
     * Returns the method of the given request as the traces show it, for the request and for its response: a
     * {@code workspace/executeCommand} request also shows the command that it executes, as
     * {@code workspace/executeCommand [gopls.tidy]}, which the traces would otherwise show only in its params, with
     * the verbose level (#284).
     */
    private static String getTracedMethod(RequestMessage requestMessage) {
        String method = requestMessage.getMethod();
        if (!LSPRequestConstants.WORKSPACE_EXECUTE_COMMAND.equals(method)) {
            return method;
        }
        String command = getCommand(requestMessage.getParams());
        return command != null ? method + " [" + command + "]" : method;
    }

    /**
     * Returns the command of the given params of a {@code workspace/executeCommand} request: LSP4IJ sends them as
     * {@link ExecuteCommandParams}, while a request that hasn't been read into its type has them as JSON.
     */
    private static @Nullable String getCommand(@Nullable Object params) {
        if (params instanceof ExecuteCommandParams executeCommandParams) {
            return executeCommandParams.getCommand();
        }
        if (params instanceof JsonObject json
                && json.get("command") instanceof JsonPrimitive command
                && command.isString()) {
            return command.getAsString();
        }
        return null;
    }

    private static String getMethod(RequestMetadata requestMetadata) {
        return requestMetadata != null ? requestMetadata.method : "<unknown>";
    }

    private static String getLatencyMillis(RequestMetadata requestMetadata, Instant now) {
        return requestMetadata != null ? String.valueOf(now.toEpochMilli() - requestMetadata.start.toEpochMilli()) : "?";
    }

    /**
     * Data class for holding pending request metadata.
     */
    public static class RequestMetadata {
        final String method;
        final Instant start;

        public RequestMetadata(String method, Instant start) {
            this.method = method;
            this.start = start;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            RequestMetadata that = (RequestMetadata) o;
            return Objects.equals(method, that.method) && Objects.equals(start, that.start);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, start);
        }
    }
}
