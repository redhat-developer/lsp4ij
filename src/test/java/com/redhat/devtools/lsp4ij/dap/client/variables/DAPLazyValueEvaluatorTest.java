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
import com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback;
import org.eclipse.lsp4j.debug.ValueFormat;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHint;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A lazy DAP variable's value is asked for when the user clicks its link, and then shown in its place. */
public class DAPLazyValueEvaluatorTest extends LightPlatformTestCase {

    private final List<Integer> asked = new ArrayList<>();
    private final List<ValueFormat> formats = new ArrayList<>();
    private final List<String> evaluated = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final List<Variable> resolved = new ArrayList<>();

    public void testOnlyAVariableWithTheLazyHintAndAReferenceIsLazy() {
        assertTrue(DAPLazyValueEvaluator.isLazy(variable("size", "(...)", 12, true)));
        assertFalse(DAPLazyValueEvaluator.isLazy(variable("size", "3", 12, false)));
        assertFalse(DAPLazyValueEvaluator.isLazy(variable("size", "3", 0, true)));
        assertFalse(DAPLazyValueEvaluator.isLazy(new Variable()));
    }

    public void testTheValueReplacesTheLink() {
        var value = variable("size", "3", 0, false);
        var evaluator = new DAPLazyValueEvaluator(server(CompletableFuture.completedFuture(response(value))), 12, null, resolved::add);
        assertFalse(evaluator.isShowValuePopup());

        evaluator.startEvaluation(callback());

        assertEquals(List.of(12), asked);
        assertEquals(List.of(value), resolved);
        assertEquals(List.of("3"), evaluated);
        // the link is gone once the value shows
        assertFalse(evaluator.isEnabled());
    }

    public void testTheValueIsAskedForInTheSessionsFormat() {
        var format = new ValueFormat();
        format.setHex(true);
        var evaluator = new DAPLazyValueEvaluator(server(CompletableFuture.completedFuture(response(variable("size", "0x3", 0, false)))),
                12, format, resolved::add);

        evaluator.startEvaluation(callback());

        assertEquals(List.of(format), formats);
        assertEquals(List.of("0x3"), evaluated);
    }

    public void testNoValueIsAnError() {
        var evaluator = new DAPLazyValueEvaluator(server(CompletableFuture.completedFuture(response())), 12, null, resolved::add);

        evaluator.startEvaluation(callback());

        assertEquals(List.of("The debug adapter returned no value."), errors);
        assertTrue(resolved.isEmpty());
        assertTrue(evaluator.isEnabled());
    }

    public void testAFailedRequestSaysWhy() {
        var evaluator = new DAPLazyValueEvaluator(server(CompletableFuture.failedFuture(new IllegalStateException("getter threw"))), 12, null, resolved::add);

        evaluator.startEvaluation(callback());

        assertEquals(List.of("getter threw"), errors);
        assertTrue(evaluator.isEnabled());
    }

    public void testAClickWhileTheValueLoadsDoesNotAskAgain() {
        var value = variable("size", "3", 0, false);
        var answer = new CompletableFuture<VariablesResponse>();
        var evaluator = new DAPLazyValueEvaluator(server(answer), 12, null, resolved::add);

        evaluator.startEvaluation(callback());
        evaluator.startEvaluation(callback());
        answer.complete(response(value));

        // getting the value may run code: it is asked for once
        assertEquals(List.of(12), asked);
        assertEquals(List.of("3", "3"), evaluated);
    }

    public void testAClickAfterAFailedRequestAsksAgain() {
        var value = variable("size", "3", 0, false);
        var evaluator = new DAPLazyValueEvaluator(server(CompletableFuture.failedFuture(new IllegalStateException("getter threw")),
                CompletableFuture.completedFuture(response(value))), 12, null, resolved::add);

        evaluator.startEvaluation(callback());
        evaluator.startEvaluation(callback());

        assertEquals(List.of(12, 12), asked);
        assertEquals(List.of("getter threw"), errors);
        assertEquals(List.of("3"), evaluated);
        assertFalse(evaluator.isEnabled());
    }

    private static Variable variable(String name, String value, int reference, boolean lazy) {
        var variable = new Variable();
        variable.setName(name);
        variable.setValue(value);
        variable.setVariablesReference(reference);
        var hint = new VariablePresentationHint();
        hint.setLazy(lazy);
        variable.setPresentationHint(hint);
        return variable;
    }

    private static VariablesResponse response(Variable... variables) {
        var response = new VariablesResponse();
        response.setVariables(variables);
        return response;
    }

    /**
     * Returns a debug adapter which gives the answers in turn, the last one to every request after it.
     */
    @SafeVarargs
    private IDebugProtocolServer server(CompletableFuture<VariablesResponse>... answers) {
        return (IDebugProtocolServer) Proxy.newProxyInstance(IDebugProtocolServer.class.getClassLoader(),
                new Class[]{IDebugProtocolServer.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "variables" -> {
                        var variablesArgs = (VariablesArguments) args[0];
                        asked.add(variablesArgs.getVariablesReference());
                        formats.add(variablesArgs.getFormat());
                        yield answers[Math.min(asked.size(), answers.length) - 1];
                    }
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }

    private XFullValueEvaluationCallback callback() {
        return (XFullValueEvaluationCallback) Proxy.newProxyInstance(XFullValueEvaluationCallback.class.getClassLoader(),
                new Class[]{XFullValueEvaluationCallback.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "evaluated" -> {
                        evaluated.add((String) args[0]);
                        yield null;
                    }
                    case "errorOccurred" -> {
                        errors.add((String) args[0]);
                        yield null;
                    }
                    case "isObsolete" -> false;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> throw new AssertionError(method.getName());
                });
    }
}
