/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.console;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;

import java.util.List;

/**
 * Tests the empty state of the Language Servers view when no language server is configured. It extends
 * {@link BasePlatformTestCase}, not {@code LSPCodeInsightFixtureTestCase}, whose setUp configures a mock language server.
 */
public class LSPConsoleEmptyStateTest extends BasePlatformTestCase {

    private Project project;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        project = myFixture.getProject();
    }

    public void testEmptyStateShownWhenNoLanguageServerIsConfigured() {
        assertEmpty("No language server may be configured", LanguageServersRegistry.getInstance().getServerDefinitions());
        var panel = new LSPConsoleToolWindowPanel(project);
        try {
            PlatformTestUtil.waitWithEventsDispatching("The empty state must be shown when no language server is configured",
                    panel::isEmptyStateVisible, 10);
            assertTrue(panel.isEmptyStateVisible());
            assertNotNull(panel.getEmptyStatePanel());
        } finally {
            Disposer.dispose(panel);
        }
    }

    public void testEmptyStateHiddenWhenALanguageServerIsConfigured() {
        var definition = new MockLanguageServerDefinition("empty-state-server", false);
        LanguageServersRegistry.getInstance().addServerDefinition(project, definition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.empty-state"), "plaintext")));
        try {
            var panel = new LSPConsoleToolWindowPanel(project);
            try {
                PlatformTestUtil.waitWithEventsDispatching("The empty state must be hidden when a language server is configured",
                        () -> !panel.isEmptyStateVisible(), 10);
                assertFalse(panel.isEmptyStateVisible());
            } finally {
                Disposer.dispose(panel);
            }
        } finally {
            LanguageServersRegistry.getInstance().removeServerDefinition(project, definition);
        }
    }
}
