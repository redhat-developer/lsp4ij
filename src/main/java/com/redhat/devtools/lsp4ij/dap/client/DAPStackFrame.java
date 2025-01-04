/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.redhat.devtools.lsp4ij.dap.evaluation.DAPDebuggerEvaluator;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Debug Adapter Protocol (DAP) stack frame.
 */
public class DAPStackFrame extends XStackFrame {

    private final @NotNull StackFrame stackFrame;
    private final @NotNull DAPClient client;
    private @Nullable XSourcePosition sourcePosition;
    private XDebuggerEvaluator evaluator;

    public DAPStackFrame(@NotNull DAPClient client,
                         @NotNull StackFrame stackFrame) {
        this.client = client;
        this.stackFrame = stackFrame;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        Source source = stackFrame.getSource();
        String sourceName = source != null ? source.getName() : null;
        if (StringUtils.isNotBlank(sourceName)) {
            component.append(sourceName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        } else {
            component.append(XDebuggerBundle.message("invalid.frame"), SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        int line = stackFrame.getLine();
        if (line > 0) {
            component.append(":" + line, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        component.append(" at ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        component.append(stackFrame.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        component.setIcon(AllIcons.Debugger.Frame);
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        if (sourcePosition == null && stackFrame.getSource() != null) {
            String path = stackFrame.getSource().getPath();
            try {
                VirtualFile file = VfsUtil.findFile(Paths.get(path), true);
                sourcePosition = XDebuggerUtil.getInstance().createPosition(file, stackFrame.getLine() - 1);
            }
            catch(Exception e) {
                // Invalid path...
                // ex: <node_internals>/internal/modules/cjs/loader
            }
        }
        return sourcePosition;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        // if result of computation won't be used so computation may be interrupted.
        if (node.isObsolete()) {
            return;
        }
        var server = client.getDebugProtocolServer();
        if (server == null) {
            return;
        }
        ScopesArguments scopeArgs = new ScopesArguments();
        scopeArgs.setFrameId(stackFrame.getId());
        server.scopes(scopeArgs)
                .thenAcceptAsync(scopes -> {
                    for (Scope scope : scopes.getScopes()) {
                        int parentVariablesReference = scope.getVariablesReference();
                        XValueChildrenList children = new XValueChildrenList();
                        VariablesArguments variablesArgs = new VariablesArguments();
                        variablesArgs.setVariablesReference(parentVariablesReference);
                        server.variables(variablesArgs)
                                .thenAccept(variablesResponse -> {
                                    children.addBottomGroup(new DAPValueGroup(client, scope.getName(),
                                            Arrays.asList(variablesResponse.getVariables()),
                                            parentVariablesReference));
                                    // Add the list to the node as children.
                                    node.addChildren(children, true);
                                });
                    }
                });
    }

    public @NotNull DAPClient getClient() {
        return client;
    }

    public int getFrameId() {
        return stackFrame.getId();
    }

    @Override
    public @Nullable XDebuggerEvaluator getEvaluator() {
        if (evaluator == null) {
            evaluator = new DAPDebuggerEvaluator(this);
        }
        return evaluator;
    }
}
