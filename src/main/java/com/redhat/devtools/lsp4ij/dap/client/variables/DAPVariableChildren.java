/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.variables;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.eclipse.lsp4j.debug.ValueFormat;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

/**
 * Loads the children of a DAP variable into a tree node. A variable with no more indexed children
 * (indexedVariables) than a page has all of its children asked for at once. A larger one, such as a large array, has
 * its named children asked for first, if it says it has any (namedVariables), and then its indexed ones a page at a
 * time: the node shows a page, then IntelliJ's "..." node with how many more there are, which asks for the next page
 * when the user opens it.
 */
public class DAPVariableChildren {

    /**
     * How many indexed children are asked for at a time, as many as IntelliJ advises a tree node to show.
     */
    public static final int PAGE_SIZE = XCompositeNode.MAX_CHILDREN_TO_SHOW;

    private final @NotNull IDebugProtocolServer server;
    private final @NotNull Variable variable;
    private final @Nullable ValueFormat format;
    private final @NotNull Function<Variable, XValue> values;

    /**
     * @param server   the debug adapter.
     * @param variable the variable whose children are loaded.
     * @param format   the format the values are asked for in, or null for the debug adapter's own.
     * @param values   what shows a child variable in the tree.
     */
    public DAPVariableChildren(@NotNull IDebugProtocolServer server,
                               @NotNull Variable variable,
                               @Nullable ValueFormat format,
                               @NotNull Function<Variable, XValue> values) {
        this.server = server;
        this.variable = variable;
        this.format = format;
        this.values = values;
    }

    /**
     * Loads the variable's children into the node.
     *
     * @param node the variable's node.
     */
    public void load(@NotNull XCompositeNode node) {
        Integer indexed = variable.getIndexedVariables();
        if (indexed == null || indexed <= PAGE_SIZE) {
            request(null, null, null)
                    .thenAccept(children -> node.addChildren(list(children), true))
                    .exceptionally(error -> failed(node, error));
            return;
        }

        Integer named = variable.getNamedVariables();
        CompletableFuture<Variable[]> namedChildren = named != null && named > 0 ?
                request(VariablesArgumentsFilter.NAMED, null, null) :
                CompletableFuture.completedFuture(new Variable[0]);
        namedChildren
                .thenAccept(children -> {
                    if (children.length > 0) {
                        node.addChildren(list(children), false);
                    }
                    page(node, 0, indexed);
                })
                .exceptionally(error -> failed(node, error));
    }

    /**
     * Loads the page of indexed children from start, and, if there are more, IntelliJ's "..." node, which loads the
     * next page.
     */
    private void page(@NotNull XCompositeNode node, int start, int total) {
        if (node.isObsolete()) {
            return;
        }
        int count = Math.min(PAGE_SIZE, total - start);
        request(VariablesArgumentsFilter.INDEXED, start, count)
                .thenAccept(children -> {
                    int next = start + count;
                    boolean last = next >= total;
                    node.addChildren(list(children), last);
                    if (!last) {
                        node.tooManyChildren(total - next, () -> page(node, next, total));
                    }
                })
                .exceptionally(error -> failed(node, error));
    }

    private @NotNull CompletableFuture<Variable[]> request(@Nullable VariablesArgumentsFilter filter,
                                                            @Nullable Integer start,
                                                            @Nullable Integer count) {
        VariablesArguments args = new VariablesArguments();
        args.setVariablesReference(variable.getVariablesReference());
        args.setFormat(format);
        args.setFilter(filter);
        args.setStart(start);
        args.setCount(count);
        return server.variables(args)
                .thenApply(response -> response != null && response.getVariables() != null ?
                        response.getVariables() : new Variable[0]);
    }

    private @NotNull XValueChildrenList list(@NotNull Variable[] children) {
        XValueChildrenList list = new XValueChildrenList();
        for (Variable child : children) {
            list.add(child.getName(), values.apply(child));
        }
        return list;
    }

    private static Void failed(@NotNull XCompositeNode node, @NotNull Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        node.setErrorMessage(cause.getMessage() != null ? cause.getMessage() : cause.toString());
        return null;
    }
}
