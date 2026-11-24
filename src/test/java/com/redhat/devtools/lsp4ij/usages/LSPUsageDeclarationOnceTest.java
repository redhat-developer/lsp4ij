/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.rules.UsageTypeGroupingRule;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Find Usages shows the declaration of a symbol once, as a definition, although the language server also gives it as
 * a reference, since LSP4IJ asks for the references with the declaration, and as a type definition, since the symbol
 * is a type: the usage view merges the usages of a line only within a group of the same usage type, so that it showed
 * the declaration in three groups (#729).
 */
public class LSPUsageDeclarationOnceTest extends LSPCodeInsightFixtureTestCase {

    private static final String TEXT = "class ArgNode {}\nArgNode node = new ArgNode();\n";
    // "ArgNode" in "class ArgNode {}"
    private static final Range DECLARATION = range(0, 6, 13);
    private static final Range TYPE_REFERENCE = range(1, 0, 7);
    private static final Range CONSTRUCTOR_REFERENCE = range(1, 19, 26);

    public LSPUsageDeclarationOnceTest() {
        super("*.foo");
    }

    public void testTheDeclarationIsShownOnceAsADefinition() throws Exception {
        MockLanguageServer.reset();
        PsiFile file = myFixture.configureByText("main.foo", TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject()).getLanguageServers(file, null, null).get(30, TimeUnit.SECONDS);
        String uri = LSPIJUtils.toUriAsString(file);
        MockLanguageServer.INSTANCE.setDefinition(List.of(new Location(uri, DECLARATION)));
        MockLanguageServer.INSTANCE.setTypeDefinitions(List.of(new LocationLink(uri, DECLARATION, DECLARATION)));
        // With the declaration, which LSP4IJ asks for
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockReferences(new Location(uri, DECLARATION),
                new Location(uri, TYPE_REFERENCE), new Location(uri, CONSTRUCTOR_REFERENCE));

        List<String> usages = findUsages(file, new LSPUsageTriggeredPsiElement(file, toTextRange(DECLARATION)));

        String definitions = LanguageServerBundle.message("usage.type.definitions");
        String references = LanguageServerBundle.message("usage.type.references");
        assertSameElements(usages,
                definitions + " " + toTextRange(DECLARATION),
                references + " " + toTextRange(TYPE_REFERENCE),
                references + " " + toTextRange(CONSTRUCTOR_REFERENCE));
    }

    /**
     * Returns the usages that Find Usages shows for the given element, as the group that the usage view shows each
     * in, followed by its text range.
     */
    private @NotNull List<String> findUsages(@NotNull PsiFile file, @NotNull LSPUsageTriggeredPsiElement element) throws Exception {
        List<Usage> usages = new CopyOnWriteArrayList<>();
        FindUsagesOptions options = new FindUsagesOptions(file.getProject());
        // Find Usages searches in a background thread
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> new LSPUsageSearcher().processElementUsages(element, usages::add, options))
                .get(30, TimeUnit.SECONDS);
        return usages.stream()
                .map(usage -> groupOf(usage) + " " + textRangeOf(usage))
                .toList();
    }

    private static @NotNull String groupOf(@NotNull Usage usage) {
        List<UsageGroup> groups = new UsageTypeGroupingRule().getParentGroupsFor(usage, UsageTarget.EMPTY_ARRAY);
        assertEquals(1, groups.size());
        return groups.get(0).getPresentableGroupText();
    }

    private static @NotNull TextRange textRangeOf(@NotNull Usage usage) {
        UsageInfo usageInfo = ((UsageInfo2UsageAdapter) usage).getUsageInfo();
        return ((LSPUsageViewElement) usageInfo.getElement()).getUsage().getTextRange();
    }

    private static @NotNull Range range(int line, int startCharacter, int endCharacter) {
        return new Range(new Position(line, startCharacter), new Position(line, endCharacter));
    }

    private @NotNull TextRange toTextRange(@NotNull Range range) {
        var document = myFixture.getEditor().getDocument();
        return new TextRange(LSPIJUtils.toOffset(range.getStart(), document), LSPIJUtils.toOffset(range.getEnd(), document));
    }
}
