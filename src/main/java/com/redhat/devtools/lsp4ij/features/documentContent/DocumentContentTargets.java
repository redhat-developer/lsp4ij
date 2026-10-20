/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentContent;

import org.eclipse.lsp4j.CallHierarchyIncomingCall;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCall;
import org.eclipse.lsp4j.DocumentLink;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolLocation;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Preloads only protocol navigation targets, never arbitrary strings or edit/completion data. */
public final class DocumentContentTargets {

    private DocumentContentTargets() {
    }

    public static <T> CompletableFuture<T> prepare(T result, DocumentContentProvider provider) {
        Set<String> uris = new HashSet<>();
        collect(result, uris);
        if (uris.isEmpty()) {
            return CompletableFuture.completedFuture(result);
        }
        var pending = uris.stream().map(provider::resolve).toArray(CompletableFuture[]::new);
        // Missing server content must not discard ordinary locations in a mixed response.
        return CompletableFuture.allOf(pending).handle((ignored, error) -> result);
    }

    private static void collect(Object value, Set<String> uris) {
        if (value instanceof Collection<?> items) {
            items.forEach(item -> collect(item, uris));
        } else if (value instanceof Either<?, ?> either) {
            collect(either.isLeft() ? either.getLeft() : either.getRight(), uris);
        } else if (value instanceof Location location) {
            add(location.getUri(), uris);
        } else if (value instanceof LocationLink location) {
            add(location.getTargetUri(), uris);
        } else if (value instanceof SymbolInformation symbol) {
            collect(symbol.getLocation(), uris);
        } else if (value instanceof WorkspaceSymbol symbol) {
            collect(symbol.getLocation(), uris);
        } else if (value instanceof WorkspaceSymbolLocation location) {
            add(location.getUri(), uris);
        } else if (value instanceof TypeHierarchyItem item) {
            add(item.getUri(), uris);
        } else if (value instanceof CallHierarchyItem item) {
            add(item.getUri(), uris);
        } else if (value instanceof CallHierarchyIncomingCall call) {
            collect(call.getFrom(), uris);
        } else if (value instanceof CallHierarchyOutgoingCall call) {
            collect(call.getTo(), uris);
        } else if (value instanceof DocumentLink link) {
            add(link.getTarget(), uris);
        }
    }

    private static void add(String uri, Set<String> uris) {
        if (uri != null) {
            uris.add(uri);
        }
    }
}
