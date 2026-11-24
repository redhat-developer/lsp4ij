/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.download;

import org.jetbrains.annotations.NotNull;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Records what the installers log, at every level, as the IDE's log would have it with
 * {@code #com.redhat.devtools.lsp4ij.installation} in Debug Log Settings. LSP4IJ logs with SLF4J, which IntelliJ binds
 * to java.util.logging.
 */
public final class LogRecorder implements AutoCloseable {

    private static final String CATEGORY = "com.redhat.devtools.lsp4ij.installation";

    // Held, so that the level set on it stays
    private final @NotNull Logger logger = Logger.getLogger(CATEGORY);
    private final Level level = logger.getLevel();
    private final @NotNull List<String> lines = new CopyOnWriteArrayList<>();
    private final @NotNull Handler handler = new Handler() {
        @Override
        public void publish(LogRecord record) {
            StringWriter thrown = new StringWriter();
            if (record.getThrown() != null) {
                thrown.append('\n');
                record.getThrown().printStackTrace(new PrintWriter(thrown));
            }
            lines.add(record.getLevel() + " " + record.getMessage() + thrown);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };

    public LogRecorder() {
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
    }

    /**
     * Returns the records, as {@code INFO message}, with the stack trace of their exception, if any.
     */
    public @NotNull List<String> lines() {
        return List.copyOf(lines);
    }

    public @NotNull String output() {
        return String.join("\n", lines);
    }

    @Override
    public void close() {
        logger.removeHandler(handler);
        logger.setLevel(level);
    }
}
