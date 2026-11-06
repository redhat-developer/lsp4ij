/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBRadioButton;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.server.definition.launching.TransportSupport;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import com.redhat.devtools.lsp4ij.settings.ui.CommandLineWidget;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tests the transport in the New Language Server dialog: a template's socket is shown, a language server which runs
 * already needs no command but its port, and the language server it adds has the transport.
 */
public class NewLanguageServerDialogTransportTest extends BasePlatformTestCase {

    private static final UserDefinedTransport RUNNING =
            new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005);

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

    public void testTheTemplateOfALanguageServerWhichTalksOnlyOverStdioDisablesTheSocket() {
        var template = template("server --stdio", UserDefinedTransport.STDIO);
        template.setSupportedTransports(List.of(new TransportSupport(UserDefinedTransport.Mode.STDIO, "--stdio")));
        var dialog = dialog(template);
        try {
            var socket = form(dialog).stream()
                    .filter(component -> component instanceof JBRadioButton button
                            && LanguageServerBundle.message("language.server.transport.socket").equals(button.getText()))
                    .findFirst().orElseThrow();
            assertFalse(socket.isEnabled());
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

    private static @NotNull LanguageServerTemplate template(@NotNull String command, @NotNull UserDefinedTransport transport) {
        var template = new LanguageServerTemplate();
        template.setName("Socket server");
        template.setProgramArgs(Map.of(ServerTemplate.DEFAULT_KEY, command));
        template.setTransport(transport);
        return template;
    }
}
