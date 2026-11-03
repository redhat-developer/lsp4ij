/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.usages;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.impl.UsagePreviewPanel;
import com.intellij.usages.impl.rules.UsageTypeGroupingRule;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A Find Usages result of a file without real PSI, like a plain text or TextMate file, is shown at its reference:
 * the usage preview highlights the reference, and opening the result goes to it.
 *
 * @see <a href="https://github.com/redhat-developer/lsp4ij/issues/359">#359</a>
 */
public class LSPUsagePreviewTest extends LSPCodeInsightFixtureTestCase {

    private static final String NAME = "callMe";

    // the Python file of #359, as plain text
    private static final String TEXT = "def callMe() :\n    print(\"I was called\")\n\ncallMe()\n" + "\n".repeat(23)
            + "callMe()\n\n\ncallMe()\n";

    // "callMe" in the definition and in the three calls, on lines 1, 4, 28 and 31 as the Find view shows them
    private static final List<Range> REFERENCES = List.of(
            range(0, 4),
            range(3, 0),
            range(27, 0),
            range(30, 0));

    public LSPUsagePreviewTest() {
        super("*.txt");
    }

    public void testPreviewHighlightsTheReference() throws Exception {
        PsiFile file = open();
        Editor editor = myFixture.getEditor();
        Document document = editor.getDocument();

        List<UsageInfo2UsageAdapter> usages = findUsages(file);

        assertEquals(REFERENCES.size(), usages.size());
        for (int i = 0; i < usages.size(); i++) {
            TextRange reference = LSPIJUtils.toTextRange(REFERENCES.get(i), document);
            // what the usage view previews when the result is selected
            List<UsageInfo> infos = Arrays.asList(usages.get(i).getMergedInfos());
            UsagePreviewPanel.highlight(infos, editor, file.getProject(), false, HighlighterLayer.ADDITIONAL_SYNTAX);

            // not the whole file, which is the one PSI element of a plain text file
            assertEquals(List.of(reference), previewHighlights(editor));
            assertEquals(reference.getEndOffset(), editor.getCaretModel().getOffset());
        }
    }

    public void testOpeningAResultGoesToTheReference() throws Exception {
        PsiFile file = open();
        Document document = myFixture.getEditor().getDocument();

        List<UsageInfo2UsageAdapter> usages = findUsages(file);

        assertEquals(REFERENCES.size(), usages.size());
        FileEditorManager editors = FileEditorManager.getInstance(file.getProject());
        for (int i = 0; i < usages.size(); i++) {
            Range reference = REFERENCES.get(i);
            UsageInfo2UsageAdapter usage = usages.get(i);
            int offset = LSPIJUtils.toOffset(reference.getStart(), document);
            assertEquals(reference.getStart().getLine(), usage.getLine());
            assertEquals(offset, usage.getNavigationOffset());

            // as when the result is opened from the usage view
            usage.navigate(true);
            Editor editor = editors.getSelectedTextEditor();
            assertNotNull(editor);
            assertEquals(offset, editor.getCaretModel().getOffset());
        }
    }

    public void testOpeningAResultInAnotherFileGoesToItsReference() throws Exception {
        PsiFile other = myFixture.addFileToProject("other.txt", "\n\ncallMe()\n");
        PsiFile file = open();
        Range reference = range(2, 0);
        MockLanguageServer.INSTANCE.getTextDocumentService()
                .setMockReferences(new Location(LSPIJUtils.toUriAsString(other), reference));

        List<UsageInfo2UsageAdapter> usages = findUsages(file);

        assertEquals(1, usages.size());
        usages.get(0).navigate(true);
        Editor editor = FileEditorManager.getInstance(file.getProject()).getSelectedTextEditor();
        assertNotNull(editor);
        assertEquals(other.getVirtualFile(), FileDocumentManager.getInstance().getFile(editor.getDocument()));
        assertEquals(LSPIJUtils.toOffset(reference.getStart(), editor.getDocument()),
                editor.getCaretModel().getOffset());
    }

    public void testResultIsGroupedAsAReference() throws Exception {
        PsiFile file = open();

        List<UsageInfo2UsageAdapter> usages = findUsages(file);

        String references = LanguageServerBundle.message("usage.type.references");
        assertEquals(REFERENCES.size(), usages.size());
        for (Usage usage : usages) {
            List<UsageGroup> groups = new UsageTypeGroupingRule().getParentGroupsFor(usage, UsageTarget.EMPTY_ARRAY);
            assertEquals(1, groups.size());
            assertEquals(references, groups.get(0).getPresentableGroupText());
        }
    }

    private PsiFile open() throws Exception {
        MockLanguageServer.reset();
        PsiFile file = myFixture.configureByText("index.txt", TEXT);
        LanguageServiceAccessor.getInstance(myFixture.getProject())
                .getLanguageServers(file, null, null)
                .get(30, TimeUnit.SECONDS);
        String uri = LSPIJUtils.toUriAsString(file);
        MockLanguageServer.INSTANCE.getTextDocumentService().setMockReferences(REFERENCES.stream()
                .map(range -> new Location(uri, range))
                .toArray(Location[]::new));
        return file;
    }

    private static @NotNull List<UsageInfo2UsageAdapter> findUsages(@NotNull PsiFile file) throws Exception {
        // the usages of "callMe" in the definition, as when Find Usages is invoked there
        TextRange name = TextRange.from(TEXT.indexOf(NAME), NAME.length());
        LSPUsageTriggeredPsiElement element = new LSPUsageTriggeredPsiElement(file, name);
        FindUsagesOptions options = new FindUsagesOptions(file.getProject());
        List<Usage> usages = new CopyOnWriteArrayList<>();
        // Find Usages searches in a background thread
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> new LSPUsageSearcher().processElementUsages(element, usages::add, options))
                .get(30, TimeUnit.SECONDS);
        return usages.stream()
                .map(UsageInfo2UsageAdapter.class::cast)
                .sorted(Comparator.comparingInt(UsageInfo2UsageAdapter::getNavigationOffset))
                .toList();
    }

    private static @NotNull List<TextRange> previewHighlights(@NotNull Editor editor) {
        return Arrays.stream(editor.getMarkupModel().getAllHighlighters())
                .filter(highlighter -> EditorColors.SEARCH_RESULT_ATTRIBUTES.equals(highlighter.getTextAttributesKey()))
                .map(RangeHighlighter::getTextRange)
                .toList();
    }

    private static @NotNull Range range(int line, int character) {
        return new Range(new Position(line, character), new Position(line, character + NAME.length()));
    }
}
