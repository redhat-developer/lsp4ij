/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.console.actions.ApplyLanguageServerSettingsAction;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.devtools.lsp4ij.LanguageServerBundle.BUNDLE;

/**
 * The settings view of a language server, which both the settings page and the LSP console apply, saves nothing
 * while a JSON field is not valid JSON: the error names the field, and the saved settings stay as they were.
 */
public class LanguageServerViewJsonValidationTest extends BasePlatformTestCase {

    private static final String INITIALIZATION_OPTIONS = "{\"provideFormatter\": true}";
    private static final String CLIENT_CONFIGURATION = "{\"format\": {\"enabled\": true}}";
    private static final String INVALID = "{\"provideFormatter\": ";

    private UserDefinedLanguageServerDefinition definition;
    private LanguageServerView view;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        definition = new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(),
                null,
                "Test server",
                null,
                null,
                "test-server",
                null,
                Map.of(),
                false,
                null,
                false,
                null,
                INITIALIZATION_OPTIONS,
                null,
                CLIENT_CONFIGURATION,
                null,
                null);
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

    public void testInvalidJsonIsNotSaved() {
        view.setInitializationOptionsContent(INVALID);
        // a valid change applied together with the invalid JSON is not saved either
        view.setServerTrace(ServerTrace.verbose);
        assertInvalid("language.server.validation.json.initializationOptions", applyError());
        assertEquals(INITIALIZATION_OPTIONS, savedInitializationOptions());
        assertNull(ProjectLanguageServerSettings.getInstance(getProject())
                .getLanguageServerSettings(definition.getId()));
    }

    public void testInvalidClientConfigurationIsNotSaved() {
        view.setClientConfigurationContent(INVALID);
        assertInvalid("language.server.validation.json.clientConfiguration", applyError());
        assertEquals(CLIENT_CONFIGURATION, savedClientConfiguration());
    }

    public void testInvalidWorkspaceFoldersConfigurationIsNotSaved() {
        view.setWorkspaceFolderStrategyConfiguration(INVALID);
        assertInvalid("language.server.validation.json.workspaceFolders", applyError());
        assertNull(UserDefinedLanguageServerSettings.getInstance()
                .getUserDefinedLanguageServerSettings(definition.getId())
                .getWorkspaceFolderStrategyConfiguration());
    }

    public void testValidJsonIsSaved() throws ConfigurationException {
        // the lenient parser which reads the content accepts comments
        String initializationOptions = "// options\n{\"provideFormatter\": false}";
        view.setInitializationOptionsContent(initializationOptions);
        view.setClientConfigurationContent("");
        view.apply();
        assertEquals(initializationOptions, savedInitializationOptions());
        assertEquals("", savedClientConfiguration());
    }

    public void testApplyActionShowsTheErrorAndDoesNotSave() {
        view.setInitializationOptionsContent(INVALID);
        List<String> shown = new ArrayList<>();
        TestDialog previous = TestDialogManager.setTestDialog(message -> {
            shown.add(message);
            return Messages.OK;
        });
        try {
            var event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(getProject()));
            new ApplyLanguageServerSettingsAction(view).actionPerformed(event);
        } finally {
            TestDialogManager.setTestDialog(previous);
        }
        assertEquals(1, shown.size());
        assertInvalid("language.server.validation.json.initializationOptions", shown.get(0));
        assertEquals(INITIALIZATION_OPTIONS, savedInitializationOptions());
    }

    private @NotNull String applyError() {
        try {
            view.apply();
        } catch (Exception e) {
            return assertInstanceOf(e, ConfigurationException.class).getLocalizedMessage();
        }
        throw new AssertionError("apply() saved invalid JSON");
    }

    private String savedInitializationOptions() {
        return GlobalLanguageServerSettings.getInstance()
                .getLanguageServerSettings(definition.getId())
                .getInitializationOptionsContent();
    }

    private String savedClientConfiguration() {
        return UserDefinedLanguageServerSettings.getInstance()
                .getUserDefinedLanguageServerSettings(definition.getId())
                .getClientConfigurationContent();
    }

    private static void assertInvalid(@NotNull @PropertyKey(resourceBundle = BUNDLE) String fieldKey,
                                      @NotNull String message) {
        String prefix = LanguageServerBundle.message("language.server.validation.json.invalid",
                LanguageServerBundle.message(fieldKey),
                "");
        assertTrue(message, message.startsWith(prefix));
    }
}
