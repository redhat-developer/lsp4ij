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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * The temporary breakpoint that "Run to Cursor" sets before it resumes the program. It stays until the program next
 * stops, at that breakpoint or anywhere else, as VS Code's does, and is forgotten when the session ends. A debug
 * adapter answers 'continue' as the program resumes, so a temporary breakpoint removed once that request is answered
 * is gone before a program that takes longer than that to reach the cursor gets there, and the program runs past it.
 */
public class RunToCursorBreakpoint {

    private final @NotNull BreakpointHandlerBase<?> breakpointHandler;
    // The position of the temporary breakpoint, or null when none is set. set(), stopped() and ended() take turns, and
    // each sends its 'setBreakpoints' requests before it returns, so the debug adapter gets them in the order of the
    // calls: a stop while the breakpoint is being set removes it once it is set.
    private @Nullable XSourcePosition position;

    public RunToCursorBreakpoint(@NotNull BreakpointHandlerBase<?> breakpointHandler) {
        this.breakpointHandler = breakpointHandler;
    }

    /**
     * Sets the temporary breakpoint at the given position, in place of one where the program has not stopped since.
     *
     * @param sourcePosition the position to run to.
     * @return the completable future of the 'setBreakpoints' requests which set it.
     */
    public synchronized CompletableFuture<@Nullable Void> set(@NotNull XSourcePosition sourcePosition) {
        XSourcePosition previous = position;
        position = sourcePosition;
        if (previous != null) {
            breakpointHandler.unregisterTemporaryBreakpoint(previous);
        }
        return breakpointHandler.registerTemporaryBreakpoint(sourcePosition);
    }

    /**
     * Removes the temporary breakpoint, if one is set, since the program stopped.
     */
    public synchronized void stopped() {
        XSourcePosition set = position;
        position = null;
        if (set != null) {
            breakpointHandler.unregisterTemporaryBreakpoint(set);
        }
    }

    /**
     * Forgets the temporary breakpoint, since the session ended: there is no debug adapter to remove it from.
     */
    public synchronized void ended() {
        position = null;
    }
}
