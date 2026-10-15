/*******************************************************************************
 * Copyright (c) 2026 Contributors to the LSP4IJ project.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.diagnostics;

import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.redhat.devtools.lsp4ij.fixtures.LSPCodeInsightTestFixture;
import com.redhat.devtools.lsp4ij.fixtures.LSPTestFixtureFactory;
import com.redhat.devtools.lsp4ij.*;
import com.redhat.devtools.lsp4ij.client.features.*;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServerDefinition;
import org.eclipse.lsp4j.*;
import java.util.*;

public class DiagnosticUpdateHookTest extends UsefulTestCase {
    private LSPCodeInsightTestFixture myFixture;
    private LanguageServerWrapper wrapper;
    private final List<List<Diagnostic>> reports = new ArrayList<>();
    @Override protected void setUp() throws Exception {
        super.setUp();
        var builder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getName());
        myFixture = LSPTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(builder.getFixture());
        myFixture.setUp();
        var definition = new MockLanguageServerDefinition("diagnostic-hook", false) {
            @Override public LSPClientFeatures createClientFeatures() {
                return new LSPClientFeatures().setDiagnosticFeature(new LSPDiagnosticFeature() {
                    @Override public boolean canReportProblem(VirtualFile file) { return false; }
                    @Override public void diagnosticsUpdated(VirtualFile file, List<Diagnostic> diagnostics) {
                        reports.add(diagnostics);
                    }
                });
            }
        };
        wrapper = new LanguageServerWrapper(myFixture.getProject(), definition);
    }
    @Override protected void tearDown() throws Exception {
        try { Disposer.dispose(wrapper); myFixture.tearDown(); } finally { super.tearDown(); }
    }
    private Diagnostic warning(String message) {
        var result = new Diagnostic(new Range(new Position(0,0),new Position(0,1)),message);
        result.setSeverity(DiagnosticSeverity.Warning);
        return result;
    }
    public void testClosedFileWarningsReplaceAndClearWithoutOpening() {
        var file = myFixture.addFileToProject("closed.lua","value").getVirtualFile();
        var first = warning("first");
        wrapper.updateDiagnostics(file,"one",List.of(first),null);
        var snapshot = reports.get(reports.size()-1);
        assertEquals(List.of(first),snapshot);
        try { snapshot.clear(); fail("Snapshot must be read-only"); } catch(UnsupportedOperationException expected) { }
        wrapper.updateDiagnostics(file,"one",List.of(warning("replacement")),null);
        wrapper.updateDiagnostics(file,"one",List.of(),null);
        assertTrue(reports.get(reports.size()-1).isEmpty());
        assertEquals(List.of(first),snapshot);
        assertNull(wrapper.getOpenedDocument(LSPIJUtils.toUri(file)));
    }
    public void testOpenFileReportsMergedProvidersAndEmptyCache() {
        var file = myFixture.addFileToProject("open.lua","value").getVirtualFile();
        myFixture.configureFromExistingVirtualFile(file);
        var first=warning("published"); var second=warning("pulled");
        wrapper.updateDiagnostics(file,"publish",List.of(first),null);
        wrapper.updateDiagnostics(file,"pull",List.of(second),null);
        assertEquals(Set.of(first,second),Set.copyOf(reports.get(reports.size()-1)));
        wrapper.updateDiagnostics(file,"publish",List.of(),null);
        assertEquals(List.of(second),reports.get(reports.size()-1));
        wrapper.updateDiagnostics(file,"pull",List.of(),null);
        assertTrue(reports.get(reports.size()-1).isEmpty());
        wrapper.updateDiagnostics(file,"pull",List.of(),null);
        assertTrue(reports.get(reports.size()-1).isEmpty());
    }
}
