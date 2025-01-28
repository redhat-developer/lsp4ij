/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.openapi.progress.ProgressIndicator;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP progress feature.
 */
@ApiStatus.Experimental
public class LSPProgressFeature extends AbstractLSPFeature {

    private @Nullable ServerCapabilities serverCapabilities;

    @Override
    public void setServerCapabilities(@Nullable ServerCapabilities serverCapabilities) {
        this.serverCapabilities = serverCapabilities;
    }

    /**
     * Returns the progress task title.
     *
     * @param title coming from the {@link WorkDoneProgressBegin#getTitle()}.
     * @return the progress task title.
     */
    public String getProgressTaskTitle(@NotNull String title) {
        String name = getServerDefinition().getDisplayName();
        return name + ": " + title;
    }

    /**
     * On progress begin.
     *
     * @param begin             the LSP {@link WorkDoneProgressBegin}.
     * @param progressIndicator the progress indicator.
     */
    public void onProgressBegin(@NotNull WorkDoneProgressBegin begin,
                                @NotNull ProgressIndicator progressIndicator) {
        Integer percentage = begin.getPercentage();
        progressIndicator.setIndeterminate(percentage == null);
        updateProgressIndicator(begin.getMessage(), percentage, progressIndicator);
    }

    /**
     * On progress report.
     *
     * @param report            the LSP {@link WorkDoneProgressReport}.
     * @param progressIndicator the progress indicator.
     */
    public void onProgressReport(@NotNull WorkDoneProgressReport report,
                                 @NotNull ProgressIndicator progressIndicator) {
        updateProgressIndicator(report.getMessage(), report.getPercentage(), progressIndicator);
    }

    /**
     * On progress end.
     *
     * @param end the LSP {@link WorkDoneProgressEnd}.
     */
    public void onProgressEnd(@NotNull WorkDoneProgressEnd end) {
        // Do nothing
    }

    /**
     * Update text and fraction of the given progress indicator.
     *
     * @param message           the text to display and null otherwise.
     * @param percentage        the fraction and null otherwise.
     * @param progressIndicator the progress indicator.
     */
    protected void updateProgressIndicator(@Nullable String message,
                                           @Nullable Integer percentage,
                                           @NotNull ProgressIndicator progressIndicator) {
        if (message != null && !message.isBlank()) {
            updateMessage(message, progressIndicator);
        }
        if (percentage != null) {
            progressIndicator.setFraction(percentage.doubleValue() / 100);
        }
    }

    /**
     * Update the given progress indicator text with the given message.
     *
     * @param message           the message.
     * @param progressIndicator the progress indicator.
     */
    protected void updateMessage(@NotNull String message,
                                 @NotNull ProgressIndicator progressIndicator) {
        progressIndicator.setText2(message);
    }

}
