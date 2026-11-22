/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The language server which the New Language Server dialog adds from a template has what the template gives, also
 * what the dialog doesn't show: the working directory and the workspace folders.
 */
public class NewLanguageServerDialogTemplateTest extends BasePlatformTestCase {

    private static final String NAME = "Template server";

    public void testTheAddedLanguageServerHasTheWorkingDirectoryOfTheTemplate() {
        var added = add(template -> template.setWorkingDir("$USER_HOME$/.lsp4ij/server"));
        try {
            assertEquals("$USER_HOME$/.lsp4ij/server", added.getWorkingDir());
        } finally {
            remove(added);
        }
    }

    public void testATemplateLoadedAfterAnotherGetsNoWorkingDirectoryOfTheOther() {
        var dialog = new NewLanguageServerDialog(getProject());
        dialog.loadFromTemplate(template(template -> template.setWorkingDir("$USER_HOME$/.lsp4ij/server")));
        dialog.loadFromTemplate(template(template -> template.setWorkingDir(null)));
        var added = add(dialog);
        try {
            assertEquals("", added.getWorkingDir());
        } finally {
            remove(added);
        }
    }

    public void testTheAddedLanguageServerHasTheWorkspaceFoldersOfTheTemplate() {
        // as a template's workspaceFolderSettings.json reads, and the Workspace Folders tab of the settings keeps it
        var added = add(template -> template.setWorkspaceFolderConfiguration("{\n  \"rootType\": \"SOURCE_ROOTS\"\n}\n"));
        try {
            String configuration = "{\n  \"rootType\": \"SOURCE_ROOTS\"\n}";
            assertEquals(configuration, added.getWorkspaceFolderStrategyConfiguration());
            assertEquals(configuration, UserDefinedLanguageServerSettings.getInstance()
                    .getUserDefinedLanguageServerSettings(added.getId())
                    .getWorkspaceFolderStrategyConfiguration());
        } finally {
            remove(added);
        }
    }

    public void testATemplateWithoutWorkspaceFoldersAddsNone() {
        // an exported language server without a workspace folder configuration writes {}
        for (String none : new String[]{null, "", "{}"}) {
            var added = add(template -> template.setWorkspaceFolderConfiguration(none));
            try {
                assertEquals("as before", "", added.getWorkspaceFolderStrategyConfiguration());
            } finally {
                remove(added);
            }
        }
    }

    public void testInstancesOfTheTemplateCanNotShareARunningLanguageServer() {
        var dialog = new NewLanguageServerDialog(getProject());
        try {
            dialog.loadFromTemplate(template(template -> {
                template.setWorkspaceFolderConfiguration("{\"serverInstances\": {}}");
                template.setTransport(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005));
            }));

            String error = LanguageServerBundle.message("language.server.validation.socket.instances.attach");
            assertTrue(dialog.doValidateAll().stream().anyMatch(problem -> error.equals(problem.message)));
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    private @NotNull UserDefinedLanguageServerDefinition add(@NotNull Consumer<LanguageServerTemplate> templateSetup) {
        var dialog = new NewLanguageServerDialog(getProject());
        dialog.loadFromTemplate(template(templateSetup));
        return add(dialog);
    }

    /**
     * Presses OK, and returns the language server which the dialog adds.
     */
    private static @NotNull UserDefinedLanguageServerDefinition add(@NotNull NewLanguageServerDialog dialog) {
        dialog.doOKAction();
        return LanguageServersRegistry.getInstance().getServerDefinitions().stream()
                .filter(definition -> definition instanceof UserDefinedLanguageServerDefinition
                        && NAME.equals(definition.getDisplayName()))
                .map(UserDefinedLanguageServerDefinition.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private void remove(@NotNull UserDefinedLanguageServerDefinition added) {
        LanguageServersRegistry.getInstance().removeServerDefinition(getProject(), added);
    }

    private static @NotNull LanguageServerTemplate template(@NotNull Consumer<LanguageServerTemplate> setup) {
        var template = new LanguageServerTemplate();
        template.setName(NAME);
        template.setProgramArgs(Map.of(ServerTemplate.DEFAULT_KEY, "server --stdio"));
        setup.accept(template);
        return template;
    }
}
