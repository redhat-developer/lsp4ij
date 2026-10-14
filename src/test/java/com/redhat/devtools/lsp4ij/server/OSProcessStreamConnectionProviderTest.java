/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightPlatformTestCase;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class OSProcessStreamConnectionProviderTest extends LightPlatformTestCase {

    public void testStopBeforeStartRejectsProcessCreation() throws Exception {
        var provider = provider();
        try {
            provider.stop();
            assertThrows(CannotStartProcessException.class, provider::start);
            assertNull(provider.getPid());
        } finally {
            reap(provider);
        }
    }

    public void testStartedProcessIsReapedByStop() throws Exception {
        var provider = provider();
        try {
            provider.start();
            Process process = provider.getProcessHandler().getProcess();
            assertTrue(process.isAlive());
            provider.stop();
            awaitExit(process);
            assertFalse(process.isAlive());
            provider.stop();
            assertThrows(CannotStartProcessException.class, provider::start);
        } finally {
            reap(provider);
        }
    }

    public void testRepeatedStartCannotReplaceAnOwnedProcess() throws Exception {
        var provider = provider();
        Process first = null;
        try {
            provider.start();
            first = provider.getProcessHandler().getProcess();
            assertThrows(CannotStartProcessException.class, provider::start);
            assertSame(first, provider.getProcessHandler().getProcess());
        } finally {
            try {
                reap(provider);
            } finally {
                if (first != null && first.isAlive()) {
                    first.destroyForcibly();
                    awaitExit(first);
                }
            }
        }
    }

    private static OSProcessStreamConnectionProvider provider() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = PathManager.getJarPathForClass(WaitingServer.class);
        return new OSProcessStreamConnectionProvider(
                new GeneralCommandLine(java, "-cp", classes, WaitingServer.class.getName()));
    }

    private static void reap(OSProcessStreamConnectionProvider provider) throws Exception {
        provider.stop();
        var handler = provider.getProcessHandler();
        // Also clean up the orphan when this regression is run against the unfixed provider.
        if (handler != null) {
            var process = handler.getProcess();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            awaitExit(process);
        }
    }

    private static void awaitExit(Process process) throws Exception {
        // Wait for actual termination; the deadline only prevents a broken test from hanging CI.
        process.onExit().get(30, TimeUnit.SECONDS);
    }

    public static class WaitingServer {
        public static void main(String[] args) throws Exception {
            System.in.read();
        }
    }
}
