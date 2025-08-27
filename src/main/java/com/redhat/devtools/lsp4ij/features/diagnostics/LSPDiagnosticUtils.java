/*******************************************************************************
 * Copyright (c) 2025 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * LSP diagnostic utilities.
 */
public class LSPDiagnosticUtils {

    private static final @NotNull Object LSP4IJ_REPORT_PROBLEM_SOURCE = new Object();

    private LSPDiagnosticUtils() {

    }

    /**
     * Returns true if the old and new diagnostics list changed and false otherwise.
     *
     * @param oldDiagnostics old diagnostics
     * @param newDiagnostics new diagnostics
     * @return true if the old and new diagnostics list changed and false otherwise.
     */
    public static boolean isDiagnosticsChanged(@NotNull Collection<Diagnostic> oldDiagnostics,
                                               @NotNull Collection<Diagnostic> newDiagnostics) {
        if (oldDiagnostics.size() != newDiagnostics.size()) {
            return true;
        }
        for (var d : newDiagnostics) {
            if (!oldDiagnostics.contains(d)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Report problem in the Project View if the opened/closed document
     * has at least one diagnosis with a severity of error
     *
     * @param file     the file
     * @param document the opened/closed dpocument which trigger the report problem.
     * @param project  the project.
     */
    public static void reportProblem(@NotNull VirtualFile file,
                                     @Nullable LSPDocumentBase document,
                                     @NotNull Project project) {
        ReadAction.nonBlocking((Callable<Void>) () -> {
                    boolean hasErrors = isHasErrors(file, document, project);
                    reportProblem(file, project, hasErrors);
                    return null;
                })
                .coalesceBy(LSP4IJ_REPORT_PROBLEM_SOURCE, file, project)
                .submit(AppExecutorUtil.getAppExecutorService());
    }

    private static boolean isHasErrors(@NotNull VirtualFile file,
                                       @Nullable LSPDocumentBase document,
                                       @NotNull Project project) {
        boolean hasErrors = document != null && document.hasErrors();
        if (!hasErrors) {
            hasErrors = hasErrors(file, project);
        }
        return hasErrors;
    }

    private static void reportProblem(@NotNull VirtualFile file,
                                      @NotNull Project project,
                                      boolean hasErrors) {
        WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);
        if (hasErrors) {
            wolf.reportProblemsFromExternalSource(file, LSP4IJ_REPORT_PROBLEM_SOURCE);
        } else {
            wolf.clearProblemsFromExternalSource(file, LSP4IJ_REPORT_PROBLEM_SOURCE);
        }
    }

    private static boolean hasErrors(@NotNull VirtualFile file,
                                     @NotNull Project project) {
        var servers = LanguageServiceAccessor.getInstance(project)
                .getStartedServers();
        for (var ls : servers) {
            var clientFeatures = ls.getClientFeatures();
            URI fileUri = FileUriSupport.getFileUri(file, clientFeatures);
            OpenedDocument openedDocument = ls.getOpenedDocument(fileUri);
            if (openedDocument != null && openedDocument.hasErrors()) {
                return true;
            } else {
                ClosedDocument closedDocument = ls.getClosedDocument(fileUri, false);
                if (closedDocument != null && closedDocument.hasErrors()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the given file can report problem and false otherwise.
     *
     * @param file    the file.
     * @param project the project.
     * @return true if the given file can report problem and false otherwise.
     */
    public static boolean canReportProblem(@NotNull VirtualFile file,
                                           @NotNull Project project) {
        var servers = LanguageServiceAccessor.getInstance(project)
                .getStartedServers();
        for (var ls : servers) {
            var clientFeatures = ls.getClientFeatures();
            URI fileUri = FileUriSupport.getFileUri(file, clientFeatures);
            OpenedDocument openedDocument = ls.getOpenedDocument(fileUri);
            if (openedDocument != null) {
                return true;
            } else {
                ClosedDocument closedDocument = ls.getClosedDocument(fileUri, false);
                if (closedDocument != null) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void clearProblem(@NotNull Set<URI> fileUris,
                                    @NotNull LSPClientFeatures clientFeatures,
                                    @NotNull Project project) {
        WolfTheProblemSolver wolf = WolfTheProblemSolver.getInstance(project);
        ReadAction.nonBlocking((Callable<Void>) () -> {
                    for (URI fileUri : fileUris) {
                        VirtualFile file = FileUriSupport.findFileByUri(fileUri.toASCIIString(), clientFeatures);
                        if (file != null && clientFeatures.getDiagnosticFeature().canReportProblem(file)) {
                            wolf.clearProblemsFromExternalSource(file, LSP4IJ_REPORT_PROBLEM_SOURCE);
                        }
                    }
                    return null;
                })
                .coalesceBy(fileUris, clientFeatures, project)
                .submit(AppExecutorUtil.getAppExecutorService());
    }
}
