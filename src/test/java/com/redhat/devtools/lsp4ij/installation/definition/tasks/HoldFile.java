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

/**
 * A program which holds the file given as its argument for two seconds, and fails if another program holds it already,
 * as two {@code npm install} in one directory break each other's install: of two exec tasks that run it at the same
 * time with the same file, one fails.
 */
public final class HoldFile {

    private HoldFile() {
    }

    public static void main(String[] args) throws Exception {
        Path file = Path.of(args[0]);
        try {
            Files.createFile(file);
        } catch (FileAlreadyExistsException e) {
            System.exit(1);
        }
        try {
            Thread.sleep(2000);
        } finally {
            Files.delete(file);
        }
    }
}
