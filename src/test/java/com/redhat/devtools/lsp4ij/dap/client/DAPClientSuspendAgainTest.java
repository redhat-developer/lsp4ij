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

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the debug adapter refuses a Resume or a step, which IntelliJ resumed the session for, the session is suspended
 * again at the stop where the program still is, unless another stop suspended it meanwhile or the session ended.
 */
public class DAPClientSuspendAgainTest {

    private final List<XSuspendContext> reached = new ArrayList<>();
    private final XSuspendContext stop = new XSuspendContext() {
    };

    @Test
    void theSessionIsSuspendedAgainAtTheStopWhereTheProgramIs() {
        assertTrue(DAPClient.suspendAgain(session(null, false), stop));

        assertEquals(List.of(stop), reached);
    }

    @Test
    void aSessionWhichAnotherStopSuspendedMeanwhileStaysThere() {
        var nextStop = new XSuspendContext() {
        };

        assertFalse(DAPClient.suspendAgain(session(nextStop, false), stop));

        assertEquals(List.of(), reached);
    }

    @Test
    void aSessionWhichEndedIsLeftAsItIs() {
        assertFalse(DAPClient.suspendAgain(session(null, true), stop));

        assertEquals(List.of(), reached);
    }

    /**
     * Returns a debug session suspended at the given suspend context, or running for null, which may have ended.
     */
    private @NotNull XDebugSession session(@Nullable XSuspendContext context, boolean stopped) {
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class<?>[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSuspendContext" -> context;
                    case "isStopped" -> stopped;
                    case "positionReached" -> {
                        reached.add((XSuspendContext) args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
