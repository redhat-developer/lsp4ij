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
package com.redhat.devtools.lsp4ij.installation;

import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * A progress indicator that can also output progress messages
 * (e.g., to a console) instead of just updating the UI.
 *
 * This class extracts and prints meaningful progress messages
 * when they are embedded in HTML-formatted text (e.g., during downloads).
 */
public abstract class PrintableProgressIndicator extends DelegatingProgressIndicator {

    /**
     * Constructs a PrintableProgressIndicator that delegates to an existing ProgressIndicator.
     *
     * @param indicator the base progress indicator to delegate to
     */
    public PrintableProgressIndicator(@NotNull ProgressIndicator indicator) {
        super(indicator);
    }

    /**
     * Overrides the default setText2 behavior to extract and print progress messages.
     *
     * If the text is HTML-formatted with <html><code>...</code></html>,
     * we assume it's a progress update and print it without creating a new line.
     */
    @Override
    public void setText2(@Nls @NlsContexts.ProgressDetails String text) {
        super.setText2(text);

        if (text.startsWith("<html><code>")) {
            // During certain operations (e.g., downloading),
            // IntelliJ sends progress messages wrapped in HTML tags.
            // To avoid flooding the console, we strip the tags and update the current line.
            String progressMessage = text.substring("<html><code>".length(), text.length() - "</code></html>".length());
            printProgress(progressMessage);
        }
    }

    /**
     * Called when a progress message needs to be printed (or updated).
     * Subclasses should implement this to handle message output (e.g., update a console).
     *
     * @param progressMessage the clean progress message without HTML
     */
    protected abstract void printProgress(@NotNull String progressMessage);
}
