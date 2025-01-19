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
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation;
import com.intellij.xdebugger.frame.presentation.XStringValuePresentation;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.redhat.devtools.lsp4ij.internal.StringUtils;
import org.eclipse.lsp4j.debug.Variable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

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
        if (isStringType(type) || isNumberType(type) || isBooleanType(type)) {
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
        final String value = variable.getValue() != null ? variable.getValue() : "";
        final String type = variable.getType() != null ? variable.getType() : "";

        if (isStringType(type)) {
            String stringValue = value.replaceAll("^\"+|\"+$", "");
            return new XStringValuePresentation(stringValue);
        }

        if (isNumberType(type)) {
            return new XRegularValuePresentation(value, type) {
                @Override
                public void renderValue(@NotNull XValueTextRenderer renderer) {
                    renderer.renderValue(value, DefaultLanguageHighlighterColors.NUMBER);
                }
            };
        }

        if (isBooleanType(type)) {
            return new XValuePresentation() {
                @Override
                public void renderValue(@NotNull XValueTextRenderer renderer) {
                    renderer.renderValue(value, DefaultLanguageHighlighterColors.KEYWORD);
                }
            };
        }

        return new XRegularValuePresentation(value, type);
    }

    protected boolean isStringType(@NotNull String type) {
        return "string".equalsIgnoreCase(type);
    }

    protected boolean isBooleanType(@NotNull String type) {
        return "boolean".equalsIgnoreCase(type) ||
                "bool".equalsIgnoreCase(type);
    }

    protected boolean isNumberType(@NotNull String type) {
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

}
