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
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.installation.download.GitHubTokenSettings;
import com.redhat.devtools.lsp4ij.settings.ui.DescriptionPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * The field of the GitHub token of the LSP4IJ settings, which the {@code github} downloads of installers send to the
 * GitHub API when neither {@code GITHUB_TOKEN} nor {@code GH_TOKEN} is set (#2188). It's a password field, which never
 * shows the token in clear text, and the token is kept in the Password Safe.
 * <p>
 * The token is read and written in a background thread, one operation after the other, never on the EDT, where the
 * Password Safe of 2026.3 isn't allowed: the field is disabled until the stored token is loaded.
 */
final class GitHubTokenField {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubTokenField.class);

    // The last read or write of the Password Safe, after which the next one runs, so that a load after a store reads
    // what was stored
    private static CompletableFuture<?> lastAccess = CompletableFuture.completedFuture(null);

    private final JBPasswordField field = new JBPasswordField();

    // The stored token, or null while it's loading: read and written on the EDT
    private @Nullable String storedToken;

    GitHubTokenField() {
        field.setColumns(30);
        field.setEnabled(false);
    }

    /**
     * Adds the label, the field and its help text to the given form.
     *
     * @param form the form of the settings.
     * @return the form.
     */
    @NotNull FormBuilder addTo(@NotNull FormBuilder form) {
        JBLabel label = new JBLabel(LanguageServerBundle.message("language.server.github.token"));
        label.setLabelFor(field);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(field);
        // A comment of the Kotlin UI DSL, as the description of a language server, since ComponentPanelBuilder is
        // deprecated
        return form.addLabeledComponent(label, row)
                .addComponentToRightColumn(DescriptionPanel.create(
                        LanguageServerBundle.message("language.server.github.token.comment")));
    }

    /**
     * Loads the stored token into the field, in a background thread. The field is disabled until it's loaded.
     *
     * @return the future of the load, which completes on the EDT once the field shows the stored token.
     */
    @NotNull CompletableFuture<Void> reset() {
        storedToken = null;
        field.setEnabled(false);
        Executor edt = runnable -> ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
        return inOrder(GitHubTokenSettings::getToken)
                .thenAcceptAsync(token -> {
                    storedToken = Objects.requireNonNullElse(token, "");
                    field.setText(storedToken);
                    field.setEnabled(true);
                }, edt)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.warn("Cannot load the GitHub token of the LSP4IJ settings from the Password Safe", error);
                    }
                });
    }

    /**
     * Returns true if the field has another token than the stored one. A field which is still loading isn't modified.
     *
     * @return true if the field has another token than the stored one.
     */
    boolean isModified() {
        return storedToken != null && !storedToken.equals(getToken());
    }

    /**
     * Stores the token of the field, in a background thread, or removes the stored token if the field is empty.
     *
     * @return the future of the store.
     */
    @NotNull CompletableFuture<Void> apply() {
        if (!isModified()) {
            return CompletableFuture.completedFuture(null);
        }
        String token = getToken();
        storedToken = token;
        return GitHubTokenField.<Void>inOrder(() -> {
                    GitHubTokenSettings.setToken(token);
                    return null;
                })
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.warn("Cannot store the GitHub token of the LSP4IJ settings in the Password Safe", error);
                    }
                });
    }

    /**
     * Runs the given read or write of the Password Safe in a background thread, after the one before it, whether that
     * one failed or not.
     *
     * @param access the read or write of the Password Safe.
     * @return the future of its result.
     */
    private static synchronized <T> @NotNull CompletableFuture<T> inOrder(@NotNull Supplier<T> access) {
        CompletableFuture<T> next = lastAccess.handleAsync((ignored, error) -> access.get(),
                AppExecutorUtil.getAppExecutorService());
        lastAccess = next;
        return next;
    }

    /**
     * Returns the password field, for the tests.
     */
    @NotNull JBPasswordField getField() {
        return field;
    }

    private @NotNull String getToken() {
        return new String(field.getPassword()).strip();
    }
}
