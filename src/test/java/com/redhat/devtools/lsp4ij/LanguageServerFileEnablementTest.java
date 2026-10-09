/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc. and others.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Started servers sharing a file mapping can still own different files. */
public class LanguageServerFileEnablementTest extends UsefulTestCase {
    private LSPCodeInsightTestFixture fixture;
    private ScopedServer alpha;
    private ScopedServer beta;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        var projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        fixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        fixture.setUp();
        alpha = new ScopedServer("alpha");
        beta = new ScopedServer("beta");
        for (var definition : List.of(alpha, beta)) {
            // Advertise both full-document and range formatting.
            definition.getServer().initialize(new InitializeParams()).get().getCapabilities()
                    .setDocumentRangeFormattingProvider(true);
            definition.getServer().setFormattingTextEdits(List.of(new TextEdit(
                    new Range(new Position(0, 0), new Position(0, 3)), definition.name)));
            LanguageServersRegistry.getInstance().addServerDefinition(fixture.getProject(), definition,
                    List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.scoped"), null)));
            LanguageServerManager.getInstance(fixture.getProject()).getLanguageServer(definition.getId())
                    .get(5, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            for (var definition : List.of(alpha, beta)) {
                definition.getServer().waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(fixture.getProject(), definition);
            }
        } finally {
            try {
                fixture.tearDown();
            } finally {
                super.tearDown();
            }
        }
    }

    public void testHasAnyRespectsFileEnablement() {
        var file = fixture.configureByText("alpha.scoped", "old\n");
        var accessor = LanguageServiceAccessor.getInstance(fixture.getProject());
        assertTrue(accessor.hasAny(file, server -> server.getServerDefinition() == alpha));
        assertFalse(accessor.hasAny(file, server -> server.getServerDefinition() == beta));
        assertFalse(accessor.hasAny(file, server -> false));
        alpha.enabled = false;
        assertFalse(accessor.hasAny(file, server -> true));
        alpha.enabled = true;
        assertTrue(accessor.hasAny(file, server -> true));
    }

    public void testProcessLanguageServersRespectsFileEnablement() {
        var accessor = LanguageServiceAccessor.getInstance(fixture.getProject());
        for (var definition : List.of(alpha, beta)) {
            var file = fixture.configureByText(definition.name + ".scoped", "old\n");
            var selected = new ArrayList<String>();
            accessor.processLanguageServers(file, server -> selected.add(server.getServerDefinition().getId()));
            assertEquals(List.of(definition.getId()), selected);
            definition.enabled = false;
            selected.clear();
            accessor.processLanguageServers(file, server -> selected.add(server.getServerDefinition().getId()));
            assertTrue(selected.isEmpty());
            definition.enabled = true;
        }
    }

    public void testWholeFileFormattingUsesOwningServer() throws Exception {
        assertFormatting(false);
    }

    public void testFilesWithoutVirtualFileAreNotSelected() {
        var file = (PsiFile) fixture.configureByText("alpha.scoped", "old\n").copy();
        assertNull(file.getVirtualFile());
        var accessor = LanguageServiceAccessor.getInstance(fixture.getProject());
        assertFalse(accessor.hasAny(file, server -> {
            fail("A file without a virtual file must not reach the filter");
            return true;
        }));
        accessor.processLanguageServers(file, server ->
                fail("A file without a virtual file must not reach the processor"));
    }

    public void testRangeFormattingUsesOwningServer() throws Exception {
        assertFormatting(true);
    }

    private void assertFormatting(boolean selection) throws Exception {
        for (var definition : List.of(alpha, beta)) {
            var file = fixture.configureByText(definition.name + ".scoped", "old\nkeep\n");
            LanguageServiceAccessor.getInstance(fixture.getProject()).getLanguageServers(file, null, null)
                    .get(5, TimeUnit.SECONDS);
            if (selection) {
                fixture.getEditor().getSelectionModel().setSelection(0, 3);
            }
            fixture.performEditorAction(IdeActions.ACTION_EDITOR_REFORMAT);
            assertEquals(definition.name + "\nkeep\n", fixture.getEditor().getDocument().getText());
        }
    }

    private static final class ScopedServer extends MockLanguageServerDefinition {
        private final String name;
        private boolean enabled = true;

        private ScopedServer(String name) {
            super("scoped-" + name, false);
            this.name = name;
        }

        @Override
        public @NotNull LSPClientFeatures createClientFeatures() {
            return new LSPClientFeatures() {
                @Override
                public boolean isEnabled(@NotNull VirtualFile file) {
                    return enabled && file.getName().equals(name + ".scoped");
                }
            };
        }
    }
}
