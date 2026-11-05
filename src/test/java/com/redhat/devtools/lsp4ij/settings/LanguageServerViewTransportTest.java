/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests the transport of a user-defined language server in its settings page: the page shows the saved transport, and
 * a changed one is saved, in the settings and in the language server definition.
 */
public class LanguageServerViewTransportTest extends BasePlatformTestCase {

    private static final UserDefinedTransport ACCEPT = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0);

    private UserDefinedLanguageServerDefinition definition;
    private LanguageServerView view;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        definition = new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), null, "Test server", null,
                null, "node server.js --socket=${port}", null, Map.of(), false, null, false, null, null, null, null,
                null, null);
        definition.setTransport(ACCEPT);
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), definition, List.of());
        view = new LanguageServerView(definition, null, false, getProject());
        view.reset();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            Disposer.dispose(view);
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), definition);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testTheSavedTransportIsShownAndUnmodified() {
        assertEquals(ACCEPT, savedTransport());
        assertEquals(ACCEPT, view.getTransport());
        assertFalse(view.isModified());
    }

    public void testAChangedTransportIsSaved() throws ConfigurationException {
        var running = new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005);
        view.setTransport(running);
        assertTrue(view.isModified());

        view.apply();

        assertEquals(running, savedTransport());
        assertEquals(running, definition.getTransport());
        assertFalse(view.isModified());
    }

    public void testTheStandardInputAndOutputIsSaved() throws ConfigurationException {
        view.setTransport(UserDefinedTransport.STDIO);
        view.apply();

        assertSame(UserDefinedTransport.STDIO, savedTransport());
        assertNull("the settings store no mode for the standard input and output", settings().getTransportMode());
    }

    public void testARunningLanguageServerWithoutItsPortIsNotSaved() {
        view.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 0));

        try {
            view.apply();
            fail("A running language server without its port must not be saved");
        } catch (ConfigurationException e) {
            assertEquals(LanguageServerBundle.message("language.server.validation.socket.port"), e.getLocalizedMessage());
        }
        assertEquals(ACCEPT, savedTransport());
    }

    public void testTheHostIsKeptOnlyForALanguageServerThatRunsAlready() {
        view.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, "127.0.0.1", 4389));

        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 4389), view.getTransport());
    }

    public void testABlankHostIsNoHost() {
        view.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "  ", 6005));

        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 6005), view.getTransport());
    }

    public void testALanguageServerWhichIsNotUserDefinedHasNoTransportToSet() {
        var notUserDefined = new MockLanguageServerDefinition("not-user-defined", false);
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), notUserDefined, List.of());
        var notUserDefinedView = new LanguageServerView(notUserDefined, null, false, getProject());
        try {
            notUserDefinedView.reset();
            notUserDefinedView.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 0));

            // its page has no command, so no transport, which it leaves as the standard input and output
            assertSame(UserDefinedTransport.STDIO, notUserDefinedView.getTransport());
        } finally {
            Disposer.dispose(notUserDefinedView);
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), notUserDefined);
        }
    }

    private UserDefinedTransport savedTransport() {
        return settings().getTransport();
    }

    private UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings() {
        return UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings(definition.getId());
    }
}
