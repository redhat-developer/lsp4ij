/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;
import org.jetbrains.annotations.NotNull;

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
            List<ValidationInfo> errors = dialog.doValidateAll();
            assertEquals(1, errors.size());
            assertEquals(LanguageServerBundle.message("language.server.validation.socket.port"), errors.get(0).message);
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    public void testAStartedLanguageServerNeedsItsCommand() {
        var dialog = dialog(template("", new UserDefinedTransport(UserDefinedTransport.Mode.START_AND_ACCEPT, null, 0)));
        try {
            List<ValidationInfo> errors = dialog.doValidateAll();
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
            List<ValidationInfo> errors = dialog.doValidateAll();
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
