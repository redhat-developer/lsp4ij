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
package com.redhat.devtools.lsp4ij.operations.formatting;

import com.intellij.formatting.service.AsyncDocumentFormattingService;
import com.intellij.formatting.service.AsyncFormattingRequest;
import com.intellij.lang.LanguageFormatting;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import org.eclipse.lsp4j.ServerCapabilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for LSP {@link AsyncDocumentFormattingService} implementation.
 */
public  abstract class AbstractLSPFormattingService extends AsyncDocumentFormattingService {

    @Nullable
    @Override
    protected FormattingTask createFormattingTask(@NotNull AsyncFormattingRequest formattingRequest) {
        File ioFile = formattingRequest.getIOFile();
        if (ioFile == null) {
            return null;
        }
        VirtualFile file = LSPIJUtils.findResourceFor(ioFile.toURI());
        if (file == null) {
            return null;
        }
        Project project = formattingRequest.getContext().getProject();
        PsiFile psiFile = LSPIJUtils.getPsiFile(file, project);
        if (psiFile == null) {
            return null;
        }
        TextRange formattingRange = getFormattingRange(formattingRequest);
        return new LSPFormattingTask(psiFile, formattingRange, formattingRequest);
    }

    private static class LSPFormattingTask implements FormattingTask {

        private final @NotNull PsiFile psiFile;

        private final @Nullable TextRange formattingRange;

        private final @NotNull AsyncFormattingRequest formattingRequest;

        private LSPFormattingSupport formattingSupport;

        private LSPFormattingTask(@NotNull PsiFile psiFile, @Nullable TextRange formattingRange, @NotNull AsyncFormattingRequest formattingRequest) {
            this.psiFile = psiFile;
            this.formattingRange = formattingRange;
            this.formattingRequest = formattingRequest;
        }

        @Override
        public void run() {
            formattingSupport = LSPFileSupport.getSupport(psiFile).getFormattingSupport();
            formattingSupport.cancel();
            Editor[] editors = LSPIJUtils.editorsForFile(psiFile.getVirtualFile(), psiFile.getProject());
            formattingSupport.format(editors.length > 0 ? editors[0] : null, formattingRange, formattingRequest);
        }

        @Override
        public boolean cancel() {
            if (formattingSupport != null) {
                formattingSupport.cancel();
            }
            return true;
        }

        @Override
        public boolean isRunUnderProgress() {
            return true;
        }

    }

    @Override
    protected @NotNull String getNotificationGroupId() {
        return "LSP4IJ";
    }

    @Override
    protected @NotNull String getName() {
        return "LSP Formatting";
    }

    @Override
    public final boolean canFormat(@NotNull PsiFile file) {
        if (!LanguageFormatting.INSTANCE.allForLanguage(file.getLanguage()).isEmpty()) {
            // When IJ provides formatting for the language (ex : JAVA, HTML)
            // the LSP formatting support is ignored to avoid having some conflicts when formatting is done
            return false;
        }
        if (!LanguageServersRegistry.getInstance().isFileSupported(file)) {
            // The file is not associated to a language server
            return false;
        }
        // Check if the file can support formatting / range formatting
        Project project = file.getProject();
        try {
            return !LanguageServiceAccessor.getInstance(project)
                    .getLanguageServers(file.getVirtualFile(), this::canSupportFormatting)
                    .get(200, TimeUnit.MILLISECONDS)
                    .isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static TextRange getFormattingRange(AsyncFormattingRequest formattingRequest) {
        List<TextRange> ranges = formattingRequest.getFormattingRanges();
        if (ranges.isEmpty()) {
            return null;
        }
        TextRange textRange = ranges.get(0);
        if (textRange.getLength() == formattingRequest.getDocumentText().length()) {
            // The full document must be formatted
            return null;
        }
        return textRange;
    }

    protected abstract boolean canSupportFormatting(ServerCapabilities serverCapabilities);
}
