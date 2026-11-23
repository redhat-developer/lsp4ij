/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests the transport of a user-defined language server in its settings page: the page shows the saved transport, and
 * a changed one is saved, in the settings and in the language server definition.
 */
public class LanguageServerViewTransportTest extends BasePlatformTestCase {

    private static final UserDefinedTransport ACCEPT = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0);
    // typescript-language-server talks only over stdio
    private static final String TYPESCRIPT_TEMPLATE = "typescript-language-server";

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
        // The command still takes ${port}, which stdio doesn't give: a warning, which doesn't keep Apply from saving
        view.apply();

        assertSame(UserDefinedTransport.STDIO, savedTransport());
        assertSame(UserDefinedTransport.Mode.STDIO, settings().getTransportMode());
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

    public void testAStartedLanguageServerNeedsItsCommandOnTheSettingsPageToo() {
        view.setCommandLine("");

        assertEquals(LanguageServerBundle.message("new.language.server.dialog.validation.commandLine.must.be.set"), applyError());
        assertEquals("nothing is saved", "node server.js --socket=${port}", definition.getCommandLine());
    }

    public void testTheSettingsPageListsEveryProblem() {
        view.setCommandLine("");
        view.setInitializationOptionsContent("{\"provideFormatter\": ");

        String error = applyError();
        assertTrue(error, error.contains(LanguageServerBundle.message("new.language.server.dialog.validation.commandLine.must.be.set")));
        assertTrue(error, error.contains(LanguageServerBundle.message("language.server.validation.json.initializationOptions")));
    }

    public void testInstancesCanNotShareARunningLanguageServer() {
        view.setWorkspaceFolderStrategyConfiguration("{\"serverInstances\": {}}");
        view.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005));

        assertEquals(LanguageServerBundle.message("language.server.validation.socket.instances.attach"), applyError());
        assertEquals("nothing is saved", ACCEPT, savedTransport());
    }

    public void testAWorkspaceFolderConfigurationWithAValueOfTheWrongTypeIsSavedAsOneWithoutInstances() throws ConfigurationException {
        var running = new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005);
        view.setWorkspaceFolderStrategyConfiguration("{\"markers\": null}");
        view.setTransport(running);

        view.apply();

        assertEquals(running, savedTransport());
    }

    public void testInstancesCanNotShareAFixedPort() {
        view.setWorkspaceFolderStrategyConfiguration("{\"serverInstances\": {}}");
        view.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 4389));

        assertEquals(LanguageServerBundle.message("language.server.validation.socket.instances.port"), applyError());
        assertEquals("nothing is saved", ACCEPT, savedTransport());
    }

    public void testInstancesWithAnAutoPortAreSaved() throws ConfigurationException {
        var connect = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 0);
        view.setWorkspaceFolderStrategyConfiguration("{\"serverInstances\": {}}");
        view.setTransport(connect);

        view.apply();

        assertEquals(connect, savedTransport());
    }

    private @NotNull String applyError() {
        try {
            view.apply();
        } catch (ConfigurationException e) {
            return e.getLocalizedMessage();
        }
        throw new AssertionError("apply() saved settings with problems");
    }

    public void testASavedSocketOfALanguageServerWhoseTemplateListsOnlyStdioIsShownWithAWarningAndSaved()
            throws ConfigurationException {
        var fromTemplate = fromStdioOnlyTemplate("node my-server.js --socket=${port}", ACCEPT);
        var page = new LanguageServerView(fromTemplate, null, false, getProject());
        try {
            page.reset();

            assertEquals(ACCEPT, page.getTransport());
            assertFalse(page.isModified());
            var socket = radio(page, "language.server.transport.socket");
            assertTrue(socket.isSelected());
            assertTrue(socket.isEnabled());
            assertTrue("the hint stays", label(page, "language.server.transport.stdio.only").isVisible());
            var problems = page.validateFields();
            assertEquals(1, problems.size());
            assertEquals(LanguageServerBundle.message("language.server.validation.socket.stdio.only"),
                    problems.get(0).message);
            assertSame(socket, problems.get(0).component);
            assertTrue(problems.get(0).warning);

            // a warning, which doesn't block Apply
            var connect = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 4389);
            page.setTransport(connect);
            page.apply();

            assertEquals(connect, settings(fromTemplate).getTransport());
            assertEquals(connect, fromTemplate.getTransport());
        } finally {
            Disposer.dispose(page);
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), fromTemplate);
        }
    }

    public void testASocketChosenForALanguageServerWhoseTemplateListsOnlyStdioIsAWarningAndSaved()
            throws ConfigurationException {
        var fromTemplate = fromStdioOnlyTemplate("node my-server.js --stdio", UserDefinedTransport.STDIO);
        var page = new LanguageServerView(fromTemplate, null, false, getProject());
        try {
            page.reset();
            assertEmpty(page.validateFields());

            radio(page, "language.server.transport.socket").doClick();

            assertEquals(ACCEPT, page.getTransport());
            String warning = LanguageServerBundle.message("language.server.validation.socket.stdio.only");
            // next to the error of the command, which the template has no socket argument to swap in
            assertEquals(List.of(LanguageServerBundle.message("language.server.validation.socket.port.macro",
                            UserDefinedTransport.PORT_MACRO), warning),
                    page.validateFields().stream().map(problem -> problem.message).toList());
            page.setCommandLine("node my-server.js --socket=${port}");
            assertEquals(List.of(warning), page.validateFields().stream().map(problem -> problem.message).toList());
            page.apply();
            assertEquals(ACCEPT, settings(fromTemplate).getTransport());
        } finally {
            Disposer.dispose(page);
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), fromTemplate);
        }
    }

    public void testASocketOfALanguageServerWithoutATemplateIsNoWarning() {
        assertEquals(ACCEPT, view.getTransport());
        assertEmpty(view.validateFields());
    }

    /**
     * Adds a language server created from the TypeScript template, which lists only stdio, with the given command and
     * transport, and returns it.
     */
    private @NotNull UserDefinedLanguageServerDefinition fromStdioOnlyTemplate(
            @NotNull String command, @NotNull UserDefinedTransport transport) {
        // The bundled templates are read from the jar of LSP4IJ in the test sandbox, which is outside the roots that a
        // test may read when the checkout isn't in the user's home directory
        String templates = PathManager.getResourceRoot(LanguageServerTemplateManager.class, "/templates/lsp");
        assertNotNull(templates);
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), templates);
        var template = LanguageServerTemplateManager.getInstance().findTemplateById(TYPESCRIPT_TEMPLATE);
        assertNotNull(template);
        assertTrue("the template lists only stdio", template.getSupportedTransports().stream()
                .allMatch(support -> support.mode() == UserDefinedTransport.Mode.STDIO));
        var fromTemplate = new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), TYPESCRIPT_TEMPLATE,
                "My TypeScript server", null, null, command, null, Map.of(), false, null, false, null, null, null, null,
                null, null);
        fromTemplate.setTransport(transport);
        LanguageServersRegistry.getInstance().addServerDefinition(getProject(), fromTemplate, List.of());
        return fromTemplate;
    }

    private static @NotNull JBRadioButton radio(@NotNull LanguageServerView page, @NotNull String key) {
        String text = LanguageServerBundle.message(key);
        return UIUtil.findComponentsOfType(page.getComponent(), JBRadioButton.class).stream()
                .filter(button -> text.equals(button.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No radio button " + text));
    }

    private static @NotNull JBLabel label(@NotNull LanguageServerView page, @NotNull String key) {
        String text = LanguageServerBundle.message(key);
        return UIUtil.findComponentsOfType(page.getComponent(), JBLabel.class).stream()
                .filter(label -> text.equals(label.getText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No label " + text));
    }

    private UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings() {
        return settings(definition);
    }

    private static UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings(
            @NotNull UserDefinedLanguageServerDefinition definition) {
        return UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings(definition.getId());
    }
}
