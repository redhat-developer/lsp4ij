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

import junit.framework.TestCase;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopePresentationHint;

/**
 * Tests which scope of a stack frame is expanded when its variables are shown.
 */
public class DAPStackFrameScopesTest extends TestCase {

    public void testLocalsScopeIsExpanded() {
        Scope arguments = scope("Arguments", ScopePresentationHint.ARGUMENTS);
        Scope locals = scope("Locals", ScopePresentationHint.LOCALS);
        assertSame(locals, DAPStackFrame.getAutoExpandedScope(new Scope[]{arguments, locals}));
    }

    public void testFirstScopeIsExpandedWithoutLocals() {
        Scope globals = scope("Globals", null);
        Scope registers = scope("Registers", ScopePresentationHint.REGISTERS);
        assertSame(globals, DAPStackFrame.getAutoExpandedScope(new Scope[]{globals, registers}));
    }

    public void testNoScopeIsExpandedWithoutScopes() {
        assertNull(DAPStackFrame.getAutoExpandedScope(new Scope[0]));
    }

    private static Scope scope(String name, String presentationHint) {
        Scope scope = new Scope();
        scope.setName(name);
        scope.setPresentationHint(presentationHint);
        return scope;
    }
}
