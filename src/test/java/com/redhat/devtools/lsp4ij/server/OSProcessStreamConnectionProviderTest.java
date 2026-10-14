/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.server;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.LightPlatformTestCase;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
            process.onExit().get(10, TimeUnit.SECONDS);
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
            reap(provider);
            if (first != null && first.isAlive()) {
                first.destroyForcibly().onExit().get(10, TimeUnit.SECONDS);
            }
        }
    }

    public void testStopDuringStartReapsTheCreatedProcess() throws Exception {
        var creating = new CountDownLatch(1);
        var continueStart = new CountDownLatch(1);
        var stopping = new CountDownLatch(1);
        var provider = new OSProcessStreamConnectionProvider(new GeneralCommandLine(commandLine()) {
            @Override
            public Process createProcess() throws ExecutionException {
                creating.countDown();
                try {
                    if (!continueStart.await(10, TimeUnit.SECONDS)) {
                        throw new ExecutionException("Timed out waiting to continue process creation");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ExecutionException(e);
                }
                return super.createProcess();
            }
        });
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = executor.submit(() -> {
                provider.start();
                return null;
            });
            assertTrue(creating.await(10, TimeUnit.SECONDS));
            var stop = executor.submit(() -> {
                stopping.countDown();
                provider.stop();
            });
            assertTrue(stopping.await(10, TimeUnit.SECONDS));
            // Stop must not return while startup can still create an unowned process.
            assertThrows(TimeoutException.class, () -> stop.get(100, TimeUnit.MILLISECONDS));
            continueStart.countDown();
            start.get(10, TimeUnit.SECONDS);
            stop.get(10, TimeUnit.SECONDS);
            Process process = provider.getProcessHandler().getProcess();
            process.onExit().get(10, TimeUnit.SECONDS);
            assertFalse(process.isAlive());
        } finally {
            continueStart.countDown();
            executor.shutdown();
            try {
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            } finally {
                reap(provider);
            }
        }
    }

    private static OSProcessStreamConnectionProvider provider() {
        return new OSProcessStreamConnectionProvider(commandLine());
    }

    private static GeneralCommandLine commandLine() {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classes = PathManager.getJarPathForClass(WaitingServer.class);
        return new GeneralCommandLine(java, "-cp", classes, WaitingServer.class.getName());
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
            process.onExit().get(10, TimeUnit.SECONDS);
        }
    }

    public static class WaitingServer {
        public static void main(String[] args) throws Exception {
            System.in.read();
        }
    }
}
