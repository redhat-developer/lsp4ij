/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client.variables;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.*;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.redhat.devtools.lsp4ij.dap.DAPBundle;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHintAttributes;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Debug Adapter Protocol (DAP) value.
 */
public class DAPValue extends XNamedValue {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPValue.class);

    /**
     * How long "Jump to Source" waits for the DAP server to locate the declaration of a variable, before it searches the
     * name instead: IntelliJ 2026.3's split debugger waits at most 10 seconds for the position, and a DAP server which
     * never answers must not leave the user without one.
     */
    static final int DECLARATION_TIMEOUT_SECONDS = 3;

    // How long the "Jump to Source" of this value waits for the DAP server, which only a test changes
    private volatile @NotNull Duration declarationTimeout = Duration.ofSeconds(DECLARATION_TIMEOUT_SECONDS);

    @NotNull
    private final DAPStackFrame stackFrame;
    @NotNull
    private final Variable variable;
    private final @Nullable Integer parentVariablesReference;
    @Nullable
    private final Icon icon;
    // The variable with the value of a lazy variable, once the user asked for it
    private volatile @Nullable Variable resolved;
    // Whether "Set Value..." is offered depends on the value as the DAP server described it when the value was created,
    // and on the capabilities which the DAP server had then: IntelliJ 2026.3 asks once whether a value can be set, when
    // it shows the value, and asks for its modifier again when the value is set. What changes later, such as the value
    // of a lazy variable, is checked when the value is set, so that "Set Value..." never does nothing.
    private final boolean readOnly;
    private final boolean supportedSetVariable;
    private final boolean supportedSetExpression;

    public DAPValue(@NotNull DAPStackFrame stackFrame,
                    @NotNull Variable variable,
                    @Nullable Integer parentVariablesReference) {
        super(variable.getName());
        this.stackFrame = stackFrame;
        this.variable = variable;
        this.parentVariablesReference = parentVariablesReference;
        var client = getClient();
        this.icon = client.getServerDescriptor().getVariableSupport().getIcon(variable);
        this.readOnly = isReadOnly(variable);
        this.supportedSetVariable = client.isSupportsSetVariable();
        this.supportedSetExpression = client.isSupportsSetExpression();
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        var server = getClient().getDebugProtocolServer();
        if (resolved == null && server != null && DAPLazyValueEvaluator.isLazy(variable)) {
            // Getting the value may run code: get it when the user asks for it
            node.setPresentation(icon, getPresentation(variable), false);
            var format = getClient().getValueFormat();
            node.setFullValueEvaluator(new DAPLazyValueEvaluator(server, variable.getVariablesReference(), format, value -> {
                resolved = value;
                node.setPresentation(icon, getPresentation(value), value.getVariablesReference() > 0);
            }));
            return;
        }
        Variable shown = getShownVariable();
        node.setPresentation(icon, getPresentation(shown), shown.getVariablesReference() > 0);
    }

    /**
     * Returns the variable which this value shows: the variable with the value of a lazy variable once the user asked
     * for it, and the variable itself otherwise.
     *
     * @return the variable which this value shows.
     */
    @NotNull Variable getShownVariable() {
        Variable value = resolved;
        return value != null ? value : variable;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        var server = getClient().getDebugProtocolServer();
        if (server == null) {
            return;
        }

        Variable shown = getShownVariable();
        int parentVariablesReference = shown.getVariablesReference();
        // a large variable, such as a large array, is read a page at a time
        new DAPVariableChildren(server, shown, getClient().getValueFormat(),
                child -> new DAPValue(stackFrame, child, parentVariablesReference))
                .load(node);
    }

    @Nullable
    @Override
    public XValueModifier getModifier() {
        var client = getClient();
        // A capability which the DAP server turns on later offers "Set Value...", and one which it turns off later
        // keeps it, so that the modifier which IntelliJ 2026.3 asks for when the value is set is never gone
        var request = getSetValueRequest(supportedSetVariable || client.isSupportsSetVariable(),
                supportedSetExpression || client.isSupportsSetExpression(), parentVariablesReference, readOnly,
                getAssignableExpression(parentVariablesReference, variable));
        if (request instanceof SetValueRequest.SetVariable) {
            return new DAPValueModifier(this);
        }
        if (request instanceof SetValueRequest.SetExpression setExpression) {
            return new DAPExpressionModifier(this, setExpression.expression());
        }
        // Disable the 'Set Value...' menu and F2.
        return null;
    }

    /**
     * The DAP request which sets a value.
     */
    sealed interface SetValueRequest {

        /**
         * The 'setVariable' request, which names the variable in the container of its parent 'variablesReference'.
         */
        record SetVariable() implements SetValueRequest {
        }

        /**
         * The 'setExpression' request, which assigns the value to an expression.
         *
         * @param expression the expression which the value is assigned to.
         */
        record SetExpression(@NotNull String expression) implements SetValueRequest {
        }
    }

    /**
     * Returns the DAP request which sets a value, or null if it can't be set, as when the DAP server marks the value as
     * read-only:
     * <ul>
     *     <li>a variable with the 'variablesReference' of its parent is set with 'setVariable'. As in VS Code, it is
     *     set with 'setExpression' only when the DAP server can't set variables, by assigning to its 'evaluateName',
     *     if it has one.</li>
     *     <li>a value without a parent 'variablesReference', such as an Evaluate result, can't be named in
     *     'setVariable': it is set with 'setExpression', by assigning to the evaluated expression, which is its
     *     name.</li>
     * </ul>
     *
     * @param supportsSetVariable      true if the DAP server supports the 'setVariable' request.
     * @param supportsSetExpression    true if the DAP server supports the 'setExpression' request.
     * @param parentVariablesReference the 'variablesReference' of the value's parent, or null if it has none.
     * @param readOnly                 true if the DAP server marks the value as read-only.
     * @param expression               the expression which 'setExpression' would assign to: the evaluated expression
     *                                 of a value without a parent, and the 'evaluateName' of a variable, or null if it
     *                                 has none.
     * @return the DAP request which sets the value, or null if it can't be set.
     */
    static @Nullable SetValueRequest getSetValueRequest(boolean supportsSetVariable,
                                                        boolean supportsSetExpression,
                                                        @Nullable Integer parentVariablesReference,
                                                        boolean readOnly,
                                                        @Nullable String expression) {
        if (readOnly) {
            return null;
        }
        if (parentVariablesReference != null && supportsSetVariable) {
            return new SetValueRequest.SetVariable();
        }
        if (supportsSetExpression && StringUtils.isNotBlank(expression)) {
            return new SetValueRequest.SetExpression(expression);
        }
        return null;
    }

    /**
     * Returns the expression which 'setExpression' would assign a new value to: the evaluated expression of a value
     * without a parent 'variablesReference', such as an Evaluate result, which is its name, and the 'evaluateName' of
     * a variable, or null if it has none.
     *
     * @param parentVariablesReference the 'variablesReference' of the value's parent, or null if it has none.
     * @param variable                 the DAP variable.
     * @return the expression which 'setExpression' would assign a new value to, or null if there is none.
     */
    static @Nullable String getAssignableExpression(@Nullable Integer parentVariablesReference,
                                                    @NotNull Variable variable) {
        return parentVariablesReference == null ? variable.getName() : variable.getEvaluateName();
    }

    /**
     * Returns why this value can't be set now although "Set Value..." is offered, or null if it can: the DAP server
     * no longer supports the request which sets it, or marks the value which this value shows as read-only, such as
     * the value of a lazy variable once the user asked for it, or the new value of a 'setExpression'.
     *
     * @param supported true if the DAP server supports the request which sets this value now.
     * @return why this value can't be set now, or null if it can.
     */
    @Nullable @Nls String getWhyItCantBeSet(boolean supported) {
        if (!supported) {
            return DAPBundle.message("dap.variables.setValue.unsupported");
        }
        if (isReadOnly(getShownVariable())) {
            return DAPBundle.message("dap.variables.setValue.readOnly");
        }
        return null;
    }

    /**
     * Reports why a value can't be set as the error of a DAP server is reported: once "Set Value..." has closed its
     * editor. SetValueInplaceEditor sets the value before it closes the editor, and shows an error reported meanwhile
     * on that editor.
     *
     * @param callback the callback of "Set Value...".
     * @param why      why the value can't be set.
     */
    static void reportWhyItCantBeSet(@NotNull XValueModifier.XModificationCallback callback,
                                     @NotNull @Nls String why) {
        AppExecutorUtil.getAppExecutorService().execute(() -> callback.errorOccurred(why));
    }

    /**
     * Returns true if the DAP server marks the given variable as read-only with the 'readOnly' presentation hint
     * attribute, and false otherwise.
     *
     * @param variable the DAP variable.
     * @return true if the DAP server marks the given variable as read-only, and false otherwise.
     */
    static boolean isReadOnly(@NotNull Variable variable) {
        var hint = variable.getPresentationHint();
        var attributes = hint != null ? hint.getAttributes() : null;
        return attributes != null && Arrays.asList(attributes).contains(VariablePresentationHintAttributes.READ_ONLY);
    }

    @NotNull
    private XValuePresentation getPresentation(@NotNull Variable shown) {
        return getClient().getServerDescriptor().getVariableSupport().getValuePresentation(shown);
    }


    @Override
    public void computeSourcePosition(@NotNull XNavigatable navigatable) {
        // "Jump to Source", which may ask for it on the EDT
        setSourcePosition(getDeclarationSourcePosition(), navigatable);
    }

    /**
     * Returns the future of the position of the declaration of the variable which this value shows, which "Jump to
     * Source" opens, or else of the variable itself, such as a lazy variable whose value has no declaration. When the
     * variable has a 'declarationLocationReference', the DAP server is asked where the declaration is, with the
     * 'locations' request, off the calling thread, and only while the stop of the value's frame lasts: the reference is
     * valid only until the program resumes. The name of the variable is searched in the file of the frame, as for the
     * inline values, when neither variable has a reference, when the request fails or the stop has ended, when the DAP
     * server answers no location which can be opened, and when it doesn't locate the declaration within
     * {@link #DECLARATION_TIMEOUT_SECONDS} seconds.
     *
     * @return the future of the position of the declaration of the variable, which is null if neither the DAP server nor
     * the search finds it.
     */
    @NotNull CompletableFuture<@Nullable XSourcePosition> getDeclarationSourcePosition() {
        Integer shownReference = getDeclarationLocationReference(getShownVariable());
        // The value of a lazy variable, which the user asked for, may have no declaration of its own
        Integer reference = shownReference != null ? shownReference : getDeclarationLocationReference(variable);
        if (reference == null) {
            return getSourcePositionOfName();
        }
        DAPClient client = getClient();
        Duration timeout = declarationTimeout;
        return DAPLocations.locate(this, reference, DAPBundle.message("dap.variables.declaration.stale"))
                .thenComposeAsync(location -> DAPLocations.getSourcePosition(client, location),
                        AppExecutorUtil.getAppExecutorService())
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(error -> {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ?
                            error.getCause() : error;
                    LOGGER.warn("Cannot locate the declaration of '{}', its name is searched instead: {}", getName(),
                            cause instanceof TimeoutException ?
                                    "no answer within " + timeout.toMillis() + " ms" : cause.getMessage());
                    return null;
                })
                .thenCompose(position -> position != null ?
                        CompletableFuture.completedFuture(position) : getSourcePositionOfName());
    }

    /**
     * Sets how long the "Jump to Source" of this value waits for the DAP server to locate the declaration, before it
     * searches the name instead, {@link #DECLARATION_TIMEOUT_SECONDS} seconds by default.
     *
     * @param declarationTimeout how long "Jump to Source" waits for the DAP server.
     */
    @TestOnly
    void setDeclarationTimeout(@NotNull Duration declarationTimeout) {
        this.declarationTimeout = declarationTimeout;
    }

    /**
     * Returns the 'declarationLocationReference' of the given variable, or null if it has none. As in VS Code, 0 is no
     * reference.
     *
     * @param variable the DAP variable.
     * @return the 'declarationLocationReference' of the variable, or null if it has none.
     */
    static @Nullable Integer getDeclarationLocationReference(@NotNull Variable variable) {
        Integer reference = variable.getDeclarationLocationReference();
        return reference != null && reference > 0 ? reference : null;
    }

    /**
     * Returns the future of the position of the variable's name in the file of the frame, as the language servers of
     * the file or its highlighter find it.
     */
    private @NotNull CompletableFuture<@Nullable XSourcePosition> getSourcePositionOfName() {
        return stackFrame.getSourcePositionFor(variable);
    }

    /**
     * Gives the source position to the navigatable once it is known, in a read action: the future may complete on a
     * pooled thread, and the navigatable of the inline values reads the document of the position.
     *
     * @param sourcePosition the future of the source position.
     * @param navigatable    the navigatable which takes the source position.
     */
    static void setSourcePosition(@NotNull CompletableFuture<@Nullable XSourcePosition> sourcePosition,
                                  @NotNull XNavigatable navigatable) {
        sourcePosition.thenAccept(position -> {
            if (position != null) {
                ApplicationManager.getApplication().runReadAction(() -> navigatable.setSourcePosition(position));
            }
        });
    }

    @NotNull
    @Override
    public ThreeState computeInlineDebuggerData(@NotNull XInlineDebuggerDataCallback callback) {
        // The value is shown where the name of the variable is, never at its declaration: a 'locations' request for
        // each value shown inline would cost too much
        setSourcePosition(getSourcePositionOfName(), callback::computed);
        return ThreeState.YES;
    }

    @Override
    public boolean canNavigateToSource() {
        return true;
    }

    @Override
    public boolean canNavigateToTypeSource() {
        return false;
    }

    @Override
    public void computeTypeSourcePosition(@NotNull XNavigatable navigatable) {

    }

    public @NotNull DAPClient getClient() {
        return stackFrame.getClient();
    }

    public @NotNull DAPStackFrame getStackFrame() {
        return stackFrame;
    }

    public @Nullable Integer getParentVariablesReference() {
        return parentVariablesReference;
    }

    public @NotNull Variable getVariable() {
        return variable;
    }
}
