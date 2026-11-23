/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.client.features;

import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LanguageServersRegistry;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests the flags of {@link EditorBehaviorFeature}, which are cached for each file.
 */
public class EditorBehaviorFeatureTest extends LSPCodeInsightFixtureTestCase {

    private MockLanguageServerDefinition serverDefinition;

    public EditorBehaviorFeatureTest() {
        // The language server of the fixture serves no file of the test: only the one of the test serves its file
        super("*.unused");
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (serverDefinition != null) {
                serverDefinition.getServer().waitBeforeTearDown();
                LanguageServersRegistry.getInstance().removeServerDefinition(myFixture.getProject(), serverDefinition);
            }
        } finally {
            super.tearDown();
        }
    }

    /**
     * A language server whose flag changes while the flag of a file is computed from it, as when it starts, or its
     * configuration changes, meanwhile: the flag of the file is then computed again, instead of being kept as it was
     * before the change.
     */
    public void testAFlagThatChangesWhileItIsComputedIsComputedAgain() throws Exception {
        var enabled = new AtomicBoolean();
        serverDefinition = new MockLanguageServerDefinition("editor-behavior-server", false) {
            @Override
            public @NotNull LSPClientFeatures createClientFeatures() {
                var clientFeatures = new LSPClientFeatures();
                return clientFeatures.setEditorBehaviorFeature(new EditorBehaviorFeature(clientFeatures) {
                    @Override
                    public boolean isEnableSemanticTokensFileViewProvider(@NotNull PsiFile file) {
                        // The first computation reads the flag before it changes: the change is counted by the
                        // modification tracker of the language server, as a change of its configuration is
                        boolean wasEnabled = enabled.getAndSet(true);
                        if (!wasEnabled) {
                            clientFeatures.getServerWrapper().incrementModificationCount();
                        }
                        return wasEnabled;
                    }
                });
            }
        };
        LanguageServersRegistry.getInstance().addServerDefinition(myFixture.getProject(), serverDefinition,
                List.of(ServerMappingSettings.createFileNamePatternsMappingSettings(List.of("*.ts"), "typescript")));
        PsiFile file = myFixture.configureByText("test.ts", "function foo() {}");
        LanguageServiceAccessor.getInstance(file.getProject())
                .getLanguageServers(file, null, null)
                .get(5000, TimeUnit.MILLISECONDS);

        // Computes the flag from the language server, unless a computation of the editor already did
        EditorBehaviorFeature.enableSemanticTokensFileViewProvider(file);

        assertTrue("The flag must be computed again once it has changed while it was computed",
                EditorBehaviorFeature.enableSemanticTokensFileViewProvider(file));
    }
}
