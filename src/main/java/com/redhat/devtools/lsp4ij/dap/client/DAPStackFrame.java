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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPFileRegistry;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPSourceReferencePosition;
import com.redhat.devtools.lsp4ij.dap.client.files.DeferredLocalFileSourcePosition;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValueGroup;
import com.redhat.devtools.lsp4ij.dap.client.variables.providers.DebugVariableContext;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyDeferredSourcePosition;
import com.redhat.devtools.lsp4ij.dap.evaluation.DAPDebuggerEvaluator;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static com.redhat.devtools.lsp4ij.dap.DAPIJUtils.getValidFilePath;

/**
 * Debug Adapter Protocol (DAP) stack frame.
 */
public class DAPStackFrame extends XStackFrame {

    private final @NotNull StackFrame stackFrame;
    private final @NotNull DAPClient client;
    private volatile @Nullable XSourcePosition sourcePosition;
    private @Nullable DisassemblyDeferredSourcePosition disassemblyInstructionSourcePosition;
    private XDebuggerEvaluator evaluator;
    private volatile @Nullable CompletableFuture<DebugVariableContext> variablesContext;

    public DAPStackFrame(@NotNull DAPClient client,
                         @NotNull StackFrame stackFrame) {
        this.client = client;
        this.stackFrame = stackFrame;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        component.append(stackFrame.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        int line = stackFrame.getLine();
        if (line > 0) {
            component.append(":" + line, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        component.append(", ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        Source source = stackFrame.getSource();
        String sourceName = getSourceNameFromNameOrPath(source);
        if (sourceName != null) {
            component.append(sourceName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        } else {
            component.append(XDebuggerBundle.message("invalid.frame"), SimpleTextAttributes.ERROR_ATTRIBUTES);
        }
        component.setIcon(AllIcons.Debugger.Frame);
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        if (sourcePosition != null) {
            return sourcePosition;
        }

        var source = stackFrame.getSource();
        if (source == null) {
            return null;
        }

        sourcePosition = doGetSourcePosition(source, stackFrame.getLine() - 1);
        return sourcePosition;
    }

    private @Nullable XSourcePosition doGetSourcePosition(@NotNull Source source, int line) {
        int sourceReference = source.getSourceReference() != null ? source.getSourceReference() : 0;
        if (sourceReference > 0) {
            // If the value > 0 the contents of the source must be retrieved through
            // the SourceRequest (even if a path is specified).
            var file = DAPFileRegistry.getInstance().getOrCreateDAPFile(client.getConfigName(), getValidSourceName(source), client.getProject());
            if (file.shouldReload(getClient().getSessionId())) {
                return new DAPSourceReferencePosition(file, sourceReference, line, client);
            }
            return XDebuggerUtil.getInstance().createPosition(file, line);
        }

        Path filePath = getValidFilePath(source);
        if (filePath == null) {
            return null;
        }
        if (ApplicationManager.getApplication().isDispatchThread()) {
            // getSourcePosition() is queried from UI code on the EDT (e.g. stack frames list rendering).
            // LocalFileSystem.findFileByPath() can hit PersistentFS and triggers SlowOperations on the EDT,
            // so we defer VFS resolution and notify the debugger UI once the position is ready.
            return new DeferredLocalFileSourcePosition(filePath, line);
        }

        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath.toString());
        if (file == null || !file.isValid()) {
            return null;
        }
        return XDebuggerUtil.getInstance().createPosition(file, line);
    }

    public CompletableFuture<XSourcePosition> getSourcePositionFor(@NotNull Variable variable) {
        var sourcePosition = getSourcePosition();
        if (sourcePosition == null) {
            return CompletableFuture.completedFuture(null);
        }
        return getVariablesContext()
                .thenApply(context -> context.getSourcePositionFor(variable));
    }

    /**
     * Drops the variables context of this stack frame, with the inline values which the language servers answered,
     * so that they are asked again the next time the variables of this frame are computed.
     */
    public synchronized void clearVariablesContext() {
        variablesContext = null;
    }

    private CompletableFuture<DebugVariableContext> getVariablesContext() {
        // read once: clearVariablesContext() can drop it at any time
        var context = variablesContext;
        return context != null ? context : getVariablesContextSync();
    }

    private synchronized CompletableFuture<DebugVariableContext> getVariablesContextSync() {
        if (variablesContext != null) {
            return variablesContext;
        }

        var context = new DebugVariableContext(this);
        Executor executor = AppExecutorUtil.getAppExecutorService();
        // The providers first load, without a read action, what may take time, such as the inline values that a
        // language server answers, then configure the context in a read action, which waits for nothing
        variablesContext = CompletableFuture.supplyAsync(context::loadContext, executor)
                .thenCompose(loaded -> loaded)
                .thenApplyAsync(unused -> {
                    ApplicationManager.getApplication().runReadAction(context::configureContext);
                    return context;
                }, executor);
        return variablesContext;
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
                    Scope[] scopeArray = scopes.getScopes();
                    if (scopeArray == null || scopeArray.length == 0) {
                        node.addChildren(new XValueChildrenList(), true);
                        return;
                    }
                    AtomicInteger remaining = new AtomicInteger(scopeArray.length);
                    Scope expanded = getAutoExpandedScope(scopeArray);
                    for (Scope scope : scopeArray) {
                        int parentVariablesReference = scope.getVariablesReference();
                        XValueChildrenList children = new XValueChildrenList();
                        VariablesArguments variablesArgs = new VariablesArguments();
                        variablesArgs.setVariablesReference(parentVariablesReference);
                        variablesArgs.setFormat(client.getValueFormat());
                        server.variables(variablesArgs)
                                .thenAccept(variablesResponse -> {
                                    children.addBottomGroup(new DAPValueGroup(this, scope.getName(),
                                            Arrays.asList(variablesResponse.getVariables()),
                                            parentVariablesReference,
                                            scope == expanded));
                                    node.addChildren(children, remaining.decrementAndGet() == 0);
                                });
                    }
                });
    }

    /**
     * Returns the scope whose group is expanded when the frame's variables are shown: the one which the DAP server
     * presents as the frame's local variables ('locals'), or else the first one. The debugger computes the values of
     * an expanded group's variables, and their inline values in the editor.
     *
     * @param scopes the scopes of the frame, in the order of the DAP server.
     * @return the scope whose group is expanded, or null if there is none.
     */
    static @Nullable Scope getAutoExpandedScope(@NotNull Scope[] scopes) {
        return Arrays.stream(scopes)
                .filter(scope -> ScopePresentationHint.LOCALS.equals(scope.getPresentationHint()))
                .findFirst()
                .orElse(scopes.length > 0 ? scopes[0] : null);
    }

    public @NotNull DAPClient getClient() {
        return client;
    }

    public int getFrameId() {
        return stackFrame.getId();
    }

    /**
     * Returns the stack frame which the DAP server sent.
     *
     * @return the stack frame which the DAP server sent.
     */
    public @NotNull StackFrame getStackFrame() {
        return stackFrame;
    }

    public @Nullable String getInstructionPointerReference() {
        return stackFrame.getInstructionPointerReference();
    }

    /**
     * Returns true if the debug adapter can restart this frame with the 'restartFrame' request and false otherwise.
     *
     * @return true if the debug adapter can restart this frame and false otherwise.
     */
    public boolean canRestart() {
        return canRestart(client.getCapabilities(), stackFrame);
    }

    /**
     * Returns true if the debug adapter supports the 'restartFrame' request (supportsRestartFrame) and the frame
     * doesn't say that it can't be restarted (canRestart), and false otherwise.
     *
     * @param capabilities the debug adapter capabilities.
     * @param stackFrame   the DAP stack frame.
     * @return true if the debug adapter can restart the frame and false otherwise.
     */
    static boolean canRestart(@NotNull Capabilities capabilities, @NotNull StackFrame stackFrame) {
        // A frame which doesn't say whether it can be restarted can be, when the debug adapter supports 'restartFrame'
        return Boolean.TRUE.equals(capabilities.getSupportsRestartFrame())
                && !Boolean.FALSE.equals(stackFrame.getCanRestart());
    }

    @Override
    public @Nullable XDebuggerEvaluator getEvaluator() {
        if (evaluator == null) {
            evaluator = new DAPDebuggerEvaluator(this);
        }
        return evaluator;
    }

    public @Nullable XSourcePosition getAlternativePosition() {
        String instructionPointerReference = getInstructionPointerReference();
        if (StringUtils.isEmpty(instructionPointerReference)) {
            return null;
        }
        var disassemblyFile = getClient().getDisassemblyFile();
        if (disassemblyFile == null || !FileEditorManager.getInstance(client.getProject()).isFileOpen(disassemblyFile)) {
            // Don't load the disassembly instruction source position when:
            // - the DAP server doesn't support Disassembly
            // - the disassembly view is not opened
            return null;
        }
        if (disassemblyInstructionSourcePosition != null && disassemblyInstructionSourcePosition.getModificationCount() == disassemblyFile.getModificationCount()) {
            return disassemblyInstructionSourcePosition;
        }
        disassemblyInstructionSourcePosition = new DisassemblyDeferredSourcePosition(instructionPointerReference, disassemblyFile, client);
        return disassemblyInstructionSourcePosition;
    }


    private static @NotNull String getValidSourceName(@NotNull Source source) {
        String sourceName = getSourceNameFromNameOrPath(source);
        if (sourceName != null) {
            return sourceName;
        }
        return source.getSourceReference() + "";
    }

    private static @Nullable String getSourceNameFromNameOrPath(@Nullable Source source) {
        if (source == null) {
            return null;
        }
        if (StringUtils.isNotBlank(source.getName())) {
            return source.getName();
        }
        if (StringUtils.isNotBlank(source.getPath())) {
            // Get the file source name from the path
            String path = source.getPath();
            int slashIndex = path.lastIndexOf('/');
            if (slashIndex == -1) {
                slashIndex = path.lastIndexOf('\\');
            }
            return slashIndex != -1 ? path.substring(slashIndex + 1) : path;
        }
        return null;
    }

}
