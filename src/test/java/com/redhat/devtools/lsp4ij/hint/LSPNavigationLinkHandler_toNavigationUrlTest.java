/*******************************************************************************
 * Copyright (c) 2026 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.hint;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link LSPNavigationLinkHandler#toNavigationUrl(Location)}.
 */
public class LSPNavigationLinkHandler_toNavigationUrlTest {

    @Test
    public void testLineIsOneBased() {
        // LSP positions are 0-based, the #L fragment must be 1-based
        // (see LSPIJUtils.openInEditor which parses it back with line - 1)
        Location location = new Location("file:///foo/bar.txt",
                new Range(new Position(3, 9), new Position(3, 10)));
        Assert.assertEquals("#lsp-navigation/file:///foo/bar.txt#L4:9",
                LSPNavigationLinkHandler.toNavigationUrl(location));
    }
}
