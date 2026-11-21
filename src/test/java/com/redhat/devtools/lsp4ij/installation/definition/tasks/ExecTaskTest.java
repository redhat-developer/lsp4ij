/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.intellij.openapi.util.io.NioFiles;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTaskRegistry;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the installers of two language servers which install into the same directory, as the CSS and ESLint
 * language servers, which share their npm package, run their commands there one at a time (#1960).
 */
public class ExecTaskTest {

    @BeforeClass
    public static void initPlatform() {
        TestApplicationManager.getInstance();
    }

    @Test
    public void testTwoInstallersWhichRunACommandInTheSameDirectoryRunThereOneAtATime() throws Exception {
        Path directory = Files.createTempDirectory("lsp4ij-exec");
        var executor = Executors.newFixedThreadPool(2);
        try {
            // Each command holds the same file for two seconds, and fails if the other one holds it
            String held = directory.resolve("installing").toString();
            var start = new CountDownLatch(1);
            List<Future<Boolean>> runs = IntStream.range(0, 2)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        var context = new InstallerContext(null, InstallerContext.InstallerAction.RUN);
                        return createTask(held, directory).run(context);
                    }))
                    .toList();
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (var run : runs) {
                results.add(run.get(60, TimeUnit.SECONDS));
            }
            assertEquals(List.of(true, true), results, "the two commands must not run in the directory at the same time");
        } finally {
            executor.shutdownNow();
            NioFiles.deleteRecursively(directory);
        }
    }

    /**
     * Returns an exec task which runs {@link HoldFile} with the given file, in the given working directory.
     */
    private static @NotNull ExecTask createTask(@NotNull String held, @NotNull Path workingDir) throws Exception {
        String java = ProcessHandle.current().info().command().orElseThrow();
        // The directory of the compiled test classes, above the folders of the package of HoldFile
        Path classes = Path.of(HoldFile.class.getResource("HoldFile.class").toURI());
        for (int i = HoldFile.class.getName().split("\\.").length; i > 0; i--) {
            classes = classes.getParent();
        }
        var descriptor = new ServerInstallerDescriptor("test", false, new InstallerTaskRegistry());
        return new ExecTask(null, "install", null, null,
                List.of(java, "-cp", classes.toString(), HoldFile.class.getName(), held),
                workingDir.toString(), null, true, descriptor);
    }
}
