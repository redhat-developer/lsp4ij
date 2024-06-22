/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens.inspector;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Semantic tokens inspector manager.
 */
public class SemanticTokensInspectorManager {

    private static @NotNull Comparator<SemanticTokensHighlightInfo> HIGHLIGHT_INFO_SORTER() {
        return (a, b) -> {
            int diff = b.start() - a.start();
            if (diff == 0) {
                return b.end() - a.end();
            }
            return diff;
        };
    }

    public static SemanticTokensInspectorManager getInstance(@NotNull Project project) {
        return project.getService(SemanticTokensInspectorManager.class);
    }

    private final Collection<SemanticTokensInspectorListener> listeners = new CopyOnWriteArrayList<>();

    public void addSemanticTokensInspectorListener(@NotNull SemanticTokensInspectorListener listener) {
        this.listeners.add(listener);
    }

    public void removeSemanticTokensInspectorListener(@NotNull SemanticTokensInspectorListener listener) {
        this.listeners.remove(listener);
    }

    public boolean hasSemanticTokensInspectorListener() {
        return !listeners.isEmpty();
    }

    public static String format(SemanticTokensInspectorData data,
                                boolean showTextAttributes,
                                boolean showTokenType,
                                boolean showTokenModifiers,
                                Project project) {
        var document = data.document();
        var infos = data.highlightInfos();
        if (infos.isEmpty()) {
            return document.getText();
        }
        infos.sort(HIGHLIGHT_INFO_SORTER());
        if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
            return doFormat(infos, document, showTextAttributes, showTokenType, showTokenModifiers);
        }
        return WriteCommandAction.runWriteCommandAction(project, (Computable<String>) () -> {
            return doFormat(infos, document, showTextAttributes, showTokenType, showTokenModifiers);
        });
    }

    private static @NotNull String doFormat(List<SemanticTokensHighlightInfo> infos,
                                            Document document,
                                            boolean showTextAttributes,
                                            boolean showTokenType,
                                            boolean showTokenModifiers) {
        Document newDocument = new DocumentImpl(document.getText());
        for (var info : infos) {
            String infoToShow = format(info, showTextAttributes, showTokenType, showTokenModifiers);

            int end = info.end();
            String endContent = "</" + infoToShow + ">";
            LSPIJUtils.applyEdit(end, end, endContent, newDocument, -1);

            int start = info.start();
            String startContent = "<" + infoToShow + ">";
            LSPIJUtils.applyEdit(start, start, startContent, newDocument, -1);
        }
        return newDocument.getText();
    }

    @Nullable
    private static String format(@NotNull SemanticTokensHighlightInfo info,
                                 boolean showTextAttributes,
                                 boolean showTokenType,
                                 boolean showTokenModifiers) {
        StringBuilder formattedInfo = new StringBuilder();
        if (showTextAttributes) {
            formattedInfo.append(info.colorKey() != null ? info.colorKey().getExternalName() : null);
        }
        if (showTokenType) {
            if (showTextAttributes) {
                formattedInfo.append(" - ");
            }
            formattedInfo.append(info.tokenType());
        }
        if (showTokenModifiers) {
            formattedInfo.append(":");
            formattedInfo.append(info.tokenModifiers().stream().collect(Collectors.joining(".")));
        }
        return formattedInfo.toString();
    }

    public void notify(@NotNull SemanticTokensInspectorData data) {
        for (SemanticTokensInspectorListener listener : listeners) {
            listener.notify(data);
        }
    }
}
