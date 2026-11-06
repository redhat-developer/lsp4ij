/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.settings;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ui.UIUtil;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tests the working directory of a user-defined language server, in the panel which its settings page and the New
 * Language Server dialog share: the line below the field says when the directory doesn't exist, in which the language
 * server can't start, without refusing it, since the installer can still create it.
 */
public class LanguageServerViewWorkingDirectoryTest extends BasePlatformTestCase {

    private UserDefinedLanguageServerDefinition definition;
    private LanguageServerView view;
    private Path directory;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        directory = Files.createTempDirectory("lsp4ij-working-directory");
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
            Files.deleteIfExists(directory);
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testADirectoryWhichDoesNotExistIsTold() {
        String missing = directory.resolve("missing").toString();

        view.setWorkingDir(missing);

        assertEquals(LanguageServerBundle.message("language.server.workingDir.missing", missing), preview().getText());
    }

    public void testADirectoryWhichExistsIsNotTold() {
        view.setWorkingDir(directory.toString());

        assertEquals("", preview().getText());
    }

    public void testAMacroIsResolvedBeforeTheDirectoryIsChecked() {
        String name = "lsp4ij-" + UUID.randomUUID();

        view.setWorkingDir("$USER_HOME$/" + name);

        String text = preview().getText();
        assertTrue(text, text.endsWith(name + LanguageServerBundle.message("language.server.workingDir.missing", "")));
        assertFalse("the macro is resolved", text.contains("$USER_HOME$"));
    }

    public void testARelativeDirectoryIsNotChecked() {
        // the language server's process resolves it against the IDE's working directory
        view.setWorkingDir("server");

        assertEquals("", preview().getText());
    }

    /**
     * Returns the line below the working directory field.
     */
    private @NotNull JLabel preview() {
        var field = UIUtil.findComponentsOfType(view.getComponent(), TextFieldWithBrowseButton.class).stream()
                // the environment variables are a subclass
                .filter(component -> component.getClass() == TextFieldWithBrowseButton.class)
                .findFirst()
                .orElseThrow();
        return UIUtil.findComponentsOfType(view.getComponent(), JLabel.class).stream()
                .filter(label -> label.getLabelFor() == field && label.getText() != null && !label.getText().endsWith(":"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No line below the working directory field"));
    }
}
