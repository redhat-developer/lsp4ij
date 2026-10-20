/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns temporary document connections for one request. Closing the scope releases only
 * its claims; an editor, another scope, or an ordinary feature can retain a connection.
 */
public final class DocumentConnectionScope implements AutoCloseable {

    private final List<Runnable> releases = new ArrayList<>();
    private boolean closed;

    void register(Runnable release) {
        synchronized (this) {
            if (!closed) {
                releases.add(release);
                return;
            }
        }
        release.run();
    }

    @Override
    public void close() {
        List<Runnable> pending;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            pending = List.copyOf(releases);
            releases.clear();
        }
        pending.forEach(Runnable::run);
    }
}
