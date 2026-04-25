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
package com.redhat.devtools.lsp4ij.dap.stepping;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.dap.client.DAPClient;
import com.redhat.devtools.lsp4ij.dap.client.DAPStackFrame;
import com.redhat.devtools.lsp4ij.dap.client.DAPSuspendContext;
import com.redhat.devtools.lsp4ij.internal.CompletableFutures;
import org.eclipse.lsp4j.debug.StepInTarget;
import org.eclipse.lsp4j.debug.StepInTargetsResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Handles "Smart Step Into" functionality for DAP debug sessions.
 * When multiple function calls exist on a single line, this handler allows the user
 * to choose which function to step into via the DAP stepInTargets request.
 */
public class DAPSmartStepIntoHandler extends XSmartStepIntoHandler<DAPStepIntoVariant> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPSmartStepIntoHandler.class);

    private final @NotNull XDebugSession session;

    /**
     * Creates a new Smart Step Into handler.
     *
     * @param session the debug session
     */
    public DAPSmartStepIntoHandler(@NotNull XDebugSession session) {
        this.session = session;
    }

    @Override
    public @NotNull List<DAPStepIntoVariant> computeSmartStepVariants(@NotNull XSourcePosition position) {
        CompletableFuture<List<DAPStepIntoVariant>> future = getStepInTargetsFuture(position);

        try {
            CompletableFutures.waitUntilDone(future, (com.intellij.psi.PsiFile) null, 5000);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (ExecutionException e) {
            LOGGER.warn("Error getting smart step variants", e);
            return Collections.emptyList();
        } catch (java.util.concurrent.TimeoutException e) {
            return Collections.emptyList();
        }

        List<DAPStepIntoVariant> result = future.getNow(null);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * Helper method to get the CompletableFuture for step-in targets.
     * Extracted to avoid code duplication between sync and async versions.
     */
    private CompletableFuture<List<DAPStepIntoVariant>> getStepInTargetsFuture(@NotNull XSourcePosition position) {
        XSuspendContext suspendContext = session.getSuspendContext();
        if (!(suspendContext instanceof DAPSuspendContext dapContext)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        var activeStack = dapContext.getActiveExecutionStack();
        if (activeStack == null) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        var topFrame = activeStack.getTopFrame();
        if (!(topFrame instanceof DAPStackFrame dapFrame)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        DAPClient client = dapFrame.getClient();

        // Check capability
        if (!client.isSupportsStepInTargetsRequest()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        int frameId = dapFrame.getFrameId();
        CompletableFuture<StepInTargetsResponse> responseFuture = client.stepInTargets(frameId);

        // Transform the response into variants
        Document document = LSPIJUtils.getDocument(position.getFile());
        int zeroBasedLine = position.getLine();

        return responseFuture.handle((response, throwable) -> {
            if (throwable != null) {
                LOGGER.error("Error while fetching step-in targets from DAP server", throwable);
                return Collections.emptyList();
            }

            if (response == null || response.getTargets() == null || response.getTargets().length == 0) {
                return Collections.emptyList();
            }

            List<DAPStepIntoVariant> variants = new ArrayList<>();

            // Track how many times we've seen each function name to handle duplicates
            Map<String, Integer> functionNameCounts = new java.util.HashMap<>();

            for (StepInTarget target : response.getTargets()) {
                // Extract function name from label to count occurrences
                String label = target.getLabel();
                String functionName = label;
                int parenIndex = label.indexOf('(');
                if (parenIndex > 0) {
                    functionName = label.substring(0, parenIndex);
                }

                // Get the occurrence index for this function name
                int occurrenceIndex = functionNameCounts.getOrDefault(functionName, 0);
                functionNameCounts.put(functionName, occurrenceIndex + 1);

                DAPStepIntoVariant variant = new DAPStepIntoVariant(target, document, zeroBasedLine, occurrenceIndex);
                variants.add(variant);
            }
            return variants;
        });
    }

    @Override
    public @NotNull Promise<List<DAPStepIntoVariant>> computeSmartStepVariantsAsync(
            @NotNull XSourcePosition position) {

        // Get the CompletableFuture and wrap it in an AsyncPromise
        CompletableFuture<List<DAPStepIntoVariant>> future = getStepInTargetsFuture(position);

        AsyncPromise<List<DAPStepIntoVariant>> promise = new AsyncPromise<>();
        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                promise.setResult(Collections.emptyList());
            } else {
                promise.setResult(result != null ? result : Collections.emptyList());
            }
        });

        return promise;
    }

    @Override
    public void startStepInto(@NotNull DAPStepIntoVariant variant, @Nullable XSuspendContext context) {
        if (!(context instanceof DAPSuspendContext dapContext)) {
            return;
        }

        Integer threadId = dapContext.getThreadId();
        if (threadId == null) {
            return;
        }

        var activeStack = dapContext.getActiveExecutionStack();
        if (activeStack == null) {
            return;
        }

        var topFrame = activeStack.getTopFrame();
        if (!(topFrame instanceof DAPStackFrame dapFrame)) {
            return;
        }

        DAPClient client = dapFrame.getClient();

        // Get stepping granularity (null for normal stepping, INSTRUCTION for disassembly)
        var granularity = getSteppingGranularity(client);

        // Execute stepIn with targetId
        Integer targetId = variant.getTargetId();
        client.stepIn(threadId, targetId, granularity);
    }

    @Override
    public void stepIntoEmpty(XDebugSession session) {
        // Fallback to regular step into when no variants available
        session.stepInto();
    }

    @Override
    public @Nullable String getPopupTitle(@NotNull XSourcePosition position) {
        return getPopupTitle();
    }

    //@Override
    public @Nullable String getPopupTitle() {
        // In 2026.1 this method must be implemented
        // See https://github.com/JetBrains/intellij-community/blob/f2a2af473deafff16e900aa725b60c8fb0712039/platform/xdebugger-api/src/com/intellij/xdebugger/stepping/XSmartStepIntoHandler.java#L79
        return "Choose Method to Step Into";
    }

    /**
     * Gets the stepping granularity for the current session.
     * Returns INSTRUCTION granularity when disassembly mode is active, null otherwise.
     *
     * @param client the DAP client
     * @return the stepping granularity, or null for default (source line) granularity
     */
    private @Nullable org.eclipse.lsp4j.debug.SteppingGranularity getSteppingGranularity(@NotNull DAPClient client) {
        // Note: For Smart Step Into, we typically use source-level granularity
        // Disassembly-level stepping with target selection is rarely needed
        // If needed in the future, this could access DAPDebugProcess.getAlternativeSourceHandler()
        return null;
    }
}
