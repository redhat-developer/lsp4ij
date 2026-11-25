/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * A program which holds the file given as its first argument until the file given as its second argument exists, which
 * the test creates once the installation which runs it is to end, and fails if another program holds it already, as
 * two {@code npm install} in one directory break each other's install: of two exec tasks that run it at the same time
 * with the same file, one fails.
 * <p>
 * Since it holds the file until the test releases it, and not for a while, the test decides when the installation
 * which runs it ends, however slow the machine is. It ends with the exit code 2 if the test doesn't release it within
 * a minute, so that it doesn't outlive a test which failed.
 */
public final class HoldFile {

    // How long the program holds a file at most, if the test doesn't release it
    private static final long MAX_HOLD_NANOS = TimeUnit.SECONDS.toNanos(60);
    // How often the program looks for the file which releases it
    private static final long POLL_MILLIS = 10;

    private HoldFile() {
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        try {
            Files.createFile(file);
        } catch (FileAlreadyExistsException e) {
            System.exit(1);
        }
        boolean released;
        try {
            released = awaitRelease(Path.of(args[1]));
        } finally {
            Files.delete(file);
        }
        if (!released) {
            System.exit(2);
        }
    }

    /**
     * Waits until the given file, which the test creates to release the program, exists, and returns true then, or
     * false if it doesn't exist within a minute.
     */
    static boolean awaitRelease(Path release) throws InterruptedException {
        for (long end = System.nanoTime() + MAX_HOLD_NANOS; !Files.exists(release); ) {
            if (System.nanoTime() - end >= 0) {
                return false;
            }
            Thread.sleep(POLL_MILLIS);
        }
        return true;
    }
}
