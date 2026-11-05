/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server.definition.launching;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests how the settings and templates of a user-defined language server describe the way LSP4IJ talks to it.
 */
public class UserDefinedTransportTest {

    @Test
    void eachModeIsFoundByItsId() {
        for (var mode : UserDefinedTransport.Mode.values()) {
            assertSame(mode, UserDefinedTransport.Mode.fromId(mode.getId()));
        }
    }

    @Test
    void noIdOrAnUnknownOneIsTheStandardInputAndOutput() {
        assertSame(UserDefinedTransport.Mode.STDIO, UserDefinedTransport.Mode.fromId(null));
        assertSame(UserDefinedTransport.Mode.STDIO, UserDefinedTransport.Mode.fromId("pipe"));
    }

    @Test
    void theStandardInputAndOutputHasNoHostNorPort() {
        assertSame(UserDefinedTransport.STDIO, UserDefinedTransport.of(null, "localhost", 6005));
        assertSame(UserDefinedTransport.STDIO, UserDefinedTransport.of("stdio", null, 0));
    }

    @Test
    void aSocketKeepsItsHostAndPort() {
        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.CONNECT, "localhost", 6005),
                UserDefinedTransport.of("connect", "localhost", 6005));
    }

    @Test
    void onlyARunningLanguageServerIsNotStarted() {
        assertFalse(UserDefinedTransport.of("connect", "localhost", 6005).startsLanguageServer());
        assertTrue(UserDefinedTransport.of("startAndConnect", null, 0).startsLanguageServer());
        assertTrue(UserDefinedTransport.of("startAndAccept", null, 0).startsLanguageServer());
        assertTrue(UserDefinedTransport.STDIO.startsLanguageServer());
    }

    @Test
    void theCommandLineTakesThePortWhereverItHasThePortMacro() {
        assertEquals("server --port 4389 --log server-4389.log",
                UserDefinedTransport.withPort("server --port ${port} --log server-${port}.log", 4389));
        assertEquals("server --stdio", UserDefinedTransport.withPort("server --stdio", 4389));
    }
}
