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
package com.redhat.devtools.lsp4ij.dap.descriptors;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.presentation.*;
import com.redhat.devtools.lsp4ij.dap.client.variables.providers.DebugVariablePositionProvider;
import com.redhat.devtools.lsp4ij.dap.client.variables.providers.HighlighterDebugVariablePositionProvider;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * Debug Adapter Protocol (DAP) variable support.
 */
public class DebugAdapterVariableSupport {

    private @NotNull DebugAdapterDescriptor serverDescriptor;

    /**
     * Returns the icon for the given variable.
     *
     * @param variable the variable.
     *
     * @return the icon for the given variable.
     */
    @NotNull
    public Icon getIcon(@NotNull Variable variable) {
        String type = variable.getType();
        if (StringUtils.isEmpty(type)) {
            return AllIcons.Nodes.Variable;
        }
        if (isStringType(type) || isNumericType(type) || isBooleanType(type)) {
            return AllIcons.Debugger.Db_primitive;
        }
        if (isObjectType(type)) {
            return AllIcons.Debugger.Db_db_object;
        }
        if (isArrayType(type)) {
            return AllIcons.Debugger.Db_array;
        }
        if (isXmlType(type)) {
            return AllIcons.FileTypes.Xml;
        }
        if (isErrorType(type)) {
            return AllIcons.Nodes.ExceptionClass;
        }
        return AllIcons.Nodes.Variable;
    }

    /**
     * Returns the value representation for the given variable.
     *
     * @param variable the variable.
     *
     * @return value representation for the given variable.
     */
    public @NotNull XValuePresentation getValuePresentation(@NotNull Variable variable) {
        final String type = variable.getType() != null ? variable.getType() : "";
        final String formattedValue = formatValue(variable);

        // String type
        if (isStringType(type)) {
            return new XStringValuePresentation(formattedValue);
        }

        // Numeric type
        if (isNumericType(type)) {
            return new XNumericValuePresentation(formattedValue);
        }

        // Boolean type
        if (isBooleanType(type)) {
            return new XKeywordValuePresentation(formattedValue);
        }

        // Other type
        return new XRegularValuePresentation(formattedValue, type);
    }

    @NotNull
    protected String formatValue(@NotNull Variable variable) {
        final String value = variable.getValue() != null ? variable.getValue() : "";
        final String type = variable.getType() != null ? variable.getType() : "";
        if (isStringType(type)) {
            // String type (ex:value='foo' or "foo")
            // Remove start/end simple and double quote
            // to display only foo
            return removeQuotes(value);
        }
        return value;
    }

    protected boolean isStringType(@NotNull String type) {
        return "string".equalsIgnoreCase(type);
    }

    protected boolean isBooleanType(@NotNull String type) {
        return "boolean".equalsIgnoreCase(type) ||
                "bool".equalsIgnoreCase(type);
    }

    protected boolean isNumericType(@NotNull String type) {
        return "number".equalsIgnoreCase(type) ||
                "int".equalsIgnoreCase(type) ||
                "long".equalsIgnoreCase(type) ||
                "float".equalsIgnoreCase(type) ||
                "double".equalsIgnoreCase(type);
    }

    protected boolean isObjectType(@NotNull String type) {
        return "object".equalsIgnoreCase(type);
    }

    protected boolean isArrayType(@NotNull String type) {
        return "array".equalsIgnoreCase(type);
    }

    protected boolean isXmlType(@NotNull String type) {
        return "xml".equalsIgnoreCase(type);
    }

    protected boolean isErrorType(@NotNull String type) {
        return "error".equalsIgnoreCase(type);
    }

    public @NotNull DebugAdapterDescriptor getServerDescriptor() {
        return serverDescriptor;
    }

    public void setServerDescriptor(@NotNull DebugAdapterDescriptor serverDescriptor) {
        this.serverDescriptor = serverDescriptor;
    }

    public @NotNull Collection<DebugVariablePositionProvider> getDebugVariablePositionProvider() {
        return Collections.singletonList(new HighlighterDebugVariablePositionProvider());
    }

    @NotNull
    private static String removeQuotes(@NotNull String value) {
        return value
                .replaceAll("^\"+|\"+$", "")
                .replaceAll("^\'+|\'+$", "");
    }
}
