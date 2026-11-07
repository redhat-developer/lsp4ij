/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.UserDefinedLanguageServerSettings;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests the environment variables of a user-defined language server on its settings page: IntelliJ's field drops an
 * entry without {@code =} without a word, so the page counts it as a change and Apply says why it isn't saved.
 */
public class LanguageServerViewEnvironmentVariablesTest extends BasePlatformTestCase {

    private UserDefinedLanguageServerDefinition definition;
    private LanguageServerView view;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        definition = new UserDefinedLanguageServerDefinition(UUID.randomUUID().toString(), null, "Test server", null,
                null, "node server.js --stdio", null, Map.of(), false, null, false, null, null, null, null,
                null, null);
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

    public void testAVariableWithoutAValueIsAChangeWhichApplyRefuses() {
        assertFalse(view.isModified());

        environmentVariables().setText("PATH=/usr/bin;DEBUG");

        assertTrue("Apply is enabled", view.isModified());
        try {
            view.apply();
            fail("apply() saved a variable without a value");
        } catch (ConfigurationException e) {
            assertEquals(LanguageServerBundle.message("language.server.validation.environment.variable", "DEBUG"),
                    e.getLocalizedMessage());
        }
        assertEquals(Map.of(), settings().getUserEnvironmentVariables());
    }

    public void testTheHiddenVariablesOfALanguageServerWhichRunsAlreadyAreNotChecked() throws ConfigurationException {
        environmentVariables().setText("DEBUG");
        var running = new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 6005);

        view.setTransport(running);

        // LSP4IJ doesn't start it, so the field is hidden, and its entry can't block Apply
        view.apply();
        assertEquals(running, settings().getTransport());
    }

    public void testAVariableWithAnEmptyValueIsSaved() throws ConfigurationException {
        environmentVariables().setText("DEBUG=");

        assertTrue(view.isModified());
        view.apply();

        assertEquals(Map.of("DEBUG", ""), settings().getUserEnvironmentVariables());
    }

    private @NotNull EnvironmentVariablesTextFieldWithBrowseButton environmentVariables() {
        return UIUtil.findComponentOfType(view.getComponent(), EnvironmentVariablesTextFieldWithBrowseButton.class);
    }

    private UserDefinedLanguageServerSettings.UserDefinedLanguageServerItemSettings settings() {
        return UserDefinedLanguageServerSettings.getInstance().getUserDefinedLanguageServerSettings(definition.getId());
    }
}
