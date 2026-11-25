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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.CheckedDisposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The field of the GitHub token of the LSP4IJ settings, which the {@code github} downloads of installers send to the
 * GitHub API when neither {@code GITHUB_TOKEN} nor {@code GH_TOKEN} is set (#2188). It's a password field, which never
 * shows the token in clear text, and the token is kept in the Password Safe.
 * <p>
 * The token is read and written in a background thread, one operation after the other, never on the EDT, where the
 * Password Safe of 2026.3 isn't allowed: the field is disabled until the stored token is loaded. A token counts as
 * stored once the Password Safe has it: when the write fails, the field shows why and keeps the token as an unsaved
 * change, which the next apply writes again. The last applied token wins, also over a write which is still running.
 */
final class GitHubTokenField implements Disposable {

    /**
     * The notification group of the error when the settings were closed before the token could be stored.
     */
    static final String NOTIFICATION_GROUP_ID = "LSP4IJ general notifications";

    private static final Logger LOGGER = LoggerFactory.getLogger(GitHubTokenField.class);

    // The last read or write of the Password Safe, after which the next one runs, so that a load after a store reads
    // what was stored
    private static CompletableFuture<?> lastAccess = CompletableFuture.completedFuture(null);

    private final JBPasswordField field = new JBPasswordField();

    // Writes a token to the Password Safe, or removes the stored one for an empty token
    private final @NotNull Consumer<String> store;

    // The validator of the field, which shows why its token wasn't stored, until the settings are closed
    private final CheckedDisposable uiDisposable = Disposer.newCheckedDisposable();
    private final ComponentValidator validator = new ComponentValidator(uiDisposable).installOn(field);

    // The state of the field, read and written on the EDT:
    // the stored token, as the last write which succeeded left it, or null while it's loading;
    private @Nullable String storedToken;
    // the token of the last apply while its write runs, or null if none runs;
    private @Nullable String pendingToken;
    // the number of the last write, and of the last write whose success storedToken has, as writes end in order
    private int lastWrite;
    private int storedWrite;

    GitHubTokenField() {
        this(GitHubTokenSettings::setToken);
    }

    /**
     * Creates the field of the GitHub token, which writes the token with the given store.
     *
     * @param store writes a token to the Password Safe, or removes the stored one for an empty token.
     */
    GitHubTokenField(@NotNull Consumer<String> store) {
        this.store = store;
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
        // The load runs after the writes which run: it gets what they stored, and their ends change nothing
        pendingToken = null;
        storedWrite = lastWrite;
        field.setEnabled(false);
        showError(null);
        return inOrder(GitHubTokenSettings::getToken)
                .thenAcceptAsync(token -> {
                    storedToken = Objects.requireNonNullElse(token, "");
                    field.setText(storedToken);
                    field.setEnabled(true);
                }, edt())
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.warn("Cannot load the GitHub token of the LSP4IJ settings from the Password Safe", error);
                    }
                });
    }

    /**
     * Returns true if the field has another token than the last applied one: the one whose write runs, if any, or else
     * the stored one. A field which is still loading isn't modified.
     *
     * @return true if the field has another token than the last applied one.
     */
    boolean isModified() {
        return storedToken != null && !getToken().equals(pendingToken != null ? pendingToken : storedToken);
    }

    /**
     * Stores the token of the field, in a background thread, or removes the stored token if the field is empty. The
     * token counts as stored once the Password Safe has it. If the write fails, the field keeps it as an unsaved
     * change, and shows why, or, when the settings were closed meanwhile, a notification does.
     *
     * @return the future of the store, which completes on the EDT, with true if the token was stored.
     */
    @NotNull CompletableFuture<Boolean> apply() {
        if (!isModified()) {
            return CompletableFuture.completedFuture(true);
        }
        String token = getToken();
        // The writes run one after the other: this one runs after any which still runs, and its token wins
        int write = ++lastWrite;
        pendingToken = token;
        return GitHubTokenField.<Void>inOrder(() -> {
                    store.accept(token);
                    return null;
                })
                .handleAsync((ignored, error) -> {
                    boolean last = write == lastWrite;
                    if (last) {
                        pendingToken = null;
                    }
                    if (error == null) {
                        // An older write which ends after a newer one, or after a reset, doesn't change what's stored
                        if (write > storedWrite) {
                            storedWrite = write;
                            storedToken = token;
                        }
                        if (last) {
                            showError(null);
                        }
                        return true;
                    }
                    String reason = getReason(error, token);
                    LOGGER.warn("Cannot store the GitHub token of the LSP4IJ settings in the Password Safe: {}", reason);
                    if (!last) {
                        // A newer apply replaces the token which wasn't stored
                        return false;
                    }
                    showError(reason);
                    if (!field.isShowing()) {
                        // As after OK, which closes the settings before the write fails
                        Notifications.Bus.notify(new Notification(NOTIFICATION_GROUP_ID,
                                LanguageServerBundle.message("language.server.github.token.store.failed.title"),
                                StringUtil.escapeXmlEntities(LanguageServerBundle.message(
                                        "language.server.github.token.store.failed.notification", reason)),
                                NotificationType.ERROR));
                    }
                    return false;
                }, edt());
    }

    /**
     * Shows on the field why its token wasn't stored, or no error if the given reason is null.
     *
     * @param reason why the token wasn't stored, or null.
     */
    private void showError(@Nullable String reason) {
        if (!uiDisposable.isDisposed()) {
            validator.updateInfo(reason != null ? new ValidationInfo(
                    LanguageServerBundle.message("language.server.github.token.store.failed", reason), field) : null);
        }
    }

    /**
     * Returns the message of the given error of the Password Safe, without the given token, which it never has.
     */
    private static @NotNull String getReason(@NotNull Throwable error, @NotNull String token) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String message = cause.getMessage() != null && !cause.getMessage().isBlank()
                ? StringUtil.trimEnd(cause.getMessage().strip(), ".")
                : cause.getClass().getSimpleName();
        return token.isEmpty() ? message : message.replace(token, "<redacted>");
    }

    /**
     * Returns the executor of the EDT, in any modality, as the one of the settings dialog.
     */
    private static @NotNull Executor edt() {
        return runnable -> ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
    }

    @Override
    public void dispose() {
        Disposer.dispose(uiDisposable);
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
