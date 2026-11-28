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
package com.redhat.devtools.lsp4ij;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The diagnostics of a closed document ignore a diagnostic without a range, which LSP requires, as those of an opened
 * document do: such a diagnostic can't be placed in the file, and an error without a range doesn't make the file a
 * problem file.
 */
public class ClosedDocumentDiagnosticsTest {

    private final Diagnostic hint = hint();

    @Test
    void aDiagnosticWithoutARangeIsIgnoredAndTheOthersAreKept() {
        var document = new ClosedDocument();

        assertTrue(document.updateDiagnostics("test", Arrays.asList(hint, errorWithoutRange(), null), null));

        assertEquals(List.of(hint), List.copyOf(document.getDiagnostics()));
        assertFalse(document.hasErrors(), "An error without a range isn't one of the file");
    }

    @Test
    void aReportWithOnlyDiagnosticsWithoutARangeRemovesTheDiagnosticsOfItsProvider() {
        var document = new ClosedDocument();
        document.updateDiagnostics("test", List.of(hint), null);

        assertTrue(document.updateDiagnostics("test", List.of(errorWithoutRange()), null));

        assertTrue(document.getDiagnostics().isEmpty(), document.getDiagnostics().toString());
        assertFalse(document.hasErrors());
    }

    private static Diagnostic hint() {
        Diagnostic diagnostic = new Diagnostic(new Range(new Position(0, 6), new Position(0, 9)), "a hint");
        diagnostic.setSeverity(DiagnosticSeverity.Hint);
        return diagnostic;
    }

    /**
     * Returns an error without a range, as a language server may publish it. LSP4J's constructors and setters refuse
     * null, but its JSON parser doesn't.
     */
    private static Diagnostic errorWithoutRange() {
        Diagnostic diagnostic = new Diagnostic();
        diagnostic.setMessage("an error without a range");
        diagnostic.setSeverity(DiagnosticSeverity.Error);
        return diagnostic;
    }
}
