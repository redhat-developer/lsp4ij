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
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.TestApplicationManager;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.installation.download.GitHubTokenSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GitHub token of the LSP4IJ settings, which the installers send to the GitHub API, is kept in the Password Safe,
 * which the test application keeps in memory, and its field on the Language Servers page is a password field (#2188).
 * A token which the Password Safe doesn't store stays an unsaved change, and the field says why. The last applied token
 * wins, also over a write which is still running.
 */
public class GitHubTokenFieldTest {

    private static final String TOKEN = "ghp_test0123456789abcdefSETTING";
    private static final String OTHER_TOKEN = "github_pat_test0123456789OTHER";

    private static final int TIMEOUT = 30;

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    // The fields of the test, which their validators register in the Disposer tree
    private final List<GitHubTokenField> fields = new ArrayList<>();

    @After
    public void removeToken() throws Exception {
        fields.forEach(Disposer::dispose);
        runInBackground(() -> GitHubTokenSettings.setToken(null));
    }

    @Test
    public void testTheFieldStoresItsTokenInThePasswordSafeAndLoadsItBack() throws Exception {
        var field = newField(GitHubTokenSettings::setToken);
        field.addTo(FormBuilder.createFormBuilder());
        await(field.reset(), "the field must load the empty Password Safe");
        assertEquals("", new String(field.getField().getPassword()));
        assertTrue(field.getField().isEnabled(), "the field must be enabled once loaded");

        field.getField().setText(" " + TOKEN + " ");
        assertTrue(field.isModified());
        assertTrue(await(field.apply(), "the field must store its token"));

        assertEquals(TOKEN, inBackground(GitHubTokenSettings::getToken), "the Password Safe must have the token, "
                + "without the spaces around it");
        assertFalse(field.isModified(), "the field must have the stored token");
        assertNull(getError(field), "the field must show no error");

        // As when the settings are opened again
        var reopened = newField(GitHubTokenSettings::setToken);
        await(reopened.reset(), "the field must load the stored token");
        assertEquals(TOKEN, new String(reopened.getField().getPassword()));
        assertFalse(reopened.isModified());
        assertTrue(reopened.getField().echoCharIsSet(), "the field must not show the token in clear text");
    }

    @Test
    public void testAnEmptyFieldRemovesTheStoredToken() throws Exception {
        runInBackground(() -> GitHubTokenSettings.setToken(OTHER_TOKEN));
        var field = newField(GitHubTokenSettings::setToken);
        await(field.reset(), "the field must load the stored token");

        field.getField().setText("");
        assertTrue(field.isModified());
        await(field.apply(), "the field must remove the stored token");

        assertNull(inBackground(GitHubTokenSettings::getToken), "the Password Safe must not have the token anymore");
    }

    @Test
    public void testAFieldWhichIsLoadingIsNotModifiedNorStored() throws Exception {
        runInBackground(() -> GitHubTokenSettings.setToken(OTHER_TOKEN));
        var field = newField(GitHubTokenSettings::setToken);

        // Before reset, as before the settings load it
        assertFalse(field.getField().isEnabled(), "the field must be disabled until the token is loaded");
        assertFalse(field.isModified());
        await(field.apply(), "a field which isn't loaded must store nothing");

        assertEquals(OTHER_TOKEN, inBackground(GitHubTokenSettings::getToken));
    }

    @Test
    public void testATokenWhichThePasswordSafeDoesNotStoreStaysAnUnsavedChangeAndTheFieldSaysWhy() throws Exception {
        // A store which fails until the Password Safe is unlocked, with the token in its message
        var locked = new AtomicBoolean(true);
        var field = newField(token -> {
            if (locked.get()) {
                throw new IllegalStateException("Cannot write " + token + ": the Password Safe is locked.");
            }
            GitHubTokenSettings.setToken(token);
        });
        Disposable disposable = Disposer.newDisposable();
        try {
            var notification = new CompletableFuture<Notification>();
            ApplicationManager.getApplication().getMessageBus().connect(disposable)
                    .subscribe(Notifications.TOPIC, new Notifications() {
                        @Override
                        public void notify(@NotNull Notification given) {
                            if (GitHubTokenField.NOTIFICATION_GROUP_ID.equals(given.getGroupId())) {
                                notification.complete(given);
                            }
                        }
                    });
            await(field.reset(), "the field must load the empty Password Safe");
            field.getField().setText(TOKEN);

            assertFalse(await(field.apply(), "the store must fail"), "the token must not count as stored");

            assertTrue(field.isModified(), "a token which wasn't stored must stay an unsaved change");
            assertNull(inBackground(GitHubTokenSettings::getToken));
            assertEquals("Not stored in the Password Safe: Cannot write <redacted>: the Password Safe is locked. Apply "
                    + "again to retry.", getError(field), "the field must say why, without the token");
            // The field isn't showing, as after OK, which closes the settings: a notification says it too
            Notification shown = await(notification, "the notification of the token which wasn't stored");
            assertEquals(NotificationType.ERROR, shown.getType());
            assertEquals("GitHub token not stored", shown.getTitle());
            assertEquals("The GitHub token of the LSP4IJ settings wasn&#39;t stored in the Password Safe: Cannot write "
                    + "&lt;redacted&gt;: the Password Safe is locked", shown.getContent());

            // A later apply tries again
            locked.set(false);
            assertTrue(await(field.apply(), "the store must succeed once the Password Safe is unlocked"));

            assertEquals(TOKEN, inBackground(GitHubTokenSettings::getToken));
            assertFalse(field.isModified(), "the field must have the stored token");
            assertNull(getError(field), "the field must show no error once the token is stored");
        } finally {
            Disposer.dispose(disposable);
        }
    }

    @Test
    public void testAnEmptyFieldAppliedWhileATokenIsBeingStoredRemovesIt() throws Exception {
        // A slow Password Safe: the first write waits until the test lets it go on
        var firstWriteStarted = new CompletableFuture<Void>();
        var releaseFirstWrite = new CompletableFuture<Void>();
        List<String> writes = new CopyOnWriteArrayList<>();
        var field = newField(token -> {
            writes.add(token);
            if (writes.size() == 1) {
                firstWriteStarted.complete(null);
                try {
                    releaseFirstWrite.get(TIMEOUT, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("The test didn't let the first write go on in " + TIMEOUT
                            + " seconds", e);
                }
            }
            GitHubTokenSettings.setToken(token);
        });
        await(field.reset(), "the field must load the empty Password Safe");

        // Apply, as with the Apply button, then clear the field and apply again, as with OK, while the first write runs
        CompletableFuture<Boolean> first;
        CompletableFuture<Boolean> second;
        try {
            field.getField().setText(TOKEN);
            first = field.apply();
            await(firstWriteStarted, "the first write must start");
            field.getField().setText("");
            assertTrue(field.isModified(), "an empty field must differ from the token which is being stored");
            second = field.apply();
        } finally {
            // The writes of all the fields run one after the other: the next test mustn't wait for this one
            releaseFirstWrite.complete(null);
        }

        assertTrue(await(first, "the first write must end"));
        assertTrue(await(second, "the second write must end"));
        assertEquals(List.of(TOKEN, ""), writes, "the empty field must be written after the token");
        assertNull(inBackground(GitHubTokenSettings::getToken), "the last applied value, the empty field, must win");
        assertFalse(field.isModified(), "the field must have the stored value");
        assertNull(getError(field));
    }

    /**
     * Returns a new field of the GitHub token, which writes the token with the given store, and which the test
     * disposes.
     */
    private @NotNull GitHubTokenField newField(@NotNull Consumer<String> store) {
        var field = new GitHubTokenField(store);
        fields.add(field);
        return field;
    }

    /**
     * Returns the error which the field shows, or null if it shows none.
     */
    private static @Nullable String getError(@NotNull GitHubTokenField field) {
        ValidationInfo info = ComponentValidator.getInstance(field.getField())
                .map(ComponentValidator::getValidationInfo)
                .orElse(null);
        return info != null ? info.message : null;
    }

    /**
     * Waits for the given future of the field, which completes on the EDT or in the thread of the Password Safe.
     */
    private static <T> T await(CompletableFuture<T> future, String what) throws Exception {
        try {
            return future.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("Not done in " + TIMEOUT + " seconds: " + what, e);
        }
    }

    /**
     * Reads or writes the Password Safe in a background thread, as LSP4IJ does.
     */
    private static <T> T inBackground(Callable<T> action) throws Exception {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return action.call();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).get(TIMEOUT, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("The Password Safe didn't answer in " + TIMEOUT + " seconds", e);
        } catch (ExecutionException e) {
            throw new AssertionError("The Password Safe failed", e.getCause());
        }
    }

    private static void runInBackground(Runnable action) throws Exception {
        inBackground(() -> {
            action.run();
            return null;
        });
    }
}
