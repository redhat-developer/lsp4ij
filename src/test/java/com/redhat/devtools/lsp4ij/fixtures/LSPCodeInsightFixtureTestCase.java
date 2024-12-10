/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.fixtures;

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Base class test case to test LSP feature.
 */
public abstract class LSPCodeInsightFixtureTestCase extends UsefulTestCase {

    private final String[] fileNamePatterns;
    private String languageId;

    protected LSPCodeInsightTestFixture myFixture;
    private MockLanguageServerDefinition serverDefinition;

    public LSPCodeInsightFixtureTestCase(String... fileNamePatterns) {
        this.fileNamePatterns = fileNamePatterns;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
        myFixture.setUp();
        registerServer();
    }

    @Override
    protected void tearDown() throws Exception {
        MockLanguageServer.INSTANCE.waitBeforeTearDown();
        unregisterServer();
        try {
            myFixture.tearDown();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            myFixture = null;
            super.tearDown();
        }
    }

    public void setLanguageId(String languageId) {
        this.languageId = languageId;
    }

    private void registerServer() {
        serverDefinition = new MockLanguageServerDefinition();
        List<ServerMappingSettings> mappings = List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of(fileNamePatterns), languageId));
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition, mappings);
    }

    private void unregisterServer() {
        LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
    }

    // Utility methods for getting the before/after index of the n'th occurrence of a substring of the test file body
    // from the beginning or end as appropriate. These methods help to avoid hard-coding offsets into the file.

    /**
     * Returns the index immediately <i>before</i> the {@code count} occurrence of {@code snippet} in {@code fileBody}
     * searching from the beginning.
     *
     * @param fileBody the file body
     * @param snippet  the search snippet
     * @param count    the occurrence count
     * @return the index immediately before the occurrence; fails fast if not found
     */
    protected static int beforeFirst(@NotNull String fileBody, @NotNull String snippet, int count) {
        int fromIndex = 0;
        for (int i = 0; i < count; i++) {
            int index = fileBody.indexOf(snippet, fromIndex);
            assertFalse("Failed to find occurrence " + (i + 1) + " of '" + snippet + "'.", index == -1);
            if (count == (i + 1)) {
                return index;
            } else {
                fromIndex = index + 1;
                if (fromIndex == fileBody.length()) {
                    fail("Failed to find occurrence " + (i + 1) + " of '" + snippet + "'.");
                }
            }
        }
        return fromIndex;
    }

    /**
     * Returns the index immediately <i>after</i> the {@code count} occurrence of {@code snippet} in {@code fileBody}
     * searching from the beginning.
     *
     * @param fileBody the file body
     * @param snippet  the search snippet
     * @param count    the occurrence count
     * @return the index immediately after the occurrence; fails fast if not found
     */
    protected static int afterFirst(@NotNull String fileBody, @NotNull String snippet, int count) {
        return beforeFirst(fileBody, snippet, count) + snippet.length();
    }

    /**
     * Returns the index immediately <i>before</i> the {@code count} occurrence of {@code snippet} in {@code fileBody}
     * searching from the end.
     *
     * @param fileBody the file body
     * @param snippet  the search snippet
     * @param count    the occurrence count
     * @return the index immediately before the last occurrence; fails fast if not found
     */
    protected static int beforeLast(@NotNull String fileBody, @NotNull String snippet, int count) {
        int fromIndex = fileBody.length() - 1;
        for (int i = 0; i < count; i++) {
            int index = fileBody.lastIndexOf(snippet, fromIndex);
            assertFalse("Failed to find last occurrence " + (i + 1) + " of '" + snippet + "'.", index == -1);
            if (count == (i + 1)) {
                return index;
            } else {
                fromIndex = index - 1;
                if (fromIndex == 0) {
                    fail("Failed to find occurrence " + (i + 1) + " of '" + snippet + "'.");
                }
            }
        }
        return fromIndex;
    }

    /**
     * Returns the index immediately <i>after</i> the {@code count} occurrence of {@code snippet} in {@code fileBody}
     * searching from the end.
     *
     * @param fileBody the file body
     * @param snippet  the search snippet
     * @param count    the occurrence count
     * @return the index immediately after the last occurrence; fails fast if not found
     */
    protected static int afterLast(@NotNull String fileBody, @NotNull String snippet, int count) {
        return beforeLast(fileBody, snippet, count) + snippet.length();
    }
}
