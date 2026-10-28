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
package com.redhat.devtools.lsp4ij.dap.client.variables.providers;

import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XStackFrame;
import com.redhat.devtools.lsp4ij.internal.capabilities.ClientCapabilitiesFactory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * A language server's 'workspace/inlineValue/refresh' asks again for the inline values of the stopped DAP debug
 * sessions, and the client declares that it handles it, as it does 'workspace/foldingRange/refresh'.
 */
public class InlineValueRefreshTest extends LightPlatformTestCase {

    public void testTheClientDeclaresTheInlineValueAndFoldingRangeRefreshes() {
        var workspace = ClientCapabilitiesFactory.create(null).getWorkspace();
        assertEquals(Boolean.TRUE, workspace.getInlineValue().getRefreshSupport());
        assertEquals(Boolean.TRUE, workspace.getFoldingRange().getRefreshSupport());
    }

    public void testOnlyAStoppedDAPSessionIsRebuilt() {
        var rebuilt = new ArrayList<String>();
        var running = session("running", false, null, rebuilt);
        var otherDebugger = session("other debugger", true, new XStackFrame() { }, rebuilt);
        var noFrame = session("no frame", true, null, rebuilt);

        LSPDebugVariablePositionProvider.refreshInlineValues(List.of(running, otherDebugger, noFrame));

        assertEmpty("A session which isn't stopped in a DAP stack frame shows no inline values of a language server", rebuilt);
    }

    private static XDebugSession session(String name, boolean suspended, XStackFrame currentFrame, List<String> rebuilt) {
        return (XDebugSession) Proxy.newProxyInstance(XDebugSession.class.getClassLoader(), new Class[]{XDebugSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isSuspended" -> suspended;
                    case "getCurrentStackFrame" -> currentFrame;
                    case "rebuildViews" -> {
                        rebuilt.add(name);
                        yield null;
                    }
                    case "toString" -> name;
                    default -> null;
                });
    }
}
