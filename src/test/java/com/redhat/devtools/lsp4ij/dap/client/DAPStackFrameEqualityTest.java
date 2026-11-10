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
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.StackFrame;

/**
 * Tests the object by which the Variables view recognizes a stack frame, to restore its expanded nodes.
 */
public class DAPStackFrameEqualityTest extends TestCase {

    public void testSameFunctionAfterStopOrStep() {
        // the frame id changes on each stop, the line on each step
        assertEquals(DAPStackFrame.getEqualityObject(frame(1, "inspectEdits", 24, pathSource("/a/set-variable.js"))),
                DAPStackFrame.getEqualityObject(frame(7, "inspectEdits", 26, pathSource("/a/set-variable.js"))));
    }

    public void testOtherFunction() {
        assertFalse(DAPStackFrame.getEqualityObject(frame(1, "inspectEdits", 24, pathSource("/a/set-variable.js")))
                .equals(DAPStackFrame.getEqualityObject(frame(1, "global code", 24, pathSource("/a/set-variable.js")))));
    }

    public void testOtherSource() {
        assertFalse(DAPStackFrame.getEqualityObject(frame(1, "main", 3, pathSource("/a/one.js")))
                .equals(DAPStackFrame.getEqualityObject(frame(1, "main", 3, pathSource("/a/two.js")))));
    }

    public void testSourceReference() {
        assertEquals(DAPStackFrame.getEqualityObject(frame(1, "main", 3, referenceSource(5))),
                DAPStackFrame.getEqualityObject(frame(2, "main", 4, referenceSource(5))));
        assertFalse(DAPStackFrame.getEqualityObject(frame(1, "main", 3, referenceSource(5)))
                .equals(DAPStackFrame.getEqualityObject(frame(1, "main", 3, referenceSource(6)))));
    }

    public void testWithoutSource() {
        assertEquals(DAPStackFrame.getEqualityObject(frame(1, "main", 3, null)),
                DAPStackFrame.getEqualityObject(frame(2, "main", 4, null)));
    }

    private static StackFrame frame(int id, String name, int line, Source source) {
        StackFrame frame = new StackFrame();
        frame.setId(id);
        frame.setName(name);
        frame.setLine(line);
        frame.setSource(source);
        return frame;
    }

    private static Source pathSource(String path) {
        Source source = new Source();
        source.setPath(path);
        source.setName(path.substring(path.lastIndexOf('/') + 1));
        return source;
    }

    private static Source referenceSource(int sourceReference) {
        Source source = new Source();
        source.setName("eval");
        source.setSourceReference(sourceReference);
        return source;
    }
}
