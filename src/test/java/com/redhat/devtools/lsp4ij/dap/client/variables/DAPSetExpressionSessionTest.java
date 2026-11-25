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

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.TestActionEvent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XFullValueEvaluator.XFullValueEvaluationCallback;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.DAPDebugProcess;
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider;
import com.redhat.devtools.lsp4ij.dap.DebugMode;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfiguration;
import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.CapabilitiesEventArguments;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.Scope;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.ScopesResponse;
import org.eclipse.lsp4j.debug.SetExpressionArguments;
import org.eclipse.lsp4j.debug.SetExpressionResponse;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.SetVariableResponse;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.StoppedEventArgumentsReason;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadsResponse;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHint;
import org.eclipse.lsp4j.debug.VariablePresentationHintAttributes;
import org.eclipse.lsp4j.debug.VariablePresentationHintKind;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesResponse;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * "Set Value…" (F2) with the 'setExpression' request in a debug session, against a DAP server in the test's process
 * which the session attaches to. An Evaluate result has no parent 'variablesReference' for 'setVariable' to name: it is
 * set by assigning to the evaluated expression. A variable is set with 'setExpression' of its 'evaluateName' only when
 * the DAP server can't set variables.
 */
public class DAPSetExpressionSessionTest extends HeavyPlatformTestCase {

    /**
     * Bounds the waits for the session and the DAP server, so that a test fails instead of hanging.
     */
    private static final int BOUND_SECONDS = 120;
    private static final int FRAME_ID = 7;
    private static final int LOCALS_REFERENCE = 1;
    private static final int LAZY_REFERENCE = 2;
    private static final int NEW_ANSWER_REFERENCE = 3;
    private static final int LAZY_READ_ONLY_REFERENCE = 4;
    private static final int LAZY_WITHOUT_EVALUATE_NAME_REFERENCE = 5;

    private AnswerServer server;
    private XDebugSession session;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (session != null && !session.isStopped()) {
                session.stop();
            }
            if (server != null) {
                server.close();
            }
        } finally {
            super.tearDown();
        }
    }

    public void testSetValueOfAnEvaluateResultSendsSetExpressionOfTheEvaluatedExpression() throws Exception {
        debug(new AnswerServer(true, true));
        var result = evaluate("answer");
        assertNull("An Evaluate result has no parent", result.getParentVariablesReference());

        assertTrue("\"Set Value…\" is offered for an Evaluate result", isSetValueEnabled(node(result)));
        assertNull("The value is set", setValue(result, "42"));

        var args = server.setExpressions.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setExpression'", args);
        assertEquals("answer", args.getExpression());
        assertEquals("42", args.getValue());
        assertEquals(Integer.valueOf(FRAME_ID), args.getFrameId());
        assertNull("The value is asked for in the DAP server's own format", args.getFormat());
        assertEquals("42", result.getShownVariable().getValue());
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));
    }

    public void testTheNewValueOfAnEvaluateResultIsShownWithItsChildren() throws Exception {
        debug(new AnswerServer(true, true));
        server.setExpressionAnswer = args -> {
            var hint = new VariablePresentationHint();
            hint.setKind(VariablePresentationHintKind.CLASS);
            var response = new SetExpressionResponse();
            response.setValue("Answer{42}");
            response.setType("Answer");
            response.setVariablesReference(NEW_ANSWER_REFERENCE);
            response.setNamedVariables(1);
            response.setIndexedVariables(0);
            response.setPresentationHint(hint);
            response.setMemoryReference("0x2a");
            response.setValueLocationReference(5);
            return CompletableFuture.completedFuture(response);
        };
        var result = evaluate("answer");
        assertEquals("No children before", 0, result.getShownVariable().getVariablesReference());

        assertNull("The value is set", setValue(result, "new Answer(42)"));

        var shown = result.getShownVariable();
        assertEquals("Answer{42}", shown.getValue());
        assertEquals("Answer", shown.getType());
        assertEquals(NEW_ANSWER_REFERENCE, shown.getVariablesReference());
        assertEquals(Integer.valueOf(1), shown.getNamedVariables());
        assertEquals(Integer.valueOf(0), shown.getIndexedVariables());
        assertEquals(VariablePresentationHintKind.CLASS, shown.getPresentationHint().getKind());
        assertEquals("0x2a", shown.getMemoryReference());
        assertEquals(Integer.valueOf(5), shown.getValueLocationReference());
        // the children of the new value load with its 'variablesReference'
        server.variablesReferences.clear();
        var children = children(result);
        assertEquals(List.of(NEW_ANSWER_REFERENCE), variablesRequestsOtherThanLocals());
        assertEquals(1, children.size());
        assertEquals("value", children.getName(0));
        assertEquals(Integer.valueOf(NEW_ANSWER_REFERENCE), ((DAPValue) children.getValue(0)).getParentVariablesReference());
    }

    public void testSetValueOfTheTopValueOfAValuePopupShowsTheNewValueWithItsChildren() throws Exception {
        debug(new AnswerServer(true, true));
        server.setExpressionAnswer = args -> {
            var response = new SetExpressionResponse();
            response.setValue("Answer{42}");
            response.setVariablesReference(NEW_ANSWER_REFERENCE);
            return CompletableFuture.completedFuture(response);
        };
        var result = evaluate("answer");
        // the tree of the value popup of an expression in the editor, whose root is the value (XDebuggerTreeCreator)
        var tree = new XDebuggerTree(getProject(), new DAPDebuggerEditorsProvider(null, null), null,
                XDebuggerActions.INSPECT_TREE_POPUP_GROUP, null);
        Disposer.register(getTestRootDisposable(), tree);
        var root = new XValueNodeImpl(tree, null, result.getName(), result);
        tree.setRoot(root, true);
        assertTrue("The tree of a value popup is outside the session's views", tree.isDetached());
        assertEquals("41", root.getRawValue());
        assertTrue(root.isLeaf());

        var errors = new CopyOnWriteArrayList<String>();
        // As SetValueInplaceEditor.doOKAction, once the user typed a value in "Set Value…" and pressed Enter: the
        // detached tree is rebuilt, and its root shows the value again
        DebuggerUIUtil.setTreeNodeValue(root, expression("new Answer(42)"), errors::add);
        PlatformTestUtil.waitWithEventsDispatching("the popup shows the new value",
                () -> "Answer{42}".equals(root.getRawValue()), BOUND_SECONDS);
        assertEquals(List.of(), errors);
        assertFalse("The new value has children", root.isLeaf());
    }

    public void testSetValueWithSetExpressionStartsFromTheValueShown() throws Exception {
        debug(new AnswerServer(false, true));
        var result = evaluate("answer");
        assertTrue(result.getModifier() instanceof DAPExpressionModifier);
        assertEquals("41", initialValueEditorText(result));

        var size = local("size");
        assertTrue(size.getModifier() instanceof DAPExpressionModifier);
        var node = node(size);
        assertEquals("(...)", initialValueEditorText(size));
        resolve(node);
        assertEquals("The value of the lazy variable, rather than its placeholder", "3", initialValueEditorText(size));
    }

    public void testSetValueWithSetVariableStartsFromTheValueShown() throws Exception {
        debug(new AnswerServer(true, false));
        var size = local("size");
        assertTrue(size.getModifier() instanceof DAPValueModifier);
        var node = node(size);
        assertEquals("(...)", initialValueEditorText(size));
        resolve(node);
        assertEquals("The value of the lazy variable, rather than its placeholder", "3", initialValueEditorText(size));
    }

    public void testSetValueOfAnEvaluateResultAsksForTheValueInTheSessionsFormat() throws Exception {
        debug(new AnswerServer(true, true));
        ((DAPDebugProcess) session.getDebugProcess()).getValueFormat().setHexadecimal(true);
        var result = evaluate("answer");

        assertNull("The value is set", setValue(result, "42"));

        var args = server.setExpressions.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setExpression'", args);
        assertNotNull("The value is asked for in hexadecimal", args.getFormat());
        assertEquals(Boolean.TRUE, args.getFormat().getHex());
    }

    public void testSetValueIsNotOfferedForAnEvaluateResultWhichTheDAPServerMarksReadOnly() throws Exception {
        debug(new AnswerServer(true, true));
        var readOnly = new VariablePresentationHint();
        readOnly.setAttributes(new String[]{VariablePresentationHintAttributes.READ_ONLY});
        server.evaluateHint = readOnly;
        var result = evaluate("answer");

        assertNull("\"Set Value…\" isn't offered for a read-only Evaluate result", result.getModifier());
        assertFalse("\"Set Value…\" is disabled for a read-only Evaluate result", isSetValueEnabled(node(result)));
    }

    public void testALazyEvaluateResultIsShownWithTheVariableOfItsValueWhichCanBeSetAndItselfCant() throws Exception {
        debug(new AnswerServer(true, true));
        var lazy = new VariablePresentationHint();
        lazy.setLazy(true);
        server.evaluateHint = lazy;
        server.evaluateVariablesReference = LAZY_REFERENCE;
        var result = evaluate("size");

        // shown as before: its placeholder value, and the one variable which gives its value
        var node = node(result);
        // The node applies what the value gives it on the UI thread: a link would be there once it has
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertNull("A lazy Evaluate result has no \"Show value\" link", node.getFullValueEvaluator());
        assertFalse("A lazy Evaluate result can be expanded", node.isLeaf());
        assertEquals("41", result.getShownVariable().getValue());
        assertNull("\"Set Value…\" isn't offered for a lazy Evaluate result", result.getModifier());
        assertFalse("\"Set Value…\" is disabled for a lazy Evaluate result", isSetValueEnabled(node));

        var children = children(result);
        assertEquals(1, children.size());
        var value = (DAPValue) children.getValue(0);
        assertEquals("3", value.getShownVariable().getValue());
        assertTrue("\"Set Value…\" is offered for the variable which gives the value", isSetValueEnabled(node(value)));
        assertNull("The value is set", setValue(value, "4"));
        var args = server.setVariables.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setVariable'", args);
        assertEquals("size", args.getName());
        assertEquals(LAZY_REFERENCE, args.getVariablesReference());
        assertEquals("The DAP server receives no 'setExpression'", List.of(), List.copyOf(server.setExpressions));
    }

    public void testSetValueOfAnEvaluateResultOfAnEarlierStopReportsItAndSendsNothing() throws Exception {
        debug(new AnswerServer(true, true));
        var result = evaluate("answer");
        assertNotNull(modifierWhenShown(result));

        // the program runs and stops again, where the DAP server gives the id 7 to the frame again
        stopAgain();
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(result, "42"));
        assertEquals("The DAP server receives no 'setExpression'", List.of(), List.copyOf(server.setExpressions));

        // the result of an evaluation at this stop can be set
        assertNull("The value is set", setValue(evaluate("answer"), "42"));
        var args = server.setExpressions.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setExpression'", args);
        assertEquals(Integer.valueOf(FRAME_ID), args.getFrameId());
    }

    public void testSetValueOfAVariableOfAnEarlierStopReportsItAndSendsNothing() throws Exception {
        debug(new AnswerServer(true, true));
        var answer = local("answer");
        assertTrue(modifierWhenShown(answer) instanceof DAPValueModifier);

        // the program runs and stops again, where the DAP server gives the id 1 to the locals again
        stopAgain();
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(answer, "42"));
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));

        // a variable of this stop can be set
        assertNull("The value is set", setValue(local("answer"), "42"));
        var args = server.setVariables.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setVariable'", args);
        assertEquals(LOCALS_REFERENCE, args.getVariablesReference());
    }

    public void testSetValueOnceTheProgramIsResumedReportsItAndSendsNothing() throws Exception {
        debug(new AnswerServer(true, true));
        var result = evaluate("answer");
        var answer = local("answer");
        assertTrue(modifierWhenShown(result) instanceof DAPExpressionModifier);
        assertTrue(modifierWhenShown(answer) instanceof DAPValueModifier);

        // Step Over: the program runs, and the DAP server holds back the 'stopped' event which ends the step, so that
        // the frame id and the 'variablesReference' of the values may already name something else at the new stop
        session.stepOver(false);
        assertNotNull("The DAP server receives 'next'", server.nexts.poll(BOUND_SECONDS, TimeUnit.SECONDS));
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(result, "42"));
        assertEquals(DAPBundle.message("dap.variables.setValue.stale"), setValue(answer, "42"));
        assertEquals("The DAP server receives no 'setExpression'", List.of(), List.copyOf(server.setExpressions));
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));
    }

    public void testTheErrorOfTheDAPServerIsReported() throws Exception {
        debug(new AnswerServer(true, true));
        server.setExpressionAnswer = args -> CompletableFuture.failedFuture(new ResponseErrorException(
                new ResponseError(ResponseErrorCode.InternalError, "'answer + 1' can't be assigned to", null)));
        var result = evaluate("answer + 1");

        assertEquals("'answer + 1' can't be assigned to", setValue(result, "42"));

        var args = server.setExpressions.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setExpression'", args);
        assertEquals("answer + 1", args.getExpression());
        assertEquals("The value doesn't change", "41", result.getShownVariable().getValue());
    }

    public void testSetValueOfAVariableSendsSetExpressionOfItsEvaluateNameWithoutSetVariable() throws Exception {
        debug(new AnswerServer(false, true));
        var answer = local("answer");
        assertEquals(Integer.valueOf(LOCALS_REFERENCE), answer.getParentVariablesReference());

        assertTrue("\"Set Value…\" is offered for a variable with an 'evaluateName'", isSetValueEnabled(node(answer)));
        assertNull("The value is set", setValue(answer, "42"));

        var args = server.setExpressions.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setExpression'", args);
        assertEquals("frame.answer", args.getExpression());
        assertEquals("42", args.getValue());
        assertEquals(Integer.valueOf(FRAME_ID), args.getFrameId());
        assertEquals("42", answer.getShownVariable().getValue());
    }

    public void testSetValueOfAVariableSendsSetVariableWhenTheDAPServerSupportsBoth() throws Exception {
        debug(new AnswerServer(true, true));
        var answer = local("answer");

        assertNull("The value is set", setValue(answer, "42"));

        var args = server.setVariables.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setVariable'", args);
        assertEquals("answer", args.getName());
        assertEquals(LOCALS_REFERENCE, args.getVariablesReference());
        assertEquals("42", args.getValue());
        assertEquals("The DAP server receives no 'setExpression'", List.of(), List.copyOf(server.setExpressions));
    }

    public void testSetValueOfALazyVariableSendsSetExpressionOfTheEvaluateNameOfItsValue() throws Exception {
        debug(new AnswerServer(false, true));
        var size = local("size");
        var node = node(size);
        assertNotNull("\"Set Value…\" is offered for a lazy variable with an 'evaluateName'", modifierWhenShown(size));

        resolve(node);
        assertEquals("3", size.getShownVariable().getValue());
        assertTrue("\"Set Value…\" is offered for the value of a lazy variable", isSetValueEnabled(node));
        assertNull("The value is set", setValue(size, "4"));

        var args = server.setExpressions.poll(BOUND_SECONDS, TimeUnit.SECONDS);
        assertNotNull("The DAP server receives 'setExpression'", args);
        assertEquals("The 'evaluateName' of the value is assigned to", "this.size", args.getExpression());
        assertEquals("4", args.getValue());
        assertEquals("The value which the lazy variable shows changes", "4", size.getShownVariable().getValue());
    }

    public void testSetValueIsNotOfferedForALazyVariableWithoutEvaluateNameEvenOnceItsValueHasOne() throws Exception {
        debug(new AnswerServer(false, true));
        var count = local("count");
        var node = node(count);
        assertNull("\"Set Value…\" isn't offered for a lazy variable without 'evaluateName'", modifierWhenShown(count));

        resolve(node);
        assertEquals("5", count.getShownVariable().getValue());
        // IntelliJ 2026.3 asks once, when it shows the value: the answer is the same in every version
        assertNull("\"Set Value…\" is still not offered once the value has an 'evaluateName'", count.getModifier());
        assertFalse(isSetValueEnabled(node));
    }

    public void testSetValueOfALazyVariableWhoseValueIsReadOnlyReportsIt() throws Exception {
        debug(new AnswerServer(true, true));
        var fixed = local("fixed");
        var node = node(fixed);
        assertNotNull("\"Set Value…\" is offered for a lazy variable", modifierWhenShown(fixed));

        resolve(node);
        assertEquals("6", fixed.getShownVariable().getValue());
        // IntelliJ 2026.3 asks for the modifier again when the value is set: it must still be there
        assertNotNull("\"Set Value…\" stays offered once the value is read-only", fixed.getModifier());
        assertEquals(DAPBundle.message("dap.variables.setValue.readOnly"), setValue(fixed, "7"));
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));
        assertEquals("The DAP server receives no 'setExpression'", List.of(), List.copyOf(server.setExpressions));
    }

    public void testSetValueOfANewValueWhichIsReadOnlyReportsIt() throws Exception {
        debug(new AnswerServer(true, true));
        var readOnly = new VariablePresentationHint();
        readOnly.setAttributes(new String[]{VariablePresentationHintAttributes.READ_ONLY});
        server.setExpressionAnswer = args -> {
            var response = new SetExpressionResponse();
            response.setValue(args.getValue());
            response.setPresentationHint(readOnly);
            return CompletableFuture.completedFuture(response);
        };
        var result = evaluate("answer");
        assertNotNull(modifierWhenShown(result));
        assertNull("The value is set", setValue(result, "42"));
        assertNotNull("The DAP server receives 'setExpression'", server.setExpressions.poll(BOUND_SECONDS, TimeUnit.SECONDS));

        assertNotNull("\"Set Value…\" stays offered once the new value is read-only", result.getModifier());
        assertEquals(DAPBundle.message("dap.variables.setValue.readOnly"), setValue(result, "43"));
        assertEquals("The DAP server receives no other 'setExpression'", List.of(), List.copyOf(server.setExpressions));
    }

    public void testSetValueOnceTheDAPServerNoLongerSupportsItReportsIt() throws Exception {
        debug(new AnswerServer(true, true));
        var answer = local("answer");
        var result = evaluate("answer");
        assertTrue(modifierWhenShown(answer) instanceof DAPValueModifier);
        assertTrue(modifierWhenShown(result) instanceof DAPExpressionModifier);

        var update = new Capabilities();
        update.setSupportsSetVariable(false);
        update.setSupportsSetExpression(false);
        server.sendCapabilities(update);
        var client = client();
        PlatformTestUtil.waitWithEventsDispatching("the DAP server turns off setting values",
                () -> !client.isSupportsSetVariable() && !client.isSupportsSetExpression(), BOUND_SECONDS);

        assertNotNull("\"Set Value…\" stays offered for a variable", answer.getModifier());
        assertNotNull("\"Set Value…\" stays offered for an Evaluate result", result.getModifier());
        assertEquals(DAPBundle.message("dap.variables.setValue.unsupported"), setValue(answer, "42"));
        assertEquals(DAPBundle.message("dap.variables.setValue.unsupported"), setValue(result, "42"));
        assertEquals("The DAP server receives no 'setVariable'", List.of(), List.copyOf(server.setVariables));
        assertEquals("The DAP server receives no 'setExpression'", List.of(), List.copyOf(server.setExpressions));
        assertNull("\"Set Value…\" isn't offered for a value shown since", local("answer").getModifier());
    }

    /**
     * Starts debugging, attached to the given DAP server, and waits for the program to stop.
     */
    private void debug(@NotNull AnswerServer server) throws Exception {
        this.server = server;
        var type = Arrays.stream(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensions())
                .filter(t -> t.getId().equals("DAPConfiguration")).findFirst().orElseThrow();
        var settings = RunManager.getInstance(getProject()).createConfiguration("answer", type.getConfigurationFactories()[0]);
        var configuration = (DAPRunConfiguration) settings.getConfiguration();
        configuration.setDebugMode(DebugMode.ATTACH);
        configuration.setAttachAddress(InetAddress.getLoopbackAddress().getHostAddress());
        configuration.setAttachPort(String.valueOf(server.getPort()));
        configuration.setAttachConfiguration("{}");
        // the debug process connects to the DAP server in the background, as it does outside tests
        PlatformTestUtil.withSystemProperty("intellij.progress.task.ignoreHeadless", "true", () ->
                ExecutionEnvironmentBuilder.create(getProject(), DefaultDebugExecutor.getDebugExecutorInstance(), configuration)
                        .buildAndExecute());
        PlatformTestUtil.waitWithEventsDispatching("the program stops", () -> {
            // no session tab is selected in tests, so there is no current session
            var sessions = XDebuggerManager.getInstance(getProject()).getDebugSessions();
            session = sessions.length == 1 ? sessions[0] : null;
            return session != null && session.isSuspended() && session.getCurrentStackFrame() instanceof DAPStackFrame;
        }, BOUND_SECONDS);
    }

    /**
     * Makes the program stop again, as after a step, and waits for the debugger to show the new stop.
     */
    private void stopAgain() {
        var stopped = session.getSuspendContext();
        var frame = session.getCurrentStackFrame();
        server.stop();
        // The session takes the new suspend context before it makes a frame of it the current one: until then, the
        // current frame is still the one of the earlier stop, whose values are out of date
        PlatformTestUtil.waitWithEventsDispatching("the program stops again", () ->
                session.getSuspendContext() != stopped
                        && session.getCurrentStackFrame() instanceof DAPStackFrame current && current != frame,
                BOUND_SECONDS);
    }

    /**
     * Returns the value of the given expression, evaluated in the stopped frame.
     */
    private @NotNull DAPValue evaluate(@NotNull String expression) throws InterruptedException {
        var evaluator = session.getCurrentStackFrame().getEvaluator();
        assertNotNull(evaluator);
        var result = new CompletableFuture<XValue>();
        evaluator.evaluate(expression, new XDebuggerEvaluator.XEvaluationCallback() {
            @Override
            public void evaluated(@NotNull XValue value) {
                result.complete(value);
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                result.completeExceptionally(new AssertionError(errorMessage));
            }
        }, null);
        var value = (DAPValue) PlatformTestUtil.waitForFuture(result, BOUND_SECONDS * 1000L);
        assertNotNull("The DAP server receives 'evaluate'", server.evaluations.poll(BOUND_SECONDS, TimeUnit.SECONDS));
        return value;
    }

    /**
     * Sets the given value as "Set Value…" does once the user typed it and pressed Enter, and returns the error which
     * the modifier reports, or null if the value is modified.
     */
    private static @Nullable String setValue(@NotNull DAPValue value, @NotNull String text) {
        XValueModifier modifier = value.getModifier();
        assertNotNull("\"Set Value…\" is offered", modifier);
        var done = new CompletableFuture<@Nullable String>();
        modifier.setValue(expression(text), new XValueModifier.XModificationCallback() {
            @Override
            public void valueModified() {
                done.complete(null);
            }

            @Override
            public void errorOccurred(@NotNull String errorMessage) {
                done.complete(errorMessage);
            }
        });
        return PlatformTestUtil.waitForFuture(done, BOUND_SECONDS * 1000L);
    }

    /**
     * Returns the 'variablesReference' of each 'variables' request which the DAP server received since the test last
     * cleared them, except the locals'. The debugger asks for the locals by itself once the program stops, for the
     * Variables view and the inline values in the editor, and that request may come at any time during the test.
     */
    private @NotNull List<Integer> variablesRequestsOtherThanLocals() {
        return server.variablesReferences.stream()
                .filter(reference -> reference != LOCALS_REFERENCE)
                .toList();
    }

    /**
     * Returns the DAP value of the given name in the locals of the stopped frame, as the Variables view gets it.
     */
    private @NotNull DAPValue local(@NotNull String name) {
        XValueContainer locals = children(session.getCurrentStackFrame()).getBottomGroups().get(0);
        var children = children(locals);
        return (DAPValue) IntStream.range(0, children.size())
                .filter(i -> children.getName(i).equals(name))
                .mapToObj(children::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No variable " + name));
    }

    /**
     * Returns a node of a debugger tree which shows the given value, as the Variables view and Evaluate Expression do:
     * not a node of the Watches view, for which IntelliJ offers no "Set Value…" (XSetValueAction).
     */
    private @NotNull XValueNodeImpl node(@NotNull DAPValue value) {
        var tree = new XDebuggerTree(getProject(), new DAPDebuggerEditorsProvider(null, null), null,
                XDebuggerActions.VARIABLES_TREE_POPUP_GROUP, null);
        Disposer.register(getTestRootDisposable(), tree);
        return new XValueNodeImpl(tree, null, value.getName(), value);
    }

    /**
     * Returns the text with which the editor of "Set Value…" starts for the given value, as SetValueInplaceEditor asks
     * for it.
     */
    private static @Nullable String initialValueEditorText(@NotNull DAPValue value) {
        XValueModifier modifier = value.getModifier();
        assertNotNull("\"Set Value…\" is offered", modifier);
        var text = new CompletableFuture<@Nullable String>();
        modifier.calculateInitialValueEditorText(text::complete);
        return PlatformTestUtil.waitForFuture(text, BOUND_SECONDS * 1000L);
    }

    /**
     * Returns the modifier of the given value as the debugger asks for it when it shows the value. IntelliJ 2026.3 asks
     * once, with XValue.getModifierAsync(), which IntelliJ 2024.2 doesn't have, and asks getModifier() again when the
     * value is set.
     */
    private static @Nullable XValueModifier modifierWhenShown(@NotNull XValue value) throws Exception {
        Method getModifierAsync;
        try {
            getModifierAsync = XValue.class.getMethod("getModifierAsync");
        } catch (NoSuchMethodException e) {
            return value.getModifier();
        }
        var modifier = (CompletableFuture<?>) getModifierAsync.invoke(value);
        return (XValueModifier) modifier.get(BOUND_SECONDS, TimeUnit.SECONDS);
    }

    private @NotNull DAPClient client() {
        return ((DAPStackFrame) session.getCurrentStackFrame()).getClient();
    }

    /**
     * Asks for the value of the lazy variable which the given node shows, as a click on its "Show value" link does.
     */
    private static void resolve(@NotNull XValueNodeImpl node) {
        PlatformTestUtil.waitWithEventsDispatching("the node shows the link of a lazy variable",
                () -> node.getFullValueEvaluator() != null, BOUND_SECONDS);
        var evaluated = new CompletableFuture<String>();
        node.getFullValueEvaluator().startEvaluation((XFullValueEvaluationCallback) Proxy.newProxyInstance(
                XFullValueEvaluationCallback.class.getClassLoader(), new Class<?>[]{XFullValueEvaluationCallback.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "evaluated" -> evaluated.complete((String) args[0]);
                    case "errorOccurred" -> evaluated.completeExceptionally(new AssertionError(args[0]));
                    case "isObsolete" -> false;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> null;
                }));
        PlatformTestUtil.waitForFuture(evaluated, BOUND_SECONDS * 1000L);
    }

    /**
     * Returns whether "Set Value…" (F2) is enabled with the given node selected in a debugger tree.
     */
    private static boolean isSetValueEnabled(@NotNull XValueNodeImpl node) {
        var action = ActionManager.getInstance().getAction(XDebuggerActions.SET_VALUE);
        assertNotNull(action);
        var event = TestActionEvent.createTestEvent(action,
                SimpleDataContext.getSimpleContext(XDebuggerTree.SELECTED_NODES, List.of(node)));
        action.update(event);
        return event.getPresentation().isEnabledAndVisible();
    }

    private static @NotNull XExpression expression(@NotNull String text) {
        return XDebuggerUtil.getInstance().createExpression(text, null, null, EvaluationMode.EXPRESSION);
    }

    private static @NotNull XValueChildrenList children(@NotNull XValueContainer container) {
        var all = new XValueChildrenList();
        var done = new CompletableFuture<XValueChildrenList>();
        container.computeChildren(new XCompositeNode() {
            @Override
            public void addChildren(@NotNull XValueChildrenList children, boolean last) {
                IntStream.range(0, children.size()).forEach(i -> all.add(children.getName(i), children.getValue(i)));
                children.getTopGroups().forEach(all::addTopGroup);
                children.getBottomGroups().forEach(all::addBottomGroup);
                if (last) {
                    done.complete(all);
                }
            }

            @Override
            public void tooManyChildren(int remaining) {
            }

            @Override
            public void setAlreadySorted(boolean alreadySorted) {
            }

            @Override
            public void setErrorMessage(@NotNull String errorMessage) {
                done.completeExceptionally(new AssertionError(errorMessage));
            }

            @Override
            public void setErrorMessage(@NotNull String errorMessage, @Nullable XDebuggerTreeNodeHyperlink link) {
                setErrorMessage(errorMessage);
            }

            @Override
            public void setMessage(@NotNull String message, @Nullable Icon icon, @NotNull SimpleTextAttributes attributes,
                                   @Nullable XDebuggerTreeNodeHyperlink link) {
            }
        });
        return PlatformTestUtil.waitForFuture(done, BOUND_SECONDS * 1000L);
    }

    /**
     * A DAP server of a program stopped in the frame 7, whose locals are 'answer' = 41, with the 'evaluateName'
     * 'frame.answer', and three lazy variables: 'size', with the 'evaluateName' 'frame.size', whose value 3 has the
     * 'evaluateName' 'this.size'; 'fixed', whose value 6 is read-only; and 'count', without 'evaluateName', whose
     * value 5 has the 'evaluateName' 'this.count'. It evaluates any
     * expression to 41, with the presentation hint and the 'variablesReference' which a test sets, and answers
     * 'setExpression' with the assigned value unless a test answers otherwise.
     */
    private static class AnswerServer implements IDebugProtocolServer {

        private final boolean supportsSetVariable;
        private final boolean supportsSetExpression;
        private final ServerSocket serverSocket;
        private final LinkedBlockingQueue<EvaluateArguments> evaluations = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<SetVariableArguments> setVariables = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<SetExpressionArguments> setExpressions = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<Integer> variablesReferences = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<NextArguments> nexts = new LinkedBlockingQueue<>();
        private volatile Function<SetExpressionArguments, CompletableFuture<SetExpressionResponse>> setExpressionAnswer = args -> {
            var response = new SetExpressionResponse();
            response.setValue(args.getValue());
            return CompletableFuture.completedFuture(response);
        };
        private volatile IDebugProtocolClient client;
        private volatile @Nullable VariablePresentationHint evaluateHint;
        private volatile int evaluateVariablesReference;

        AnswerServer(boolean supportsSetVariable, boolean supportsSetExpression) throws IOException {
            this.supportsSetVariable = supportsSetVariable;
            this.supportsSetExpression = supportsSetExpression;
            this.serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            var accept = new java.lang.Thread(() -> {
                try {
                    var socket = serverSocket.accept();
                    var launcher = DSPLauncher.createServerLauncher(this, socket.getInputStream(), socket.getOutputStream());
                    client = launcher.getRemoteProxy();
                    launcher.startListening();
                } catch (IOException e) {
                    // closed by the test
                }
            }, "Answer DAP server");
            accept.setDaemon(true);
            accept.start();
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        /**
         * Sends the 'capabilities' event with the given capabilities, which change the ones the DAP server has.
         */
        void sendCapabilities(@NotNull Capabilities capabilities) {
            var args = new CapabilitiesEventArguments();
            args.setCapabilities(capabilities);
            client.capabilities(args);
        }

        void close() throws IOException {
            serverSocket.close();
        }

        @Override
        public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
            var capabilities = new Capabilities();
            capabilities.setSupportsConfigurationDoneRequest(true);
            capabilities.setSupportsSetVariable(supportsSetVariable);
            capabilities.setSupportsSetExpression(supportsSetExpression);
            capabilities.setSupportsValueFormattingOptions(true);
            return CompletableFuture.completedFuture(capabilities);
        }

        @Override
        public CompletableFuture<Void> attach(Map<String, Object> args) {
            return CompletableFuture.completedFuture(null).thenRun(() -> client.initialized());
        }

        @Override
        public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
            return CompletableFuture.completedFuture(null).thenRun(this::stop);
        }

        /**
         * Sends the 'stopped' event of the program, which stops in the frame 7, as after a step.
         */
        void stop() {
            var stopped = new StoppedEventArguments();
            stopped.setReason(StoppedEventArgumentsReason.PAUSE);
            stopped.setThreadId(1);
            stopped.setAllThreadsStopped(true);
            client.stopped(stopped);
        }

        @Override
        public CompletableFuture<ThreadsResponse> threads() {
            var thread = new Thread();
            thread.setId(1);
            thread.setName("main");
            var response = new ThreadsResponse();
            response.setThreads(new Thread[]{thread});
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<StackTraceResponse> stackTrace(StackTraceArguments args) {
            var frame = new StackFrame();
            frame.setId(FRAME_ID);
            frame.setName("run");
            frame.setLine(5);
            var response = new StackTraceResponse();
            response.setStackFrames(new StackFrame[]{frame});
            response.setTotalFrames(1);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<ScopesResponse> scopes(ScopesArguments args) {
            var locals = new Scope();
            locals.setName("Locals");
            locals.setPresentationHint("locals");
            locals.setVariablesReference(LOCALS_REFERENCE);
            var response = new ScopesResponse();
            response.setScopes(new Scope[]{locals});
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<VariablesResponse> variables(VariablesArguments args) {
            variablesReferences.add(args.getVariablesReference());
            var response = new VariablesResponse();
            response.setVariables(switch (args.getVariablesReference()) {
                case LOCALS_REFERENCE -> {
                    var lazy = new VariablePresentationHint();
                    lazy.setLazy(true);
                    yield new Variable[]{
                            variable("answer", "41", "frame.answer", 0, null),
                            variable("size", "(...)", "frame.size", LAZY_REFERENCE, lazy),
                            variable("fixed", "(...)", "frame.fixed", LAZY_READ_ONLY_REFERENCE, lazy),
                            variable("count", "(...)", null, LAZY_WITHOUT_EVALUATE_NAME_REFERENCE, lazy)};
                }
                case LAZY_REFERENCE -> new Variable[]{variable("size", "3", "this.size", 0, null)};
                case LAZY_READ_ONLY_REFERENCE -> {
                    var readOnly = new VariablePresentationHint();
                    readOnly.setAttributes(new String[]{VariablePresentationHintAttributes.READ_ONLY});
                    yield new Variable[]{variable("fixed", "6", "this.fixed", 0, readOnly)};
                }
                case LAZY_WITHOUT_EVALUATE_NAME_REFERENCE -> new Variable[]{variable("count", "5", "this.count", 0, null)};
                case NEW_ANSWER_REFERENCE -> new Variable[]{variable("value", "42", "answer.value", 0, null)};
                default -> new Variable[0];
            });
            return CompletableFuture.completedFuture(response);
        }

        private static @NotNull Variable variable(@NotNull String name, @NotNull String value,
                                                  @Nullable String evaluateName, int variablesReference,
                                                  @Nullable VariablePresentationHint hint) {
            var variable = new Variable();
            variable.setName(name);
            variable.setValue(value);
            variable.setEvaluateName(evaluateName);
            variable.setVariablesReference(variablesReference);
            variable.setPresentationHint(hint);
            return variable;
        }

        @Override
        public CompletableFuture<EvaluateResponse> evaluate(EvaluateArguments args) {
            evaluations.add(args);
            var response = new EvaluateResponse();
            response.setResult("41");
            response.setVariablesReference(evaluateVariablesReference);
            response.setPresentationHint(evaluateHint);
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<Void> next(NextArguments args) {
            // The program runs: the 'stopped' event which ends the step is held back
            nexts.add(args);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<SetVariableResponse> setVariable(SetVariableArguments args) {
            setVariables.add(args);
            var response = new SetVariableResponse();
            response.setValue(args.getValue());
            return CompletableFuture.completedFuture(response);
        }

        @Override
        public CompletableFuture<SetExpressionResponse> setExpression(SetExpressionArguments args) {
            setExpressions.add(args);
            return setExpressionAnswer.apply(args);
        }

        @Override
        public CompletableFuture<Void> disconnect(DisconnectArguments args) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
