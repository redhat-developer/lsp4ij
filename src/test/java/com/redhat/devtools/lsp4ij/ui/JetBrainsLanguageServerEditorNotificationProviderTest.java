/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.ui;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EditorNotificationPanel;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A file served both by a language server of LSP4IJ and by one of IntelliJ's own LSP client has a warning above its
 * editor, which names both (#1933).
 */
public class JetBrainsLanguageServerEditorNotificationProviderTest extends LSPCodeInsightFixtureTestCase {

    public JetBrainsLanguageServerEditorNotificationProviderTest() {
        super("*.lsptest");
    }

    public void testAFileServedByBothClientsHasAWarningWhichNamesTheirServers() {
        var file = open("a.lsptest");

        var panel = notification(file, new JetBrainsLanguageServers.Server("probe", true, List.of("*.lsptest")));

        assertNotNull("the file must have the warning", panel);
        // The display name of the mock server is "name"
        assertEquals("This file is served twice: by 'name' of LSP4IJ, and by 'probe' of IntelliJ's Language Servers settings. "
                + "Disable one of them to avoid duplicate results.", panel.getText());
    }

    public void testADisabledServerOfIntelliJGivesNoWarning() {
        var file = open("a.lsptest");

        assertNull(notification(file, new JetBrainsLanguageServers.Server("probe", false, List.of("*.lsptest"))));
    }

    public void testAServerOfIntelliJForOtherFilesGivesNoWarning() {
        var file = open("a.lsptest");

        assertNull(notification(file, new JetBrainsLanguageServers.Server("probe", true, List.of("*.other"))));
    }

    public void testAFileWhichNoServerOfLSP4IJServesHasNoWarning() {
        var file = open("a.txt");

        assertNull(notification(file, new JetBrainsLanguageServers.Server("probe", true, List.of("*.txt"))));
    }

    public void testAFileWhoseServerOfLSP4IJIsDisabledHasNoWarning() {
        var file = open("a.lsptest");
        var definition = LanguageServersRegistry.getInstance().getServerDefinition("mock-server-id");
        assertNotNull(definition);
        definition.setEnabled(false, myFixture.getProject());
        try {
            assertNull(notification(file, new JetBrainsLanguageServers.Server("probe", true, List.of("*.lsptest"))));
        } finally {
            definition.setEnabled(true, myFixture.getProject());
        }
    }

    public void testNoWarningOnceTheUserAskedNotToShowItAgain() {
        var file = open("a.lsptest");
        var properties = PropertiesComponent.getInstance();
        properties.setValue(JetBrainsLanguageServerEditorNotificationProvider.DISABLE_KEY, true);
        try {
            assertNull(notification(file, new JetBrainsLanguageServers.Server("probe", true, List.of("*.lsptest"))));
        } finally {
            properties.unsetValue(JetBrainsLanguageServerEditorNotificationProvider.DISABLE_KEY);
        }
    }

    private @NotNull VirtualFile open(@NotNull String fileName) {
        return myFixture.configureByText(fileName, "content").getVirtualFile();
    }

    private @Nullable EditorNotificationPanel notification(@NotNull VirtualFile file,
                                                           @NotNull JetBrainsLanguageServers.Server jetBrainsServer) {
        var project = myFixture.getProject();
        var provider = new JetBrainsLanguageServerEditorNotificationProvider(p -> List.of(jetBrainsServer));
        var panel = provider.collectNotificationData(project, file);
        if (panel == null) {
            return null;
        }
        var fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file);
        assertNotNull(fileEditor);
        return (EditorNotificationPanel) panel.apply(fileEditor);
    }
}
