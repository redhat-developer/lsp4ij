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
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.templates.ServerTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The New Language Server dialog has a help button, which opens the instructions of the template, its README.md, for a
 * template which has some, and only for it (#1935).
 */
public class NewLanguageServerDialogInstructionsTest extends BasePlatformTestCase {

    public void testTheHelpButtonShowsForATemplateWithInstructionsOnly() {
        var dialog = new NewLanguageServerDialog(getProject());
        try {
            assertFalse("Without a template, there are no instructions", dialog.isHelpButtonVisible());

            dialog.loadFromTemplate(template("With instructions", "# Install\n\nRun the installer."));
            assertTrue("A template with a README has instructions", dialog.isHelpButtonVisible());

            dialog.loadFromTemplate(template("Without instructions", null));
            assertFalse("A template without a README has no instructions", dialog.isHelpButtonVisible());
        } finally {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }
    }

    private static @NotNull LanguageServerTemplate template(@NotNull String name, @Nullable String instructions) {
        var template = new LanguageServerTemplate();
        template.setName(name);
        template.setProgramArgs(Map.of(ServerTemplate.DEFAULT_KEY, "server --stdio"));
        template.setDescription(instructions);
        return template;
    }
}
