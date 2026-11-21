/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A program which acts as another IDE that installs into a working directory: it locks the lock file given as its
 * first argument, as an exec task of that IDE does, holds the file given as its second argument for two seconds, as
 * {@link HoldFile} does, and prints {@code locked} once it holds both.
 */
public final class HoldLock {

    private HoldLock() {
    }

    public static void main(String[] args) throws Exception {
        Path lockFile = Path.of(args[0]);
        Files.createDirectories(lockFile.getParent());
        try (var channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            Path held = Files.createFile(Path.of(args[1]));
            System.out.println("locked");
            System.out.flush();
            try {
                Thread.sleep(2000);
            } finally {
                Files.delete(held);
            }
        }
    }
}
