/*******************************************************************************
 * Copyright (c) 2023 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.commands;

import com.google.gson.JsonElement;
import com.redhat.devtools.lsp4ij.JSONUtils;
import org.eclipse.lsp4j.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LSP command which wraps an LSP4J {@link Command}.
 */
public class LSPCommand extends Command {

    private final ClassLoader classLoader;

    private final List<Object> originalArguments;

    private final List<Object> arguments;

    LSPCommand(Command command, ClassLoader classLoader) {
        this.classLoader = classLoader;
        super.setTitle(command.getTitle());
        super.setCommand(command.getCommand());
        this.originalArguments = command.getArguments();
        if (originalArguments == null || originalArguments.isEmpty()) {
            arguments = Collections.emptyList();
        } else {
            arguments = new ArrayList<>(this.originalArguments.size());
            for (int i = 0; i < originalArguments.size(); i++) {
                arguments.add(null);
            }
        }
    }

    /**
     * Returns the original arguments of the wrapped command.
     *
     * @return the original arguments of the wrapped command.
     */
    public List<Object> getOriginalArguments() {
        return originalArguments;
    }

    /**
     * Returns the arguments which contain JSonElement loaded by the classloader of the plugin which defines the IntelliJ AnAction which process the command.
     *
     * @return the arguments which contain JSonElement loaded by the classloader of the plugin which defines the IntelliJ AnAction which process the command.
     */
    @Override
    public @NotNull List<Object> getArguments() {
        if (originalArguments == null || originalArguments.isEmpty()) {
            return Collections.emptyList();
        }
        for (int i = 0; i < originalArguments.size(); i++) {
            getArgumentAt(i, true);
        }
        return arguments;
    }

    /**
     * Returns the argument at the given index and null otherwise.
     *
     * @param index the index.
     * @return the argument at the given index and null otherwise.
     */
    public @Nullable Object getArgumentAt(int index) {
        return getArgumentAt(index, true);
    }

    private @Nullable Object getArgumentAt(int index, boolean checkClassLoader) {
        if (originalArguments == null || index > originalArguments.size()) {
            return null;
        }
        Object arg = arguments.get(index);
        if (arg == null) {
            Object originalArg = originalArguments.get(index);
            if (originalArg == null) {
                return null;
            }
            arg = originalArg;
            if (checkClassLoader && arg instanceof JsonElement jsonElt) {
                arg = JSONUtils.getJsonElementFromClassloader(jsonElt, classLoader);
            }
            arguments.set(index, arg);
        }
        return arg;
    }

    /**
     * Returns the converted argument at the given index and null otherwise.
     *
     * @param index the index.
     * @param type  the class type.
     * @return the converted argument at the given index and null otherwise.
     */
    public @Nullable <T> T getArgumentAt(int index, Class<T> type) {
        Object arg = getArgumentAt(index, false);
        return arg != null ? JSONUtils.toModel(arg, type) : null;
    }
}
