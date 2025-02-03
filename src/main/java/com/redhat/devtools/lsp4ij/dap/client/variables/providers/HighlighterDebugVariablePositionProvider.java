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
package com.redhat.devtools.lsp4ij.dap.client.variables.providers;

import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.xdebugger.XSourcePosition;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * {@code HighlighterDebugVariablePositionProvider} is responsible for determining the source positions of variables
 * using a highlighter to inspect tokens in the editor. It works with multiple {@link VariableRangeRegistrar}
 * implementations to register variable ranges based on token types found in the document.
 */
public class HighlighterDebugVariablePositionProvider implements DebugVariablePositionProvider {

    private final List<VariableRangeRegistrar> variableRangeRegistrars;

    /**
     * Constructs a {@code HighlighterDebugVariablePositionProvider} using default registrars.
     */
    public HighlighterDebugVariablePositionProvider() {
        this(List.of(new TextMateVariableRangeRegistrar(), new DefaultVariableRangeRegistrar()));
    }

    /**
     * Constructs a {@code HighlighterDebugVariablePositionProvider} using custom registrars.
     *
     * @param variableRangeRegistrars List of custom {@link VariableRangeRegistrar}s to use for registering variable ranges.
     */
    public HighlighterDebugVariablePositionProvider(@NotNull List<VariableRangeRegistrar> variableRangeRegistrars) {
        this.variableRangeRegistrars = variableRangeRegistrars;
    }

    /**
     * Configures the context by scanning the editor's tokens and registering variable ranges using the provided
     * {@link VariableRangeRegistrar}s. The variable ranges are registered if the token types are identified as variables.
     *
     * @param context The debug variable context that provides necessary information for registering variables.
     */
    @Override
    public void configureContext(@NotNull DebugVariableContext context) {
        EditorEx editor = context.getEditor() instanceof EditorEx ? (EditorEx) context.getEditor() : null;
        if (editor == null) {
            return;
        }
        List<VariableRangeRegistrar> registrars = variableRangeRegistrars
                .stream()
                .filter((registrar -> registrar.isApplicable(editor.getVirtualFile(), editor.getProject())))
                .toList();
        int endLineOffset = context.getEndLineOffset();
        final EditorHighlighter highlighter = editor.getHighlighter();
        final HighlighterIterator iterator = highlighter.createIterator(0);
        while (!iterator.atEnd()) {
            if (iterator.getEnd() > endLineOffset) {
                break;
            }
            IElementType tokenType = iterator.getTokenType();
            for (var registrar : registrars) {
                if (registrar.tryRegisterVariableRange(tokenType, iterator.getStart(), iterator.getEnd(), editor.getDocument(), context)) {
                    break;
                }
            }
            iterator.advance();
        }
    }

    /**
     * Retrieves the source position of a variable from the given debug variable context.
     * It queries the context for the variable's source position using its name.
     *
     * @param variable The variable for which the source position is requested.
     * @param context  The debug variable context containing registered variables.
     * @return The source position of the variable, or null if not found.
     */
    @Override
    public @Nullable XSourcePosition getSourcePosition(@NotNull Variable variable,
                                                       @NotNull DebugVariableContext context) {
        return context.getSourcePosition(variable.getName());
    }
}
