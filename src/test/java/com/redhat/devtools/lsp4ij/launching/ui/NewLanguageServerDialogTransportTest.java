/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplateManager;
import com.redhat.devtools.lsp4ij.server.definition.launching.TransportSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import com.redhat.devtools.lsp4ij.settings.ui.CommandLineWidget;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

/**
 * Tests the transport in the New Language Server dialog: a template's socket is shown, a language server which runs
 * already needs no command but its port, the language server it adds has the transport, and an imported export of a
 * language server has the transports of the template which the language server was created from.
 */
public class NewLanguageServerDialogTransportTest extends BasePlatformTestCase {

    private static final UserDefinedTransport RUNNING =
            new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005);
    // The transports of the CSS template, and of the LemMinX one, whose arguments are the launcher class
    private static final List<TransportSupport> CSS = List.of(
            new TransportSupport(UserDefinedTransport.Mode.STDIO, "--stdio"),
            new TransportSupport(UserDefinedTransport.Mode.START_AND_ACCEPT, "--socket=${port}"));
    private static final List<TransportSupport> LEMMINX = List.of(
            new TransportSupport(UserDefinedTransport.Mode.STDIO, "org.eclipse.lemminx.XMLServerLauncher"),
            new TransportSupport(UserDefinedTransport.Mode.START_AND_CONNECT,
                    "org.eclipse.lemminx.XMLServerSocketLauncher --port ${port}"));
    // The transports of the TypeScript template
    private static final List<TransportSupport> STDIO_ONLY = List.of(
            new TransportSupport(UserDefinedTransport.Mode.STDIO, "--stdio"));
    // The transports of a language server which talks only over a socket, which it listens on or connects to
    private static final List<TransportSupport> SOCKET_ONLY = List.of(
            new TransportSupport(UserDefinedTransport.Mode.START_AND_CONNECT, "--port ${port}"),
            new TransportSupport(UserDefinedTransport.Mode.START_AND_ACCEPT, "--socket=${port}"));

    public void testTheTransportWhichTheUserChoosesSwapsItsArgumentInTheCommand() {
        var dialog = dialog(template("node css.js --stdio", UserDefinedTransport.STDIO, CSS));
        try {
            click(dialog, "language.server.transport.socket");
            assertEquals("node css.js --socket=${port}", command(dialog));
            // a language server which runs already has no argument: the command waits as it is
            click(dialog, "language.server.socket.attach");
            assertEquals("node css.js --socket=${port}", command(dialog));
            click(dialog, "language.server.transport.stdio");
            assertEquals("node css.js --stdio", command(dialog));
            // the swapped command takes no port, so stdio has nothing to warn of
            assertEmpty(dialog.doValidateAll());
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testASocketStartsInTheModeOfTheTemplateWithItsArgument() {
        var dialog = dialog(template("java -classpath lemminx.jar org.eclipse.lemminx.XMLServerLauncher",
                UserDefinedTransport.STDIO, LEMMINX));
        try {
            click(dialog, "language.server.transport.socket");
            assertTrue("the mode which the template lists", radio(dialog, "language.server.socket.startAndConnect").isSelected());
            assertEquals("java -classpath lemminx.jar org.eclipse.lemminx.XMLServerSocketLauncher --port ${port}",
                    command(dialog));
            click(dialog, "language.server.transport.stdio");
            assertEquals("java -classpath lemminx.jar org.eclipse.lemminx.XMLServerLauncher", command(dialog));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testATemplateLoadsWithItsCommandAsItIs() {
        // a command without the argument of the transport, as a wrapper script has
        var dialog = dialog(template("node css.js --node-ipc", UserDefinedTransport.STDIO, CSS));
        try {
            assertEquals("node css.js --node-ipc", command(dialog));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testATemplateLoadedAfterAnotherGetsNoArgumentOfTheOther() {
        var dialog = dialog(template("java -classpath lemminx.jar org.eclipse.lemminx.XMLServerLauncher",
                UserDefinedTransport.STDIO, LEMMINX));
        try {
            dialog.loadFromTemplate(template("node css.js --stdio", UserDefinedTransport.STDIO, CSS));
            assertEquals("node css.js --stdio", command(dialog));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testTheArgumentIsSwappedAsAWholeWordAtItsLastPlace() {
        var dialog = dialog(template("node /opt/--stdio/css.js --stdio", UserDefinedTransport.STDIO, CSS));
        try {
            click(dialog, "language.server.transport.socket");
            assertEquals("node /opt/--stdio/css.js --socket=${port}", command(dialog));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testTheArgumentWhichComesLastInTheCommandIsSwapped() {
        // a command with the arguments of both transports, whichever is longer
        var dialog = dialog(template("node css.js --socket=${port} --stdio", UserDefinedTransport.STDIO, CSS));
        try {
            click(dialog, "language.server.transport.socket");
            assertEquals("node css.js --socket=${port} --socket=${port}", command(dialog));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testARunningLanguageServerNeedsNoCommand() {
        var dialog = dialog(template("", RUNNING));
        try {
            assertEmpty(dialog.doValidateAll());
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testARunningLanguageServerNeedsItsPort() {
        var dialog = dialog(template("", new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 0)));
        try {
            List<ValidationInfo> errors = fieldProblems(dialog);
            assertEquals(1, errors.size());
            assertEquals(LanguageServerBundle.message("language.server.validation.socket.port"), errors.get(0).message);
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testAStartedLanguageServerNeedsItsCommand() {
        var dialog = dialog(template("", new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0)));
        try {
            List<ValidationInfo> errors = fieldProblems(dialog);
            assertEquals(1, errors.size());
            assertEquals(LanguageServerBundle.message("new.language.server.dialog.validation.commandLine.must.be.set"),
                    errors.get(0).message);
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testTheCommandOfALanguageServerOnAFreePortTakesThePort() {
        var transport = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0);
        var dialog = dialog(template("node server.js", transport));
        try {
            List<ValidationInfo> errors = fieldProblems(dialog);
            assertEquals(1, errors.size());
            assertEquals(LanguageServerBundle.message("language.server.validation.socket.port.macro",
                    UserDefinedTransport.PORT_MACRO), errors.get(0).message);
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }

        var takesThePort = dialog(template("node server.js --socket=${port}", transport));
        try {
            assertEmpty(takesThePort.doValidateAll());
        } finally {
            takesThePort.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testTheTransportComesFirstAndARunningLanguageServerHidesTheFieldsItDoesNotUse() {
        var dialog = dialog(template("node server.js --stdio", RUNNING));
        try {
            List<Component> form = form(dialog);
            var command = (CommandLineWidget) form.stream().filter(CommandLineWidget.class::isInstance).findFirst().orElseThrow();
            var environment = form.stream().filter(EnvironmentVariablesComponent.class::isInstance).findFirst().orElseThrow();
            var stdio = (JBRadioButton) form.stream()
                    .filter(component -> component instanceof JBRadioButton button
                            && LanguageServerBundle.message("language.server.transport.stdio").equals(button.getText()))
                    .findFirst().orElseThrow();
            assertTrue("the transport decides which fields follow it", form.indexOf(stdio) < form.indexOf(command));

            var commandPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, command);
            assertFalse(commandPane.isVisible());
            assertFalse(environment.isVisible());

            stdio.doClick();
            assertTrue(commandPane.isVisible());
            assertTrue(environment.isVisible());
            assertEquals("the hidden command was kept", "node server.js --stdio", command.getText());
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testASocketForATemplateWhichListsOnlyStdioIsAWarningWhichDoesNotBlockOk() {
        var dialog = dialog(template("server --stdio", UserDefinedTransport.STDIO, STDIO_ONLY));
        try {
            var socket = radio(dialog, "language.server.transport.socket");
            assertTrue("the template may be the start of a language server which does talk over a socket",
                    socket.isEnabled());
            assertEmpty(dialog.doValidateAll());

            click(dialog, "language.server.transport.socket");
            String message = LanguageServerBundle.message("language.server.validation.socket.stdio.only");
            List<ValidationInfo> warnings = dialog.doValidateAll().stream()
                    .filter(problem -> message.equals(problem.message))
                    .toList();
            // On the socket, and listed below the fields, next to the error of the command, which has no ${port}
            assertEquals(2, warnings.size());
            assertSame(socket, warnings.get(0).component);
            assertNull(warnings.get(1).component);
            assertTrue(warnings.stream().allMatch(problem -> problem.warning && problem.okEnabled));

            commandLine(dialog).setText("server --socket=${port}");
            List<ValidationInfo> problems = dialog.doValidateAll();
            assertEquals(List.of(message, message), problems.stream().map(problem -> problem.message).toList());
            assertTrue("OK stays enabled", problems.stream().allMatch(problem -> problem.warning && problem.okEnabled));

            click(dialog, "language.server.transport.stdio");
            assertTrue(dialog.doValidateAll().stream().noneMatch(problem -> message.equals(problem.message)));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testASocketForATemplateWhichListsASocketModeOrNoTransportsIsNoWarning() {
        var listsASocket = dialog(template("node css.js --stdio", UserDefinedTransport.STDIO, CSS));
        try {
            click(listsASocket, "language.server.transport.socket");
            assertEmpty(listsASocket.doValidateAll());
        } finally {
            listsASocket.close(DialogWrapper.CANCEL_EXIT_CODE);
        }

        var listsNone = dialog(template("node server.js --socket=${port}", UserDefinedTransport.STDIO));
        try {
            click(listsNone, "language.server.transport.socket");
            assertEmpty(listsNone.doValidateAll());
        } finally {
            listsNone.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testTheSocketOfATemplateWhichListsOnlyStdioIsKeptWithAWarning() {
        // a template whose socket isn't one of its transports, which LSP4IJ reports when it loads the template
        var dialog = dialog(template("", RUNNING, STDIO_ONLY));
        try {
            assertTrue(radio(dialog, "language.server.transport.socket").isSelected());
            assertTrue(radio(dialog, "language.server.socket.attach").isSelected());
            String message = LanguageServerBundle.message("language.server.validation.socket.stdio.only");
            List<ValidationInfo> problems = dialog.doValidateAll();
            assertEquals(List.of(message, message), problems.stream().map(problem -> problem.message).toList());
            assertTrue(problems.stream().allMatch(problem -> problem.warning && problem.okEnabled));

            // Socket can be chosen again after stdio
            click(dialog, "language.server.transport.stdio");
            click(dialog, "language.server.transport.socket");
            assertTrue(radio(dialog, "language.server.transport.socket").isSelected());
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testATemplateWhichListsOnlySocketModesStartsInTheFirstOfThem() {
        var dialog = dialog(template("server --port ${port}", UserDefinedTransport.STDIO, SOCKET_ONLY));
        try {
            assertTrue(radio(dialog, "language.server.transport.socket").isSelected());
            assertTrue(radio(dialog, "language.server.socket.startAndConnect").isSelected());
            assertTrue("LSP4IJ chooses the port", radio(dialog, "language.server.socket.port.auto").isSelected());
            assertEquals("the command is the template's", "server --port ${port}", command(dialog));
            assertEmpty(dialog.doValidateAll());
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }

        // The template's own socket stays, and a template which lists stdio too starts with it, as before
        var withASocket = dialog(template("", RUNNING, List.of(
                new TransportSupport(UserDefinedTransport.Mode.START_AND_CONNECT, "--port ${port}"),
                new TransportSupport(UserDefinedTransport.Mode.ATTACH, null))));
        try {
            assertTrue(radio(withASocket, "language.server.socket.attach").isSelected());
        } finally {
            withASocket.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
        var alsoStdio = dialog(template("node css.js --stdio", UserDefinedTransport.STDIO, CSS));
        try {
            assertTrue(radio(alsoStdio, "language.server.transport.stdio").isSelected());
        } finally {
            alsoStdio.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testTheLanguageServerAddedFromATemplateWhichListsOnlySocketModesHasTheSocket() {
        var dialog = dialog(template("server --port ${port}", UserDefinedTransport.STDIO, SOCKET_ONLY));
        dialog.doOKAction();

        var added = LanguageServersRegistry.getInstance().getServerDefinitions().stream()
                .filter(definition -> definition instanceof UserDefinedLanguageServerDefinition
                        && "Socket server".equals(definition.getDisplayName()))
                .map(UserDefinedLanguageServerDefinition.class::cast)
                .findFirst()
                .orElseThrow();
        try {
            assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_CONNECT, null, 0), added.getTransport());
        } finally {
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), added);
        }
    }

    public void testStdioForATemplateWhichListsOnlySocketModesIsAWarningWhichDoesNotBlockOk() {
        var dialog = dialog(template("server --port ${port}", UserDefinedTransport.STDIO, SOCKET_ONLY));
        try {
            var stdio = radio(dialog, "language.server.transport.stdio");
            assertTrue("the template may be the start of a language server which does talk over stdio",
                    stdio.isEnabled());

            click(dialog, "language.server.transport.stdio");
            String message = LanguageServerBundle.message("language.server.validation.stdio.socket.only");
            List<ValidationInfo> problems = dialog.doValidateAll();
            // On stdio, and listed below the fields, next to the warning of the command, which still takes ${port}, as
            // the template has no argument for stdio to swap in
            String portWarning = LanguageServerBundle.message("language.server.validation.stdio.port.macro",
                    UserDefinedTransport.PORT_MACRO);
            assertEquals(List.of(portWarning, message, portWarning, message),
                    problems.stream().map(problem -> problem.message).toList());
            assertSame(stdio, problems.get(1).component);
            assertNull(problems.get(3).component);
            assertTrue("OK stays enabled", problems.stream().allMatch(problem -> problem.warning && problem.okEnabled));

            click(dialog, "language.server.transport.socket");
            assertEmpty(dialog.doValidateAll());
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testAStdioCommandWhichTakesThePortIsAWarningWhichDoesNotBlockOk() {
        var dialog = dialog(template("node server.js --socket=${port}", UserDefinedTransport.STDIO));
        try {
            String message = LanguageServerBundle.message("language.server.validation.stdio.port.macro",
                    UserDefinedTransport.PORT_MACRO);
            List<ValidationInfo> problems = dialog.doValidateAll();

            // On the command, and listed below the fields
            assertEquals(List.of(message, message), problems.stream().map(problem -> problem.message).toList());
            assertTrue(problems.get(0).component instanceof CommandLineWidget);
            assertTrue("warnings, which don't block OK", problems.stream().allMatch(problem -> problem.warning && problem.okEnabled));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testWhatTheTemplateGivesWrongIsAWarningBelowTheFieldsWhichDoesNotBlockOk() {
        var template = template("server --stdio", UserDefinedTransport.STDIO);
        template.setProblems(List.of(LanguageServerBundle.message("language.server.template.socket.mode", "atach")));
        var dialog = dialog(template);
        try {
            var warnings = dialog.doValidateAll().stream().filter(problem -> problem.warning).toList();

            assertEquals(1, warnings.size());
            assertEquals(LanguageServerBundle.message("language.server.template.socket.mode", "atach"), warnings.get(0).message);
            assertNull("listed below the fields", warnings.get(0).component);
            assertTrue(warnings.get(0).okEnabled);
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testEveryProblemIsAlsoListedBelowTheFields() {
        var dialog = dialog(template("", new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0)));
        try {
            ((JTextField) dialog.getPreferredFocusedComponent()).setText("");

            List<ValidationInfo> problems = dialog.doValidateAll();
            var onFields = problems.stream().filter(problem -> problem.component != null).map(problem -> problem.message).toList();
            var listed = problems.stream().filter(problem -> problem.component == null).map(problem -> problem.message).toList();
            assertEquals(List.of(LanguageServerBundle.message("new.language.server.dialog.validation.serverName.must.be.set"),
                    LanguageServerBundle.message("new.language.server.dialog.validation.commandLine.must.be.set")), onFields);
            assertEquals(onFields, listed);
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testTheAddedLanguageServerHasTheTransport() {
        var dialog = dialog(template("", RUNNING));
        dialog.doOKAction();

        var added = LanguageServersRegistry.getInstance().getServerDefinitions().stream()
                .filter(definition -> definition instanceof UserDefinedLanguageServerDefinition
                        && "Socket server".equals(definition.getDisplayName()))
                .map(UserDefinedLanguageServerDefinition.class::cast)
                .findFirst()
                .orElseThrow();
        try {
            assertEquals(RUNNING, added.getTransport());
        } finally {
            LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), added);
        }
    }

    public void testAnImportedExportOfALanguageServerFromATemplateWhichListsOnlyStdioWarnsOfASocket() throws IOException {
        var exported = export(definition("typescript-language-server", "typescript-language-server --stdio",
                UserDefinedTransport.STDIO));
        var template = importTemplate(exported);
        assertEquals("the transports of the TypeScript template", STDIO_ONLY, template.getSupportedTransports());
        assertEmpty(template.getProblems());

        var dialog = dialog(template);
        try {
            assertTrue(label(dialog, "language.server.transport.stdio.only").isVisible());
            var socket = radio(dialog, "language.server.transport.socket");
            assertTrue(socket.isEnabled());
            assertEmpty(dialog.doValidateAll());

            click(dialog, "language.server.transport.socket");
            String message = LanguageServerBundle.message("language.server.validation.socket.stdio.only");
            List<ValidationInfo> warnings = dialog.doValidateAll().stream()
                    .filter(problem -> message.equals(problem.message))
                    .toList();
            assertEquals(2, warnings.size());
            assertSame(socket, warnings.get(0).component);
            assertTrue(warnings.stream().allMatch(problem -> problem.warning && problem.okEnabled));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testAnImportedExportOfALanguageServerFromATemplateWithASocketModeHasItsHintAndArgument() throws IOException {
        var template = importTemplate(export(definition("vscode-css-language-server", "node css.js --stdio",
                UserDefinedTransport.STDIO)));
        assertEquals("the transports of the CSS template", CSS, template.getSupportedTransports());

        var dialog = dialog(template);
        try {
            assertFalse(label(dialog, "language.server.transport.stdio.only").isVisible());

            click(dialog, "language.server.transport.socket");

            assertTrue(labelWithText(dialog, LanguageServerBundle.message("language.server.socket.template.hint",
                    "--socket=${port}", LanguageServerBundle.message("language.server.socket.startAndAccept"))).isVisible());
            assertEquals("node css.js --socket=${port}", command(dialog));
            assertEmpty(dialog.doValidateAll());
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testAnImportedExportOfALanguageServerOverASocketFromATemplateWhichListsOnlyStdioKeepsTheSocket() throws IOException {
        var accept = new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0);
        var template = importTemplate(export(definition("typescript-language-server", "my-server --socket=${port}",
                accept)));
        assertEquals(accept, template.getTransport());
        assertEquals(STDIO_ONLY, template.getSupportedTransports());
        // the export is a template whose socket isn't one of its transports, which LSP4IJ reports when it loads it
        assertEquals(List.of(LanguageServerBundle.message("language.server.template.socket.mode.not.listed",
                "startAndAccept")), template.getProblems());

        var dialog = dialog(template);
        try {
            assertTrue(radio(dialog, "language.server.transport.socket").isSelected());
            String message = LanguageServerBundle.message("language.server.validation.socket.stdio.only");
            List<ValidationInfo> problems = dialog.doValidateAll();
            assertEquals(2, problems.stream().filter(problem -> message.equals(problem.message)).count());
            assertTrue(problems.stream().allMatch(problem -> problem.warning && problem.okEnabled));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testAnExportOfALanguageServerWithoutATemplateHasNoTransports() throws IOException {
        assertExportsNoTransports(definition(null, "server --stdio", UserDefinedTransport.STDIO));
    }

    public void testAnExportOfALanguageServerWhoseTemplateIsGoneHasNoTransports() throws IOException {
        assertExportsNoTransports(definition("a-template-which-lsp4ij-does-not-have", "server --stdio",
                UserDefinedTransport.STDIO));
    }

    private void assertExportsNoTransports(@NotNull UserDefinedLanguageServerDefinition definition) throws IOException {
        var exported = export(definition);
        assertFalse(exportedTemplateJson(exported).has("transports"));
        var template = importTemplate(exported);
        assertEmpty(template.getSupportedTransports());

        var dialog = dialog(template);
        try {
            assertFalse(label(dialog, "language.server.transport.stdio.only").isVisible());
            click(dialog, "language.server.transport.socket");
            String message = LanguageServerBundle.message("language.server.validation.socket.stdio.only");
            assertTrue(dialog.doValidateAll().stream().noneMatch(problem -> message.equals(problem.message)));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    /**
     * Exports the given language server, as the LSP console does, and returns its directory in the zip, unzipped.
     */
    private @NotNull VirtualFile export(@NotNull UserDefinedLanguageServerDefinition definition) throws IOException {
        var tempDir = myFixture.getTempDirFixture();
        var zip = tempDir.createFile("export.zip");
        assertEquals(1, templateManager().exportLsTemplates(zip, List.of(definition)));
        try (var entries = new ZipInputStream(new ByteArrayInputStream(zip.contentsToByteArray()))) {
            for (var entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                tempDir.createFile("unzipped/" + entry.getName(), new String(entries.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        var exported = tempDir.getFile("unzipped/" + definition.getDisplayName());
        assertNotNull(exported);
        return exported;
    }

    /**
     * Imports the given directory as a custom template, as "Import template..." does.
     */
    private @NotNull LanguageServerTemplate importTemplate(@NotNull VirtualFile directory) throws IOException {
        var template = templateManager().importServerTemplate(directory);
        assertNotNull(template);
        return template;
    }

    /**
     * Returns the manager of the templates, which reads the bundled templates from the jar of LSP4IJ in the test
     * sandbox: outside the roots that a test may read when the checkout isn't in the user's home directory.
     */
    private @NotNull LanguageServerTemplateManager templateManager() {
        String templates = PathManager.getResourceRoot(LanguageServerTemplateManager.class, "/templates/lsp");
        assertNotNull(templates);
        VfsRootAccess.allowRootAccess(getTestRootDisposable(), templates);
        return LanguageServerTemplateManager.getInstance();
    }

    private static @NotNull JsonObject exportedTemplateJson(@NotNull VirtualFile directory) throws IOException {
        var templateJson = directory.findChild(ServerTemplate.TEMPLATE_FILE_NAME);
        assertNotNull(templateJson);
        return JsonParser.parseString(VfsUtilCore.loadText(templateJson)).getAsJsonObject();
    }

    private static @NotNull UserDefinedLanguageServerDefinition definition(@Nullable String templateId,
                                                                           @NotNull String command,
                                                                           @NotNull UserDefinedTransport transport) {
        var definition = new UserDefinedLanguageServerDefinition("exported", templateId, "Exported", null, null,
                command, null, Map.of(), false, "", false, "", "", "", "", "", "");
        definition.setTransport(transport);
        return definition;
    }

    /**
     * Returns the problems the dialog shows on its fields, without the list of them below the fields.
     */
    private static @NotNull List<ValidationInfo> fieldProblems(@NotNull NewLanguageServerDialog dialog) {
        return dialog.doValidateAll().stream().filter(problem -> problem.component != null).toList();
    }

    /**
     * Returns the components of the dialog's form. The server name field is in it, and without a window, the top of
     * the form is the topmost parent of that field.
     */
    private static @NotNull List<Component> form(@NotNull NewLanguageServerDialog dialog) {
        Component top = dialog.getPreferredFocusedComponent();
        while (top.getParent() != null) {
            top = top.getParent();
        }
        return components(top);
    }

    /**
     * Returns the given component and all those inside it, each before those inside it, in the order they were added.
     */
    private static @NotNull List<Component> components(@NotNull Component component) {
        var components = new ArrayList<Component>();
        components.add(component);
        if (component instanceof Container container) {
            for (var child : container.getComponents()) {
                components.addAll(components(child));
            }
        }
        return components;
    }

    private @NotNull NewLanguageServerDialog dialog(@NotNull LanguageServerTemplate template) {
        var dialog = new NewLanguageServerDialog(getProject());
        dialog.loadFromTemplate(template);
        return dialog;
    }

    private static @NotNull LanguageServerTemplate template(@NotNull String command,
                                                            @NotNull UserDefinedTransport transport,
                                                            @NotNull List<TransportSupport> supportedTransports) {
        var template = template(command, transport);
        template.setSupportedTransports(supportedTransports);
        return template;
    }

    /**
     * Clicks the radio button of the dialog with the text of the given key, as the user does.
     */
    private static void click(@NotNull NewLanguageServerDialog dialog, @NotNull String key) {
        radio(dialog, key).doClick();
    }

    private static @NotNull JBRadioButton radio(@NotNull NewLanguageServerDialog dialog, @NotNull String key) {
        return (JBRadioButton) form(dialog).stream()
                .filter(component -> component instanceof JBRadioButton radio
                        && LanguageServerBundle.message(key).equals(radio.getText()))
                .findFirst().orElseThrow();
    }

    private static @NotNull JBLabel label(@NotNull NewLanguageServerDialog dialog, @NotNull String key) {
        return labelWithText(dialog, LanguageServerBundle.message(key));
    }

    private static @NotNull JBLabel labelWithText(@NotNull NewLanguageServerDialog dialog, @NotNull String text) {
        return (JBLabel) form(dialog).stream()
                .filter(component -> component instanceof JBLabel label && text.equals(label.getText()))
                .findFirst().orElseThrow();
    }

    private static @NotNull String command(@NotNull NewLanguageServerDialog dialog) {
        return commandLine(dialog).getText();
    }

    private static @NotNull CommandLineWidget commandLine(@NotNull NewLanguageServerDialog dialog) {
        return (CommandLineWidget) form(dialog).stream().filter(CommandLineWidget.class::isInstance).findFirst()
                .orElseThrow();
    }

    private static @NotNull LanguageServerTemplate template(@NotNull String command, @NotNull UserDefinedTransport transport) {
        var template = new LanguageServerTemplate();
        template.setName("Socket server");
        template.setProgramArgs(Map.of(ServerTemplate.DEFAULT_KEY, command));
        template.setTransport(transport);
        return template;
    }
}
