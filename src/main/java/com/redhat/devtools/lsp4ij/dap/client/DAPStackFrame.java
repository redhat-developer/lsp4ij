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
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.impl.XSourcePositionEx;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPFileRegistry;
import com.redhat.devtools.lsp4ij.dap.client.files.DAPSourceReferencePosition;
import com.redhat.devtools.lsp4ij.dap.client.variables.DAPValueGroup;
import com.redhat.devtools.lsp4ij.dap.client.variables.providers.DebugVariableContext;
import com.redhat.devtools.lsp4ij.dap.disassembly.DisassemblyDeferredSourcePosition;
import com.redhat.devtools.lsp4ij.dap.evaluation.DAPDebuggerEvaluator;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.MutableStateFlow;
import kotlinx.coroutines.flow.StateFlowKt;
import org.eclipse.lsp4j.debug.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

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
    private CompletableFuture<DebugVariableContext> variablesContext;

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
        XSourcePosition cached = sourcePosition;
        if (cached != null) {
            return cached;
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

        VirtualFile file = LocalFileSystem.getInstance().findFileByPath(filePath.toString());
        if (file == null || !file.isValid()) {
            return null;
        }

        if (ApplicationManager.getApplication().isDispatchThread()) {
            return new DeferredLocalFileSourcePosition(file, line);
        }

        return XDebuggerUtil.getInstance().createPosition(file, line);
    }

    private static final class DeferredLocalFileSourcePosition implements XSourcePositionEx {
        private final @NotNull VirtualFile file;
        private final int line;
        private final @NotNull MutableStateFlow<Boolean> positionUpdateFlow = StateFlowKt.MutableStateFlow(false);
        private volatile int offset = -1;

        private DeferredLocalFileSourcePosition(@NotNull VirtualFile file, int line) {
            this.file = file;
            this.line = line;
            // `getSourcePosition()` may be called on EDT; precompute offset in background and notify the debugger UI.
            ApplicationManager.getApplication().executeOnPooledThread(() -> {
                int resolvedOffset = com.intellij.openapi.application.ReadAction.compute(() -> computeOffset(file, line));
                offset = resolvedOffset;
                positionUpdateFlow.setValue(true);
            });
        }

        @Override
        public int getLine() {
            return line;
        }

        @Override
        public int getOffset() {
            return offset;
        }

        @Override
        public @NotNull VirtualFile getFile() {
            return file;
        }

        @Override
        public @NotNull Navigatable createNavigatable(@NotNull Project project) {
            int resolvedOffset = offset;
            if (resolvedOffset >= 0) {
                return new OpenFileDescriptor(project, file, resolvedOffset);
            }
            return new OpenFileDescriptor(project, file, Math.max(0, line), 0);
        }

        @Override
        public @NotNull Flow<Boolean> getPositionUpdateFlow() {
            return positionUpdateFlow;
        }

        private static int computeOffset(@NotNull VirtualFile file, int line) {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) {
                return -1;
            }
            int l = Math.max(0, line);
            int offset = l < document.getLineCount() ? document.getLineStartOffset(l) : -1;
            if (offset >= document.getTextLength()) {
                offset = document.getTextLength() - 1;
            }
            return offset;
        }
    }

    public CompletableFuture<XSourcePosition> getSourcePositionFor(@NotNull Variable variable) {
        var sourcePosition = getSourcePosition();
        if (sourcePosition == null) {
            return CompletableFuture.completedFuture(null);
        }
        return getVariablesContext()
                .thenApply(context -> context.getSourcePositionFor(variable));
    }

    private CompletableFuture<DebugVariableContext> getVariablesContext() {
        if (variablesContext != null) {
            return variablesContext;
        }
        return getVariablesContextSync();
    }

    private synchronized CompletableFuture<DebugVariableContext> getVariablesContextSync() {
        if (variablesContext != null) {
            return variablesContext;
        }

        var context = new DebugVariableContext(this);
        CompletableFuture<DebugVariableContext> future = new CompletableFuture<>();
        ApplicationManager.getApplication().executeOnPooledThread(() ->
                ApplicationManager.getApplication().runReadAction(() -> {
                    context.configureContext();
                    future.complete(context);
                }));
        variablesContext = future;
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
                    for (Scope scope : scopes.getScopes()) {
                        int parentVariablesReference = scope.getVariablesReference();
                        XValueChildrenList children = new XValueChildrenList();
                        VariablesArguments variablesArgs = new VariablesArguments();
                        variablesArgs.setVariablesReference(parentVariablesReference);
                        server.variables(variablesArgs)
                                .thenAccept(variablesResponse -> {
                                    children.addBottomGroup(new DAPValueGroup(this, scope.getName(),
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

    public @Nullable String getInstructionPointerReference() {
        return stackFrame.getInstructionPointerReference();
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
