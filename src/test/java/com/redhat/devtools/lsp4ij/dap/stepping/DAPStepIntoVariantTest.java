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
package com.redhat.devtools.lsp4ij.dap.stepping;

import com.intellij.testFramework.LightPlatformTestCase;
import org.eclipse.lsp4j.debug.StepInTarget;
import org.jetbrains.annotations.NotNull;

/**
 * Unit tests for DAPStepIntoVariant.
 */
public class DAPStepIntoVariantTest extends LightPlatformTestCase {

    public void testGetText() {
        StepInTarget target = createTarget(1, "foo(x, y)");
        DAPStepIntoVariant variant = new DAPStepIntoVariant(target, null, 0, 0);

        assertEquals("foo(x, y)", variant.getText());
    }

    public void testGetTargetId() {
        StepInTarget target = createTarget(42, "bar()");
        DAPStepIntoVariant variant = new DAPStepIntoVariant(target, null, 0, 0);

        assertEquals(42, variant.getTargetId());
    }

    public void testGetHighlightRange_withNullDocument() {
        StepInTarget target = createTarget(1, "foo()");
        target.setLine(1);
        target.setColumn(5);

        DAPStepIntoVariant variant = new DAPStepIntoVariant(target, null, 0, 0);
        var range = variant.getHighlightRange();

        assertNull(range);
    }

    public void testGetTarget() {
        StepInTarget target = createTarget(10, "test()");
        DAPStepIntoVariant variant = new DAPStepIntoVariant(target, null, 0, 0);

        assertSame(target, variant.getTarget());
    }

    @NotNull
    private static StepInTarget createTarget(int id, String label) {
        StepInTarget target = new StepInTarget();
        target.setId(id);
        target.setLabel(label);
        return target;
    }
}

