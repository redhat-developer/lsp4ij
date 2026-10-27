/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features;

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.platform.backend.documentation.DocumentationData;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.lsp4ij.LSPFileSupport;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationTarget;
import com.redhat.devtools.lsp4ij.features.navigation.LSPDefinitionParams;
import com.redhat.devtools.lsp4ij.features.navigation.LSPDefinitionSupport;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightFixtureTestCase;
import com.redhat.devtools.lsp4ij.internal.CancellationSupport;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A file's cached hover and definition depend on other files too, and are asked for again when one of them changes.
 */
public class FeatureCacheOtherFileChangeTest extends LSPCodeInsightFixtureTestCase {

    public FeatureCacheOtherFileChangeTest() {
        super("*.foo");
    }

    public void testHoverIsAskedForAgainWhenAnotherFileChanges() {
        var other = myFixture.configureByText("other.foo", "class Other");
        myFixture.configureByText("main.foo", "Oth<caret>er o");
        MockLanguageServer.INSTANCE.setHover(hover("class Other"));
        assertEquals("class Other", hover());

        // nothing has changed, so the hover is reused
        MockLanguageServer.INSTANCE.setHover(hover("class Other // documented"));
        assertEquals("class Other", hover());

        change(other);
        assertEquals("class Other // documented", hover());
    }

    public void testDefinitionIsAskedForAgainWhenAnotherFileChanges() throws Exception {
        var other = myFixture.configureByText("other.foo", "class Other");
        var main = myFixture.configureByText("main.foo", "Oth<caret>er o");
        var definitions = LSPFileSupport.getSupport(main).getDefinitionSupport();
        MockLanguageServer.INSTANCE.setDefinition(List.of(location(other, 0)));
        assertEquals(0, definitionLine(definitions));

        // nothing has changed, so the definition is reused
        MockLanguageServer.INSTANCE.setDefinition(List.of(location(other, 1)));
        assertEquals(0, definitionLine(definitions));

        // a line inserted above the definition moves it to the next line
        change(other);
        assertEquals(1, definitionLine(definitions));
    }

    public void testAnAnswerIsAskedForAgainWhenAnotherFileChangesWhileItIsAskedFor() {
        var other = myFixture.configureByText("other.foo", "class Other");
        var main = myFixture.configureByText("main.foo", "Other o");
        var loads = new AtomicInteger();
        var support = new AbstractLSPDocumentFeatureSupport<Object, Integer>(main, true, true) {
            @Override
            protected CompletableFuture<Integer> doLoad(Object params, CancellationSupport cancellationSupport) {
                if (loads.incrementAndGet() == 1) {
                    // the language server may answer before it learns of the change
                    change(other);
                }
                return CompletableFuture.completedFuture(loads.get());
            }
        };
        assertEquals(1, (int) support.getFeatureData(null).join());

        // the first answer may be out of date, so it is asked for again, and then reused while nothing changes
        assertEquals(2, (int) support.getFeatureData(null).join());
        assertEquals(2, (int) support.getFeatureData(null).join());
    }

    public void testFoldingIsReusedWhenAnotherFileChanges() {
        var other = myFixture.configureByText("other.foo", "class Other");
        var main = myFixture.configureByText("main.foo", "Oth<caret>er o");
        var folding = LSPFileSupport.getSupport(main).getFoldingRangeSupport();
        var ranges = folding.getFoldingRanges(new FoldingRangeRequestParams(new TextDocumentIdentifier()));

        // the folding of a file depends on that file only
        change(other);
        assertSame(ranges, folding.getFoldingRanges(new FoldingRangeRequestParams(new TextDocumentIdentifier())));
    }

    private String hover() {
        var editor = myFixture.getEditor();
        var targets = IdeDocumentationTargetProvider.getInstance(myFixture.getProject())
                .documentationTargets(editor, myFixture.getFile(), editor.getCaretModel().getOffset())
                .stream()
                .filter(LSPDocumentationTarget.class::isInstance)
                .toList();
        assertSize(1, targets);
        return ((DocumentationData) targets.get(0).computeDocumentation()).getHtml();
    }

    private int definitionLine(LSPDefinitionSupport definitions) throws Exception {
        var editor = myFixture.getEditor();
        int offset = editor.getCaretModel().getOffset();
        var params = new LSPDefinitionParams(new TextDocumentIdentifier(), LSPIJUtils.toPosition(offset, editor.getDocument()), offset);
        var locations = definitions.getDefinitions(params).get(30, TimeUnit.SECONDS);
        assertSize(1, locations);
        return locations.get(0).location().getRange().getStart().getLine();
    }

    /**
     * Inserts a line at the start of the file, as an edit of a file which another one depends on, and commits it.
     */
    private void change(PsiFile file) {
        var project = myFixture.getProject();
        var documents = PsiDocumentManager.getInstance(project);
        var document = documents.getDocument(file);
        assertNotNull(document);
        WriteCommandAction.runWriteCommandAction(project, () -> document.insertString(0, "\n"));
        documents.commitAllDocuments();
    }

    private static Hover hover(String text) {
        return new Hover(new MarkupContent(MarkupKind.PLAINTEXT, text));
    }

    private static Location location(PsiFile file, int line) {
        return new Location(LSPIJUtils.toUriAsString(file), new Range(new Position(line, 6), new Position(line, 11)));
    }
}
