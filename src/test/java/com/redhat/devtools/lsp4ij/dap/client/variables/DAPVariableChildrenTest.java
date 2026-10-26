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

import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.eclipse.lsp4j.debug.ValueFormat;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/** A large DAP variable's indexed children are asked for a page at a time; a small one's all at once. */
public class DAPVariableChildrenTest extends LightPlatformTestCase {

    private final List<VariablesArguments> asked = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private final List<Runnable> more = new ArrayList<>();
    private boolean failing;

    public void testAVariableWithoutManyIndexedChildrenHasThemAllAskedForAtOnce() {
        children(variable(null, null)).load(node());

        assertEquals(1, asked.size());
        assertNull(asked.get(0).getFilter());
        assertNull(asked.get(0).getStart());
        assertNull(asked.get(0).getCount());
        assertEquals(List.of("add 3 last"), calls);

        // a page's worth of indexed children is asked for at once too
        asked.clear();
        calls.clear();
        children(variable(DAPVariableChildren.PAGE_SIZE, null)).load(node());
        assertEquals(1, asked.size());
        assertNull(asked.get(0).getFilter());
    }

    public void testALargeVariablesIndexedChildrenAreAskedForAPageAtATime() {
        children(variable(250, null)).load(node());

        // the first page, then IntelliJ's "..." node with how many more there are
        assertEquals(List.of("indexed 0 100"), describe(asked));
        assertEquals(List.of("add 100", "more 150"), calls);

        more.remove(0).run();
        assertEquals(List.of("indexed 0 100", "indexed 100 100"), describe(asked));
        assertEquals(List.of("add 100", "more 150", "add 100", "more 50"), calls);

        // the last page ends the children
        more.remove(0).run();
        assertEquals(List.of("indexed 0 100", "indexed 100 100", "indexed 200 50"), describe(asked));
        assertEquals(List.of("add 100", "more 150", "add 100", "more 50", "add 50 last"), calls);
        assertTrue(more.isEmpty());
    }

    public void testALargeVariablesNamedChildrenAreAskedForFirstWhenItHasSome() {
        children(variable(250, 2)).load(node());

        assertEquals(List.of("named", "indexed 0 100"), describe(asked));
        assertEquals(List.of("add 2", "add 100", "more 150"), calls);
    }

    public void testTheValuesFormatIsAskedForEachTime() {
        var format = new ValueFormat();
        format.setHex(true);
        new DAPVariableChildren(server(), variable(250, 2), format, child -> value()).load(node());

        assertEquals(2, asked.size());
        assertTrue(asked.stream().allMatch(args -> args.getFormat() == format));
    }

    public void testAFailedRequestShowsWhy() {
        failing = true;
        children(variable(250, null)).load(node());

        assertEquals(List.of("error no variables"), calls);
    }

    private DAPVariableChildren children(Variable variable) {
        return new DAPVariableChildren(server(), variable, null, child -> value());
    }

    private static Variable variable(Integer indexed, Integer named) {
        var variable = new Variable();
        variable.setName("squares");
        variable.setValue("Int[250]");
        variable.setVariablesReference(7);
        variable.setIndexedVariables(indexed);
        variable.setNamedVariables(named);
        return variable;
    }

    /**
     * @return a debug adapter which answers as many children as asked for: 3 for all of them, 2 named ones, or the
     * indexed ones from start
     */
    private IDebugProtocolServer server() {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("variables")) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                    var arguments = (VariablesArguments) args[0];
                    asked.add(arguments);
                    if (failing) {
                        return CompletableFuture.failedFuture(new IllegalStateException("no variables"));
                    }
                    int start = arguments.getStart() != null ? arguments.getStart() : 0;
                    int count = arguments.getFilter() == VariablesArgumentsFilter.INDEXED ? arguments.getCount()
                            : arguments.getFilter() == VariablesArgumentsFilter.NAMED ? 2 : 3;
                    var response = new VariablesResponse();
                    response.setVariables(IntStream.range(start, start + count).mapToObj(i -> {
                        var child = new Variable();
                        child.setName("[" + i + "]");
                        child.setValue(Integer.toString(i * i));
                        return child;
                    }).toArray(Variable[]::new));
                    return CompletableFuture.completedFuture(response);
                });
    }

    /**
     * @return a tree node which records the children it is given, how many more there are, and why it failed
     */
    private XCompositeNode node() {
        return (XCompositeNode) Proxy.newProxyInstance(XCompositeNode.class.getClassLoader(),
                new Class[]{XCompositeNode.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "addChildren" -> {
                        calls.add("add " + ((XValueChildrenList) args[0]).size() + ((boolean) args[1] ? " last" : ""));
                        yield null;
                    }
                    case "tooManyChildren" -> {
                        calls.add("more " + args[0]);
                        if (args.length > 1) {
                            more.add((Runnable) args[1]);
                        }
                        yield null;
                    }
                    case "setErrorMessage" -> {
                        calls.add("error " + args[0]);
                        yield null;
                    }
                    case "isObsolete" -> false;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static List<String> describe(List<VariablesArguments> asked) {
        return asked.stream().map(args -> args.getFilter() == null ? "all"
                : args.getFilter() == VariablesArgumentsFilter.NAMED ? "named"
                : "indexed " + args.getStart() + " " + args.getCount()).toList();
    }

    private static XValue value() {
        return new XValue() {
            @Override
            public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
            }
        };
    }
}
