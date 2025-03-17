/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.codeLens;

import java.util.List;

import static com.redhat.devtools.lsp4ij.features.codeLens.LSPCodeLensProvider.getCodeLensLine;

/**
 * Result of LSP code lens.
 */
public class CodeLensDataResult {

    private final List<CodeLensData> codeLensData;
    private int nbToResolve;

    CodeLensDataResult(List<CodeLensData> codeLensData) {
        this.codeLensData = codeLensData;
        nbToResolve = 0;
        for(var codeLens : codeLensData) {
            codeLens.setResult(this);
            if (codeLens.isToResolve()) {
                nbToResolve++;
            }
        }
    }

    /**
     * Returns true if there are some code lens to resolve and false otherwise.
     *
     * @return true if there are some code lens to resolve and false otherwise.
     */
    public boolean hasToResolve() {
        return nbToResolve > 0;
    }

    /**
     * Returns true if there are some code lens to resolve visible in the given view port range and false otherwise.
     * @param firstViewportLine first visible line.
     * @param lastViewportLine last visible line.
     * @return true if there are some code lens to resolve visible in the given view port range and false otherwise.
     */
    public boolean hasToResolve(int firstViewportLine, int lastViewportLine) {
        if (!hasToResolve()) {
            return false;
        }
        // Iterate over the provided code lens data to filter and categorize it.
        for (var codeLens : codeLensData) {
            int codeLensLine = getCodeLensLine(codeLens); // Get the line number where the code lens should be shown.
            if (codeLensLine > lastViewportLine) {
                return false;
            }
            if (codeLensLine >= firstViewportLine && codeLens.isToResolve()) {
                return true;
            }
        }
        return false;
    }

    public List<CodeLensData> getCodeLensData() {
        return codeLensData;
    }

    void decrementResolve() {
        nbToResolve--;
    }
}
