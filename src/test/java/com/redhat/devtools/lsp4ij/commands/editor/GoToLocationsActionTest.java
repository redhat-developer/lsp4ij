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
package com.redhat.devtools.lsp4ij.commands.editor;

import com.google.gson.JsonPrimitive;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link GoToLocationsAction#resolveMode(Object)}.
 */
public class GoToLocationsActionTest {

    @Test
    public void jsonPrimitivePeekIsResolved() {
        // Command arguments are Gson JSON elements: the mode arrives as a JsonPrimitive.
        Assert.assertEquals("peek", GoToLocationsAction.resolveMode(new JsonPrimitive("peek")));
    }

    @Test
    public void jsonPrimitiveGotoAndPeekIsResolved() {
        Assert.assertEquals("gotoAndPeek", GoToLocationsAction.resolveMode(new JsonPrimitive("gotoAndPeek")));
    }

    @Test
    public void jsonPrimitiveGotoIsResolved() {
        Assert.assertEquals("goto", GoToLocationsAction.resolveMode(new JsonPrimitive("goto")));
    }

    @Test
    public void alreadyDecodedStringIsResolved() {
        Assert.assertEquals("peek", GoToLocationsAction.resolveMode("peek"));
    }

    @Test
    public void nullArgumentDefaultsToGoto() {
        Assert.assertEquals("goto", GoToLocationsAction.resolveMode(null));
    }

    @Test
    public void nonStringPrimitiveDefaultsToGoto() {
        Assert.assertEquals("goto", GoToLocationsAction.resolveMode(new JsonPrimitive(42)));
    }
}
