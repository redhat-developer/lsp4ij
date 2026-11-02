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
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.redhat.devtools.lsp4ij.LanguageServerBundle.BUNDLE;

/**
 * The new language server dialog refuses JSON fields which the language server would fail to parse, naming
 * each of them, and accepts valid and empty ones.
 */
public class NewLanguageServerDialogJsonValidationTest extends BasePlatformTestCase {

    private static final String INVALID = "{\"provideFormatter\": ";

    public void testInvalidFieldIsReported() {
        var errors = validate(template -> template.setInitializationOptions(INVALID));
        assertEquals(1, errors.size());
        assertInvalid("language.server.validation.json.initializationOptions", errors.get(0));
        // the reason says where the content breaks
        assertTrue(errors.get(0).message, errors.get(0).message.contains("line 1"));
    }

    public void testEachInvalidFieldIsReported() {
        var errors = validate(template -> {
            template.setConfiguration(INVALID);
            template.setInitializationOptions(INVALID);
            template.setExperimental(INVALID);
            template.setClientConfiguration(INVALID);
            template.setInstallerConfiguration(INVALID);
        });
        assertEquals(5, errors.size());
        assertInvalid("language.server.validation.json.configuration", errors.get(0));
        assertInvalid("language.server.validation.json.initializationOptions", errors.get(1));
        assertInvalid("language.server.validation.json.experimental", errors.get(2));
        assertInvalid("language.server.validation.json.clientConfiguration", errors.get(3));
        assertInvalid("language.server.validation.json.installer", errors.get(4));
    }

    public void testValidFieldsAreAccepted() {
        assertEmpty(validate(template -> {
            template.setConfiguration("{\"a.b\": true}");
            // the lenient parser which reads the content accepts comments
            template.setInitializationOptions("// options\n{\"provideFormatter\": true}");
            template.setExperimental("[1, 2]");
            template.setClientConfiguration("{\"format\": {\"enabled\": true}}");
            template.setInstallerConfiguration("{\"name\": \"installer\"}");
        }));
    }

    public void testEmptyFieldsAreAccepted() {
        assertEmpty(validate(template -> {
            template.setConfiguration("");
            template.setInitializationOptions("  \n");
            template.setExperimental("");
            template.setClientConfiguration("");
            template.setInstallerConfiguration("");
        }));
    }

    private @NotNull List<ValidationInfo> validate(@NotNull Consumer<LanguageServerTemplate> fields) {
        var template = new LanguageServerTemplate();
        template.setName("Test server");
        template.setProgramArgs(Map.of(ServerTemplate.DEFAULT_KEY, "test-server"));
        fields.accept(template);
        var dialog = new NewLanguageServerDialog(getProject());
        try {
            dialog.loadFromTemplate(template);
            return dialog.doValidateAll();
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    private static void assertInvalid(@NotNull @PropertyKey(resourceBundle = BUNDLE) String fieldKey,
                                      @NotNull ValidationInfo error) {
        String prefix = LanguageServerBundle.message("language.server.validation.json.invalid",
                LanguageServerBundle.message(fieldKey),
                "");
        assertTrue(error.message, error.message.startsWith(prefix));
    }
}
