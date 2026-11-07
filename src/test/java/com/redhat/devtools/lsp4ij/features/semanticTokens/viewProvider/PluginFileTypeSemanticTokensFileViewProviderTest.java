/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.semanticTokens.viewProvider;

import com.intellij.codeInsight.navigation.CtrlMouseData;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationOrUsageHandler2;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.fixtures.LSPSemanticTokensFileViewProviderFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tests the semantic tokens-based file view provider for files of a file type that a client plugin defines without a
 * language, which the platform otherwise parses as a single plain text element. The file type is associated with an
 * extension, and the language server is mapped to a narrower pattern, an exact file name.
 */
public class PluginFileTypeSemanticTokensFileViewProviderTest extends LSPSemanticTokensFileViewProviderFixtureTestCase {

    private static final String EXTENSION = "plugintype";

    private static final String TEST_FILE_NAME = "test." + EXTENSION;

    private static final String OTHER_FILE_NAME = "other." + EXTENSION;

    private static final String TEST_FILE_BODY = """
            fun main
            call main
            """;

    // language=json
    private static final String MOCK_SEMANTIC_TOKENS_PROVIDER_JSON = """
            {
              "legend": {
                "tokenTypes": ["function"],
                "tokenModifiers": ["declaration"]
              },
              "full": true
            }
            """;

    // language=json
    private static final String MOCK_SEMANTIC_TOKENS_JSON = """
            {
              "data": [0, 4, 4, 0, 1, 1, 5, 4, 0, 0]
            }
            """;

    private MockLanguageServerDefinition secondServer;

    public PluginFileTypeSemanticTokensFileViewProviderTest() {
        super(TEST_FILE_NAME, "plugintype");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ((FileTypeManagerImpl) FileTypeManager.getInstance()).registerFileType(new PluginFileType(),
                List.of(new ExtensionFileNameMatcher(EXTENSION)), getTestRootDisposable(), new DefaultPluginDescriptor("plugin"));
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (secondServer != null) {
                secondServer.getServer().waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), secondServer);
            }
        } finally {
            super.tearDown();
        }
    }

    public void testCtrlHoverUnderlinesTheReferenceInsteadOfTheWholeFile() throws Exception {
        PsiFile file = initialize(TEST_FILE_NAME, TEST_FILE_BODY, MOCK_SEMANTIC_TOKENS_PROVIDER_JSON, MOCK_SEMANTIC_TOKENS_JSON, true);
        assertInstanceOf(file.getFileType(), PluginFileType.class);
        assertInstanceOf(file.getViewProvider(), LSPSemanticTokensStructurelessFileViewProvider.class);
        MockLanguageServer.INSTANCE.setDefinition(List.of(new Location(LSPIJUtils.toUriAsString(file),
                new Range(new Position(0, 4), new Position(0, 8)))));

        // Like the Ctrl/Cmd+hover handler, compute the data in a read action off the EDT
        int reference = TEST_FILE_BODY.lastIndexOf("main");
        Editor editor = myFixture.getEditor();
        CtrlMouseData ctrlMouseData = ReadAction.nonBlocking(() -> GotoDeclarationOrUsageHandler2.getCtrlMouseData(editor, file, reference + 1))
                .submit(AppExecutorUtil.getAppExecutorService())
                .get(10, TimeUnit.SECONDS);

        assertNotNull(ctrlMouseData);
        assertEquals(List.of(new TextRange(reference, reference + "main".length())), ctrlMouseData.getRanges());
    }

    public void testOpenFileGetsTheViewProviderWhenALanguageServerIsMappedToIt() {
        // No language server is mapped to the file yet, so it gets the platform's view provider
        PsiFile file = myFixture.configureByText(OTHER_FILE_NAME, TEST_FILE_BODY);
        assertInstanceOf(file.getFileType(), PluginFileType.class);
        assertFalse(file.getViewProvider() instanceof LSPSemanticTokensFileViewProvider);

        secondServer = new MockLanguageServerDefinition("second-server", false);
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), secondServer,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of(OTHER_FILE_NAME), "plugintype")));
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();

        PsiFile reparsedFile = PsiManager.getInstance(myFixture.getProject()).findFile(file.getVirtualFile());
        assertNotNull(reparsedFile);
        assertInstanceOf(reparsedFile.getViewProvider(), LSPSemanticTokensStructurelessFileViewProvider.class);
    }

    // Like the file type of a client plugin that highlights its files with TextMate: it has no language
    private static final class PluginFileType implements FileType {

        @Override
        public @NotNull String getName() {
            return "LSP4IJ plugin file type";
        }

        @Override
        public @NotNull String getDescription() {
            return "LSP4IJ plugin file type";
        }

        @Override
        public @NotNull String getDefaultExtension() {
            return EXTENSION;
        }

        @Override
        public @Nullable Icon getIcon() {
            return null;
        }

        @Override
        public boolean isBinary() {
            return false;
        }
    }
}
