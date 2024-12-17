/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 * Fraunhofer FOKUS
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.commands;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * This class provides methods to execute LSP {@link Command} instances.
 */
public class CommandExecutor {

    public static final DataKey<LSPCommand> LSP_COMMAND = DataKey.create("com.redhat.devtools.lsp4ij.command");

    public static final DataKey<LanguageServerItem> LSP_COMMAND_LANGUAGE_SERVER = DataKey.create("com.redhat.devtools.lsp4ij.command.server");

    /**
     * The command response.
     *
     * @param exists   true if the command is executed on server / client side and false otherwise.
     * @param response the response of workspace/executeCommand executed on server side and null otherwise.
     */
    public record LSPCommandResponse(boolean exists, @Nullable CompletableFuture<java.lang.Object> response) {

        private static final LSPCommandResponse NOT_FOUND = new LSPCommandResponse(false, CompletableFuture.completedFuture(null));

        private static final LSPCommandResponse FOUND = new LSPCommandResponse(true, CompletableFuture.completedFuture(null));
    }

    /**
     * Will execute the given {@code command} either on a language server,
     * supporting the command, or on the client, if an {@link AnAction} is
     * registered for the ID of the command. If
     * {@code command} is {@code null}, then this method will do nothing. If neither
     * the server, nor the client are able to handle the command explicitly, a
     * heuristic method will try to interpret the command locally.
     *
     * @param context the LSP command context.
     * @return the {@link LSPCommandResponse} if the command exists on language server / client side and {@link LSPCommandResponse#exists() to false} otherwise.
     */
    public static LSPCommandResponse executeCommand(LSPCommandContext context) {
        Command command = context.getCommand();

        // 1. try to execute command on server side
        LSPCommandResponse result = executeCommandServerSide(command, context.getPreferredLanguageServer(), context.getPreferredLanguageServerId(), context.getProject());
        if (result.exists()) {
            return result;
        }

        VirtualFile file = context.getFile();
        if (file == null && context.getPsiFile() != null) {
            file = context.getPsiFile().getVirtualFile();
        }
        // 2. try to execute command on client side
        if (executeCommandClientSide(command,
                context.getProject(),
                context.getPsiFile(),
                file,
                context.getEditor(),
                context.getSource(),
                context.getInputEvent(),
                context.getPreferredLanguageServer())) {
            return LSPCommandResponse.FOUND;
        }

        // 3. tentative fallback
        if (file != null && command.getArguments() != null) {
                LanguageServerItem languageServer = context.getPreferredLanguageServer();
                FileUriSupport fileUriSupport = languageServer != null ? languageServer.getClientFeatures() : null;
                WorkspaceEdit edit = createWorkspaceEdit(command.getArguments(), file, fileUriSupport);
                if (edit != null) {
                    if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
                        LSPIJUtils.applyWorkspaceEdit(edit);
                    } else {
                        return WriteCommandAction.runWriteCommandAction(context.getProject(), (Computable<LSPCommandResponse>) () -> {
                            LSPIJUtils.applyWorkspaceEdit(edit);
                            return LSPCommandResponse.FOUND;
                        });
                    }
                    return LSPCommandResponse.FOUND;
            }
        }
        if (context.isShowNotificationError()) {
            Project project = context.getProject();
            String commandId = context.getCommand().getCommand();
            var preferredLanguageServer = context.getPreferredLanguageServer();
            String content = preferredLanguageServer != null ?
                    MarkdownConverter.getInstance(project).toHtml(LanguageServerBundle.message("lsp.command.error.with.ls.content", commandId, preferredLanguageServer.getServerDefinition().getDisplayName())) :
                    MarkdownConverter.getInstance(project).toHtml(LanguageServerBundle.message("lsp.command.error.without.ls.content", commandId));

            Notification notification = new Notification(LSPNotificationConstants.LSP4IJ_GENERAL_NOTIFICATIONS_ID,
                    LanguageServerBundle.message("lsp.command.error.title", commandId),
                    content,
                    NotificationType.ERROR);
            notification.setIcon(AllIcons.General.Error);
            notification.setListener(NotificationListener.URL_OPENING_LISTENER);
            Notifications.Bus.notify(notification, context.getProject());
        }
        return LSPCommandResponse.NOT_FOUND;
    }

    /**
     * Execute LSP command on server side.
     *
     * @param command                   the LSP Command to be executed. If {@code null} this method will
     *                                  do nothing.
     * @param languageServer            the language server for which the {@code command} is
     *                                  applicable.
     * @param preferredLanguageServerId the preferred language server Id.
     * @param project the project.
     * @return the LSP command response.
     */
    private static LSPCommandResponse executeCommandServerSide(@NotNull Command command,
                                                               @Nullable LanguageServerItem languageServer,
                                                               @Nullable String preferredLanguageServerId,
                                                               @NotNull Project project) {
        CompletableFuture<LanguageServer> languageServerFuture = getLanguageServerForCommand(command, languageServer, preferredLanguageServerId, project);
        if (languageServerFuture == null) {
            return LSPCommandResponse.NOT_FOUND;
        }
        // Server can handle command
        CompletableFuture<Object> result = languageServerFuture
                .thenComposeAsync(server -> {
                    if (server == null) {
                        MessageParams messageParams = new MessageParams(MessageType.Error, LanguageServerBundle.message("lsp.command.error.ls.undefined.title"));
                        ServerMessageHandler.showMessage(LanguageServerBundle.message("lsp.command.error.ls.undefined.content", command.getCommand(), preferredLanguageServerId), messageParams, project);
                        return CompletableFuture.completedFuture(null);
                    }
                    ExecuteCommandParams params = new ExecuteCommandParams();
                    params.setCommand(command.getCommand());
                    params.setArguments(command.getArguments());
                    return server.getWorkspaceService()
                            .executeCommand(params)
                            .exceptionally(error -> {
                                // Language server throws an error when executing a command
                                // Display it with an IntelliJ notification.
                                MessageParams messageParams = new MessageParams(MessageType.Error, error.getMessage());
                                ServerMessageHandler.showMessage(languageServer.getServerDefinition().getDisplayName(), messageParams, project);
                                return error;
                            });
                });
        return new LSPCommandResponse(true, result);
    }

    @Nullable
    private static CompletableFuture<LanguageServer> getLanguageServerForCommand(@NotNull Command command,
                                                                                 @Nullable LanguageServerItem languageServer,
                                                                                 @Nullable String preferredLanguageServerId,
                                                                                 @NotNull Project project) {

        if (languageServer != null && languageServer.supportsCommand(command)) {
            return languageServer
                    .getInitializedServer();
        }
        if (preferredLanguageServerId != null) {
            return LanguageServerManager.getInstance(project)
                    .getLanguageServer(preferredLanguageServerId)
                    .thenApply(ls -> ls != null ? ls.getServer() : null);
        }
        return null;
    }

    /**
     * Execute LSP command on server side.
     *
     * @param command        the LSP Command to be executed. If {@code null} this method will
     *                       do nothing.
     * @param project        the project.
     * @param psiFile        the Psi file.
     * @param file           the file.
     * @param editor         the editor.
     * @param source         the component which has triggered the command and null otherwise.
     * @param inputEvent     the input event.
     * @param languageServer the language server.
     * @return true if the LSP command on server side has been executed successfully and false otherwise.
     */
    private static boolean executeCommandClientSide(@NotNull Command command,
                                                    @NotNull Project project,
                                                    @Nullable PsiFile psiFile,
                                                    @Nullable VirtualFile file,
                                                    @Nullable Editor editor,
                                                    @Nullable Component source,
                                                    @Nullable InputEvent inputEvent,
                                                    @Nullable LanguageServerItem languageServer) {
        Application workbench = ApplicationManager.getApplication();
        if (workbench == null) {
            return false;
        }
        AnAction action = createIDEACoreCommand(command);
        if (action == null) {
            return false;
        }
        DataContext dataContext = createDataContext(file, psiFile, command, action, source, editor, languageServer, project);
        ActionUtil.invokeAction(action, dataContext, ActionPlaces.UNKNOWN, inputEvent, null);
        return true;
    }

    private static AnAction createIDEACoreCommand(Command command) {
        // Usually commands are defined via extension point, but we synthesize one on
        // the fly for the command ID, since we do not want downstream users
        // having to define them.
        String commandId = command.getCommand();
        return ActionManager.getInstance().getAction(commandId);
    }

    private static DataContext createDataContext(@Nullable VirtualFile file,
                                                 @Nullable PsiFile psiFile,
                                                 @NotNull Command command,
                                                 @NotNull AnAction action,
                                                 @Nullable Component source,
                                                 @Nullable Editor editor,
                                                 @Nullable LanguageServerItem languageServer,
                                                 @NotNull Project project) {
        SimpleDataContext.Builder contextBuilder = SimpleDataContext.builder();
        if (source != null) {
            contextBuilder.setParent(DataManager.getInstance().getDataContext(source));
        }
        contextBuilder
                .add(CommonDataKeys.PROJECT, project)
                .add(LSP_COMMAND, new LSPCommand(command, action.getClass().getClassLoader()));
        if (file != null) {
            contextBuilder.add(CommonDataKeys.VIRTUAL_FILE, file);
        }
        if (psiFile != null) {
            contextBuilder.add(CommonDataKeys.PSI_FILE, psiFile);
        }
        if (editor != null) {
            contextBuilder.add(CommonDataKeys.EDITOR, editor);
        }
        if (languageServer != null) {
            contextBuilder.add(LSP_COMMAND_LANGUAGE_SERVER, languageServer);
        }
        return contextBuilder.build();
    }

    // TODO consider using Entry/SimpleEntry instead
    private static final class Pair<K, V> {
        K key;
        V value;

        Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // this method may be turned public if needed elsewhere

    /**
     * Very empirical and unsafe heuristic to turn unknown command arguments into a
     * workspace edit...
     */
    @Nullable
    private static WorkspaceEdit createWorkspaceEdit(@NotNull List<Object> commandArguments,
                                                     @NotNull VirtualFile initialFile,
                                                     @Nullable FileUriSupport fileUriSupport) {
        Map<String, List<TextEdit>> changes = new HashMap<>();
        URI initialUri = FileUriSupport.getFileUri(initialFile, fileUriSupport);
        Pair<URI, List<TextEdit>> currentEntry = new Pair<>(initialUri, new ArrayList<>());
        commandArguments.stream().flatMap(item -> {
            if (item instanceof List) {
                return ((List<?>) item).stream();
            } else {
                return Stream.of(item);
            }
        }).forEach(arg -> {
            if (arg instanceof String) {
                changes.put(currentEntry.key.toASCIIString(), currentEntry.value);
                VirtualFile resource = FileUriSupport.findFileByUri((String) arg, fileUriSupport);
                if (resource != null) {
                    currentEntry.key = FileUriSupport.getFileUri(resource, fileUriSupport);
                    currentEntry.value = new ArrayList<>();
                }
            } else if (arg instanceof WorkspaceEdit) {
                changes.putAll(((WorkspaceEdit) arg).getChanges());
            } else if (arg instanceof TextEdit) {
                currentEntry.value.add((TextEdit) arg);
            } else if (arg instanceof Map) {
                Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
                TextEdit edit = gson.fromJson(gson.toJson(arg), TextEdit.class);
                if (isValid(edit)) {
                    currentEntry.value.add(edit);
                }
            } else if (arg instanceof JsonPrimitive json) {
                if (json.isString()) {
                    changes.put(currentEntry.key.toASCIIString(), currentEntry.value);
                    VirtualFile resource = FileUriSupport.findFileByUri(json.getAsString(), fileUriSupport);
                    if (resource != null) {
                        currentEntry.key = FileUriSupport.getFileUri(resource, fileUriSupport);
                        currentEntry.value = new ArrayList<>();
                    }
                }
            } else if (arg instanceof JsonArray array) {
                Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
                array.forEach(elt -> {
                    TextEdit edit = gson.fromJson(gson.toJson(elt), TextEdit.class);
                    if (isValid(edit)) {
                        currentEntry.value.add(edit);
                    }
                });
            } else if (arg instanceof JsonObject) {
                Gson gson = new Gson(); // TODO? retrieve the GSon used by LS
                WorkspaceEdit wEdit = gson.fromJson((JsonObject) arg, WorkspaceEdit.class);
                Map<String, List<TextEdit>> entries = wEdit.getChanges();
                if (wEdit != null && !entries.isEmpty()) {
                    changes.putAll(entries);
                } else {
                    TextEdit edit = gson.fromJson((JsonObject) arg, TextEdit.class);
                    if (isValid(edit)) {
                        currentEntry.value.add(edit);
                    }
                }
            }
        });
        if (!currentEntry.value.isEmpty()) {
            changes.put(currentEntry.key.toASCIIString(), currentEntry.value);
        }
        if (changes.isEmpty()) {
            return null;
        }
        boolean hasTextEdits = changes.values()
                .stream()
                .anyMatch(edits -> edits != null && !edits.isEmpty());
        if (!hasTextEdits) {
            return null;
        }
        WorkspaceEdit workspaceEdit = new WorkspaceEdit();
        workspaceEdit.setChanges(changes);
        return workspaceEdit;
    }

    private static boolean isValid(TextEdit edit) {
        return (edit != null && edit.getRange() != null && edit.getNewText() != null);
    }
}