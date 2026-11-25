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

import com.intellij.testFramework.TestApplicationManager;
import com.intellij.util.ui.FormBuilder;
import com.redhat.devtools.lsp4ij.installation.download.GitHubTokenSettings;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GitHub token of the LSP4IJ settings, which the installers send to the GitHub API, is kept in the Password Safe,
 * which the test application keeps in memory, and its field on the Language Servers page is a password field (#2188).
 */
public class GitHubTokenFieldTest {

    private static final String TOKEN = "ghp_test0123456789abcdefSETTING";
    private static final String OTHER_TOKEN = "github_pat_test0123456789OTHER";

    private static final int TIMEOUT = 30;

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @After
    public void removeToken() throws Exception {
        runInBackground(() -> GitHubTokenSettings.setToken(null));
    }

    @Test
    public void testTheFieldStoresItsTokenInThePasswordSafeAndLoadsItBack() throws Exception {
        var field = new GitHubTokenField();
        field.addTo(FormBuilder.createFormBuilder());
        await(field.reset(), "the field must load the empty Password Safe");
        assertEquals("", new String(field.getField().getPassword()));
        assertTrue(field.getField().isEnabled(), "the field must be enabled once loaded");

        field.getField().setText(" " + TOKEN + " ");
        assertTrue(field.isModified());
        await(field.apply(), "the field must store its token");

        assertEquals(TOKEN, inBackground(GitHubTokenSettings::getToken), "the Password Safe must have the token, "
                + "without the spaces around it");
        assertFalse(field.isModified(), "the field must have the stored token");

        // As when the settings are opened again
        var reopened = new GitHubTokenField();
        await(reopened.reset(), "the field must load the stored token");
        assertEquals(TOKEN, new String(reopened.getField().getPassword()));
        assertFalse(reopened.isModified());
        assertTrue(reopened.getField().echoCharIsSet(), "the field must not show the token in clear text");
    }

    @Test
    public void testAnEmptyFieldRemovesTheStoredToken() throws Exception {
        runInBackground(() -> GitHubTokenSettings.setToken(OTHER_TOKEN));
        var field = new GitHubTokenField();
        await(field.reset(), "the field must load the stored token");

        field.getField().setText("");
        assertTrue(field.isModified());
        await(field.apply(), "the field must remove the stored token");

        assertNull(inBackground(GitHubTokenSettings::getToken), "the Password Safe must not have the token anymore");
    }

    @Test
    public void testAFieldWhichIsLoadingIsNotModifiedNorStored() throws Exception {
        runInBackground(() -> GitHubTokenSettings.setToken(OTHER_TOKEN));
        var field = new GitHubTokenField();

        // Before reset, as before the settings load it
        assertFalse(field.getField().isEnabled(), "the field must be disabled until the token is loaded");
        assertFalse(field.isModified());
        await(field.apply(), "a field which isn't loaded must store nothing");

        assertEquals(OTHER_TOKEN, inBackground(GitHubTokenSettings::getToken));
    }

    /**
     * Waits for the given future of the field, which completes on the EDT or in the thread of the Password Safe.
     */
    private static void await(CompletableFuture<Void> future, String what) throws Exception {
        try {
            future.get(TIMEOUT, TimeUnit.SECONDS);
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
