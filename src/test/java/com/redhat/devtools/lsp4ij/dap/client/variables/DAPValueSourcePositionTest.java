/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.variables;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XNavigatable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests that {@link DAPValue} gives the source position of a value to the navigatable in a read action, also when the
 * position is known later, on a pooled thread: the navigatable of the inline values reads the document of the position.
 */
public class DAPValueSourcePositionTest extends LightPlatformTestCase {

    private final List<XSourcePosition> positions = Collections.synchronizedList(new ArrayList<>());
    private final List<Boolean> readAccess = Collections.synchronizedList(new ArrayList<>());

    public void testThePositionKnownOnAPooledThreadIsGivenInAReadAction() throws Exception {
        var position = position();
        var sourcePosition = new CompletableFuture<XSourcePosition>();
        DAPValue.setSourcePosition(sourcePosition, navigatable());

        // as when the variables context of the stack frame is still loading
        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> sourcePosition.complete(position))
                .get(10, TimeUnit.SECONDS);

        assertEquals(List.of(position), positions);
        assertEquals(List.of(true), readAccess);
    }

    public void testThePositionAlreadyKnownIsGivenAtOnce() {
        var position = position();
        DAPValue.setSourcePosition(CompletableFuture.completedFuture(position), navigatable());

        assertEquals(List.of(position), positions);
        assertEquals(List.of(true), readAccess);
    }

    public void testNoPositionIsNotGiven() throws Exception {
        var sourcePosition = new CompletableFuture<XSourcePosition>();
        DAPValue.setSourcePosition(sourcePosition, navigatable());

        ApplicationManager.getApplication()
                .executeOnPooledThread(() -> sourcePosition.complete(null))
                .get(10, TimeUnit.SECONDS);

        assertEmpty(positions);
    }

    private XNavigatable navigatable() {
        return sourcePosition -> {
            readAccess.add(ApplicationManager.getApplication().isReadAccessAllowed());
            positions.add(sourcePosition);
        };
    }

    private static XSourcePosition position() {
        return XDebuggerUtil.getInstance().createPosition(new LightVirtualFile("focus.py", "total = 0\n"), 0);
    }
}
