/*******************************************************************************
 * Copyright (c) 2023 Red Hat Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.lsp4ij.operations.diagnostics;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.lang.annotation.HighlightSeverity;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class to map language servers' {@link DiagnosticSeverity} to Intellij's {@link HighlightSeverity}, and vice-versa.
 */
public class SeverityMapping {

    public static String NONE_SEVERITY = "none";

    private SeverityMapping() {
    }

    /**
     * Maps language server's {@link DiagnosticSeverity} to Intellij's {@link HighlightSeverity}
     * @param severity the {@link DiagnosticSeverity} to map
     * @return the matching {@link HighlightSeverity}
     */
    public static @NotNull HighlightSeverity toHighlightSeverity(@Nullable DiagnosticSeverity severity) {
        if (severity == null) {
            return HighlightSeverity.INFORMATION;
        }
        switch (severity) {
            case Warning:
                return HighlightSeverity.WEAK_WARNING;
            case Hint:
            case Information:
                return HighlightSeverity.INFORMATION;
            default:
                return HighlightSeverity.ERROR;
        }
    }

    /**
     * Maps {@link HighlightSeverity} to {@link DiagnosticSeverity} levels used by language servers.
     * <ul>
     *     <li>Any severity below <code>HighlightSeverity.INFORMATION</code> is mapped to <code>null</code></li>
     *     <li>Any severity below <code>HighlightSeverity.WEAK_WARNING</code> is mapped to <code>DiagnosticSeverity.Information</code></li>
     *     <li>Any severity below <code>HighlightSeverity.ERROR</code> is mapped to <code>DiagnosticSeverity.Warning</code></li>
     *     <li>Any other severity is mapped to <code>DiagnosticSeverity.Error</code></li>
     * </ul>
     *
     * @param severity the severity to map to a {@link DiagnosticSeverity}
     * @return the matching {@link DiagnosticSeverity}
     */
    public static @Nullable DiagnosticSeverity getSeverity(@NotNull HighlightSeverity severity) {
        if (HighlightSeverity.INFORMATION.compareTo(severity) > 0) {
            return null;
        }
        if (HighlightSeverity.WEAK_WARNING.compareTo(severity) > 0) {
            return DiagnosticSeverity.Information;
        }
        if (HighlightSeverity.ERROR.compareTo(severity) > 0) {
            return DiagnosticSeverity.Warning;
        }
        return DiagnosticSeverity.Error;
    }

    /**
     * Returns {@link DiagnosticSeverity} as lower case, or <code>none</code> if severity is <code>null</code>.
     * @param severity the {@link DiagnosticSeverity} to transform as {@link String}
     * @return {@link DiagnosticSeverity} as lower case, or <code>none</code> if severity is <code>null</code>.
     */
    public static @NotNull String toString(@Nullable DiagnosticSeverity severity) {
        return (severity == null)? NONE_SEVERITY : severity.name().toLowerCase();
    }

    public static DiagnosticSeverity getSeverity(String inspectionId, InspectionProfile profile) {
        if (!isInspectionEnabled(inspectionId, profile)) {
            return null;
        }
        return getSeverity(getErrorLevel(inspectionId, profile));
    }

    private static @NotNull HighlightSeverity getErrorLevel(String inspectionId, InspectionProfile profile) {
        HighlightDisplayLevel level = profile.getErrorLevel(HighlightDisplayKey.find(inspectionId), null);
        return level.getSeverity();
    }

    private static boolean isInspectionEnabled(@NotNull String inspectionId, @NotNull InspectionProfile profile) {
        return profile.isToolEnabled(HighlightDisplayKey.find(inspectionId));
    }
}
