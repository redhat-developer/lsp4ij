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
package com.redhat.devtools.lsp4ij.dap.client;

import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.jetbrains.annotations.NotNull;

/**
 * The groups of the 'output' events of a debug adapter, which the debug console shows indented by their nesting level,
 * as VS Code shows the output of {@code console.group()}:
 * <ul>
 *     <li>{@code group: start} and {@code group: startCollapsed} start a group: the output of the event is its header,
 *     on a line of its own at the current level, and the lines which follow are one level deeper;</li>
 *     <li>{@code group: end} ends the current group, and its output, if any, is at the outer level. The level never
 *     goes below 0.</li>
 * </ul>
 * A group can't be folded, so a collapsed group is shown expanded.
 * <p>
 * The output of an event can be part of a line, so each line is indented where it starts, not each event. A group
 * starts and ends on a line of its own, as in VS Code. Telemetry output goes to no group, and isn't indented.
 * <p>
 * Each DAP session has its own groups, as each session of a tree of sessions started with 'startDebugging' has its own
 * output. It is a class of its own so that it can be tested without a debug adapter, which a {@link DAPClient} can
 * hardly be.
 */
final class DAPOutputGroups {

    /**
     * The indentation of a level of nesting, as Node.js indents the output of {@code console.group()} in a terminal.
     */
    static final String INDENT = "  ";

    // The nesting level of the groups which started and have not ended yet. Guarded by this.
    private int level;
    // Whether the output so far ends its line, so that the next output starts a line. Guarded by this.
    private boolean lineStart = true;

    /**
     * Returns the text which the debug console prints for the given 'output' event: its output, with each line
     * indented by the nesting level of the group it belongs to, and the start or the end of a group.
     *
     * @param args the 'output' event.
     * @return the text to print, empty for nothing.
     */
    synchronized @NotNull String indent(@NotNull OutputEventArguments args) {
        // The output is required, but the JSON of an event may still leave it out
        String output = args.getOutput() != null ? args.getOutput() : "";
        if (OutputEventArgumentsCategory.TELEMETRY.equals(args.getCategory())) {
            return output;
        }
        var text = new StringBuilder();
        if (args.getGroup() == null) {
            append(text, output);
            return text.toString();
        }
        switch (args.getGroup()) {
            case START, START_COLLAPSED -> {
                // The header, on a line of its own at the current level
                endLine(text);
                append(text, output);
                endLine(text);
                level++;
            }
            case END -> {
                level = Math.max(0, level - 1);
                endLine(text);
                append(text, output);
            }
        }
        return text.toString();
    }

    /**
     * Appends the given output to the text, with the indentation of the current level at the start of each line which
     * isn't empty. A carriage return starts the line again.
     */
    private void append(@NotNull StringBuilder text, @NotNull String output) {
        String indent = INDENT.repeat(level);
        for (int i = 0; i < output.length(); i++) {
            char c = output.charAt(i);
            if (lineStart && c != '\n' && c != '\r') {
                text.append(indent);
            }
            text.append(c);
            lineStart = c == '\n' || c == '\r';
        }
    }

    /**
     * Ends the current line, unless the output so far ends it.
     */
    private void endLine(@NotNull StringBuilder text) {
        if (!lineStart) {
            text.append('\n');
            lineStart = true;
        }
    }
}
