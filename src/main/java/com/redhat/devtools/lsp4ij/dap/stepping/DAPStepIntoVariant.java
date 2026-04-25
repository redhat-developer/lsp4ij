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
package com.redhat.devtools.lsp4ij.dap.stepping;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant;
import org.eclipse.lsp4j.debug.StepInTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a step-into target from the Debug Adapter Protocol (DAP).
 * Wraps {@link StepInTarget} to integrate with IntelliJ's Smart Step Into UI.
 */
public class DAPStepIntoVariant extends XSmartStepIntoVariant {

    private final @NotNull StepInTarget target;
    private final @Nullable Document document;
    private final int zeroBasedLine;
    private final int occurrenceIndex;

    /**
     * Creates a new step-into variant.
     *
     * @param target          the DAP step-in target
     * @param document        the document containing the target (for highlighting)
     * @param zeroBasedLine   the 0-based line number in IntelliJ coordinates
     * @param occurrenceIndex the occurrence index when multiple targets have the same function name (0-based)
     */
    public DAPStepIntoVariant(@NotNull StepInTarget target,
                              @Nullable Document document,
                              int zeroBasedLine,
                              int occurrenceIndex) {
        this.target = target;
        this.document = document;
        this.zeroBasedLine = zeroBasedLine;
        this.occurrenceIndex = occurrenceIndex;
    }

    @Override
    public @NotNull String getText() {
        return target.getLabel();
    }

    @Override
    public @Nullable TextRange getHighlightRange() {
        // If DAP provides line/column, use it for precise highlighting
        Integer line = target.getLine();
        Integer column = target.getColumn();

        if (document != null && line != null && column != null) {
            // DAP uses 1-based line numbers
            int oneBasedLine = line;
            int lineNumber = oneBasedLine - 1;

            if (lineNumber >= 0 && lineNumber < document.getLineCount()) {
                int lineStartOffset = document.getLineStartOffset(lineNumber);
                int lineEndOffset = document.getLineEndOffset(lineNumber);

                // Column is 1-based, convert to offset
                int columnOffset = column - 1;
                if (columnOffset >= 0) {
                    int startOffset = lineStartOffset + columnOffset;

                    // Try to use endColumn if provided for precise range
                    Integer endColumn = target.getEndColumn();
                    int endOffset;
                    if (endColumn != null && endColumn > column) {
                        endOffset = Math.min(lineStartOffset + (endColumn - 1), lineEndOffset);
                    } else {
                        // Highlight the function name (estimate ~20 chars or to end of line)
                        endOffset = Math.min(startOffset + 20, lineEndOffset);
                    }

                    return new TextRange(startOffset, endOffset);
                }
            }
        }

        // Fallback: try to find the function name in the current line
        if (document != null && zeroBasedLine >= 0 && zeroBasedLine < document.getLineCount()) {
            int lineStartOffset = document.getLineStartOffset(zeroBasedLine);
            int lineEndOffset = document.getLineEndOffset(zeroBasedLine);
            String lineText = document.getText(new TextRange(lineStartOffset, lineEndOffset));

            // Extract function name from label (e.g., "add(x, y)" -> "add")
            // This avoids overlapping ranges when labels are nested (e.g., "multiply(add(x, y), subtract(x, y))")
            String label = target.getLabel();
            String functionName = label;
            int parenIndex = label.indexOf('(');
            if (parenIndex > 0) {
                functionName = label.substring(0, parenIndex);
            }

            // Search for the N-th occurrence of the function name in the line
            // to handle cases like: add(1, 2) + add(3, 4)
            int index = -1;
            for (int i = 0; i <= occurrenceIndex; i++) {
                index = lineText.indexOf(functionName, index + 1);
                if (index < 0) {
                    break;
                }
            }

            if (index >= 0) {
                int startOffset = lineStartOffset + index;
                int endOffset = startOffset + functionName.length();
                return new TextRange(startOffset, endOffset);
            }

            // Ultimate fallback: highlight entire line
            return new TextRange(lineStartOffset, lineEndOffset);
        }

        return null;
    }

    /**
     * Gets the target ID for use with the DAP stepIn request.
     *
     * @return the target ID
     */
    public int getTargetId() {
        return target.getId();
    }

    /**
     * Gets the underlying DAP step-in target.
     *
     * @return the step-in target
     */
    public @NotNull StepInTarget getTarget() {
        return target;
    }
}
