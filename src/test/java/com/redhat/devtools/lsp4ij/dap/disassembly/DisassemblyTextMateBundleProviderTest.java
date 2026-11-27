/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.disassembly;

import com.intellij.openapi.util.io.NioFiles;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The TextMate bundle of the disassembly view is copied into the plugin directory, which several test JVMs, or IDEs,
 * can share: each must find complete files, and none may fail because another one copied them first.
 */
public class DisassemblyTextMateBundleProviderTest {

    private static final List<String> FILES = List.of("package.json", "syntaxes/disassembly.json");
    private static final int WRITERS = 8;
    private static final int ROUNDS = 25;
    private static final long TIMEOUT_SECONDS = 30;

    @Test
    public void writersThatCopyTheBundleAtOnceAllSucceedAndLeaveCompleteFiles() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(WRITERS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Path bundle = Files.createTempDirectory("lsp4ij-disassembly-bundle-");
                try {
                    var start = new CyclicBarrier(WRITERS);
                    List<Future<Path>> writers = new ArrayList<>();
                    for (int i = 0; i < WRITERS; i++) {
                        writers.add(executor.submit(() -> {
                            start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            return DisassemblyTextMateBundleProvider.copyResourceDirectory(bundle.toString(), FILES);
                        }));
                    }
                    for (Future<Path> writer : writers) {
                        // Fails with the writer's exception, such as a FileAlreadyExistsException
                        assertEquals(bundle, writer.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    }
                    for (String file : FILES) {
                        assertArrayEquals("the copy of " + file + " in round " + round,
                                resource(file), Files.readAllBytes(bundle.resolve(file)));
                    }
                    try (Stream<Path> files = Files.walk(bundle)) {
                        assertEquals("no temporary file is left in round " + round, List.of(),
                                files.filter(path -> path.toString().endsWith(".tmp")).toList());
                    }
                } finally {
                    NioFiles.deleteRecursively(bundle);
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static byte[] resource(String file) throws IOException {
        try (InputStream resource = Objects.requireNonNull(
                DisassemblyTextMateBundleProviderTest.class.getResourceAsStream("/textmate/" + file))) {
            return resource.readAllBytes();
        }
    }
}
