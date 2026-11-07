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
package com.redhat.devtools.lsp4ij.dap.breakpoints;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.lang.reflect.Method;

/**
 * Wraps JetBrains internal API calls for breakpoint operations using cached reflection, so that the
 * plugin has no compile-time dependency on internal classes ({@code XBreakpointBase}, {@code XDebugSessionImpl},
 * {@code XBreakpointManagerImpl}, {@code CustomizedBreakpointPresentation}).
 * <p>
 * Each {@link Method} is resolved once at class-load time and cached. A {@code null} field means the
 * internal API was not found, and the operation becomes a no-op.
 */
final class BreakpointInternalSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(BreakpointInternalSupport.class);

    // Internal classes, resolved by name to avoid compile-time dependency
    private static final @Nullable Class<?> X_BREAKPOINT_BASE_CLASS =
            findClass("com.intellij.xdebugger.impl.breakpoints.XBreakpointBase");
    private static final @Nullable Class<?> X_DEBUG_SESSION_IMPL_CLASS =
            findClass("com.intellij.xdebugger.impl.XDebugSessionImpl");
    private static final @Nullable Class<?> X_BREAKPOINT_MANAGER_IMPL_CLASS =
            findClass("com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl");
    private static final @Nullable Class<?> CUSTOMIZED_PRESENTATION_CLASS =
            findClass("com.intellij.xdebugger.impl.breakpoints.CustomizedBreakpointPresentation");

    // XBreakpointBase methods
    private static final @Nullable Method FIRE_BREAKPOINT_CHANGED =
            findMethod(X_BREAKPOINT_BASE_CLASS, "fireBreakpointChanged");
    private static final @Nullable Method CLEAR_ICON =
            findMethod(X_BREAKPOINT_BASE_CLASS, "clearIcon");
    private static final @Nullable Method EMIT_BREAKPOINT_CHANGED =
            findMethod(X_BREAKPOINT_BASE_CLASS, "emitBreakpointChanged");

    // XDebugSessionImpl methods
    private static final @Nullable Method GET_BREAKPOINT_PRESENTATION =
            findMethod(X_DEBUG_SESSION_IMPL_CLASS, "getBreakpointPresentation", XBreakpoint.class);

    // CustomizedBreakpointPresentation methods
    private static final @Nullable Method GET_ICON =
            findMethod(CUSTOMIZED_PRESENTATION_CLASS, "getIcon");
    private static final @Nullable Method SET_ICON =
            findMethod(CUSTOMIZED_PRESENTATION_CLASS, "setIcon", Icon.class);
    private static final @Nullable Method GET_ERROR_MESSAGE =
            findMethod(CUSTOMIZED_PRESENTATION_CLASS, "getErrorMessage");
    private static final @Nullable Method SET_ERROR_MESSAGE =
            findMethod(CUSTOMIZED_PRESENTATION_CLASS, "setErrorMessage", String.class);

    // XBreakpointManagerImpl methods
    private static final @Nullable Method FIRE_PRESENTATION_UPDATED =
            findFireBreakpointPresentationUpdated();

    private BreakpointInternalSupport() {
    }

    /**
     * Calls {@code XBreakpointBase.fireBreakpointChanged()} on the given breakpoint via reflection.
     *
     * @param breakpoint the breakpoint.
     */
    static void fireBreakpointChanged(@NotNull XBreakpoint<?> breakpoint) {
        invoke(FIRE_BREAKPOINT_CHANGED, breakpoint, "Cannot fire breakpoint changed");
    }

    /**
     * Calls {@code XBreakpointBase.clearIcon()} on the given breakpoint via reflection.
     *
     * @param breakpoint the breakpoint.
     */
    static void clearIcon(@NotNull XBreakpoint<?> breakpoint) {
        invoke(CLEAR_ICON, breakpoint, "Cannot clear breakpoint icon");
    }

    /**
     * Calls {@code XBreakpointBase.emitBreakpointChanged()} on the given breakpoint via reflection
     * (available since IntelliJ 2025.2).
     *
     * @param breakpoint the breakpoint.
     */
    static void emitBreakpointChanged(@NotNull XBreakpoint<?> breakpoint) {
        invoke(EMIT_BREAKPOINT_CHANGED, breakpoint, "Cannot refresh the tooltip of the breakpoint");
    }

    /**
     * Updates the presentation (icon and error message) of a non-line breakpoint in the session, using
     * internal APIs ({@code XDebugSessionImpl.getBreakpointPresentation()} and
     * {@code CustomizedBreakpointPresentation}).
     *
     * @return {@code true} if the presentation was changed, {@code false} if unchanged or not applicable.
     */
    static boolean updatePresentation(@NotNull XDebugSession debugSession,
                                      @NotNull XBreakpoint<?> breakpoint,
                                      @Nullable Icon icon,
                                      @Nullable String errorMessage) {
        if (GET_BREAKPOINT_PRESENTATION == null || GET_ICON == null || GET_ERROR_MESSAGE == null
                || SET_ICON == null || SET_ERROR_MESSAGE == null) {
            return false;
        }
        if (X_DEBUG_SESSION_IMPL_CLASS == null || !X_DEBUG_SESSION_IMPL_CLASS.isInstance(debugSession)) {
            return false;
        }
        try {
            Object presentation = GET_BREAKPOINT_PRESENTATION.invoke(debugSession, breakpoint);
            if (presentation == null) {
                return false;
            }
            Icon currentIcon = (Icon) GET_ICON.invoke(presentation);
            String currentErrorMessage = (String) GET_ERROR_MESSAGE.invoke(presentation);
            if (Comparing.equal(currentIcon, icon) && Comparing.strEqual(currentErrorMessage, errorMessage)) {
                return false;
            }
            SET_ICON.invoke(presentation, icon);
            SET_ERROR_MESSAGE.invoke(presentation, errorMessage);
            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Cannot update breakpoint presentation", e);
            return false;
        }
    }

    /**
     * Fires {@code XBreakpointManagerImpl.fireBreakpointPresentationUpdated()} via reflection, on the
     * EDT, to refresh the breakpoint's visual state.
     */
    static void fireBreakpointPresentationUpdated(@NotNull Project project,
                                                  @NotNull XBreakpoint<?> breakpoint,
                                                  @NotNull XDebugSession debugSession) {
        if (FIRE_PRESENTATION_UPDATED == null || X_BREAKPOINT_MANAGER_IMPL_CLASS == null) {
            return;
        }
        var breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
        if (!X_BREAKPOINT_MANAGER_IMPL_CLASS.isInstance(breakpointManager)) {
            return;
        }
        ApplicationManager.getApplication().invokeLater(
                () -> {
                    try {
                        FIRE_PRESENTATION_UPDATED.invoke(breakpointManager, breakpoint, debugSession);
                    } catch (ReflectiveOperationException e) {
                        LOGGER.warn("Cannot fire breakpoint presentation updated", e);
                    }
                },
                ModalityState.any(),
                project.getDisposed());
    }

    // -- Reflection helpers -----------------------------------------------------------------------

    /**
     * Invokes the given no-arg method on the breakpoint if both the method and the target class are resolved
     * and the breakpoint is an instance of the target class.
     */
    private static void invoke(@Nullable Method method, @NotNull XBreakpoint<?> breakpoint, @NotNull String errorMessage) {
        if (method != null && X_BREAKPOINT_BASE_CLASS != null && X_BREAKPOINT_BASE_CLASS.isInstance(breakpoint)) {
            try {
                method.invoke(breakpoint);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn(errorMessage, e);
            }
        }
    }

    private static @Nullable Class<?> findClass(@NotNull String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static @Nullable Method findMethod(@Nullable Class<?> clazz, @NotNull String name, @NotNull Class<?>... paramTypes) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Resolves {@code XBreakpointManagerImpl.fireBreakpointPresentationUpdated}: the second parameter may
     * be declared as {@code XDebugSession} or as {@code XDebugSessionImpl}, depending on the IntelliJ version.
     */
    private static @Nullable Method findFireBreakpointPresentationUpdated() {
        if (X_BREAKPOINT_MANAGER_IMPL_CLASS == null) return null;
        var method = findMethod(X_BREAKPOINT_MANAGER_IMPL_CLASS,
                "fireBreakpointPresentationUpdated", XBreakpoint.class, XDebugSession.class);
        if (method != null) return method;
        if (X_DEBUG_SESSION_IMPL_CLASS != null) {
            return findMethod(X_BREAKPOINT_MANAGER_IMPL_CLASS,
                    "fireBreakpointPresentationUpdated", XBreakpoint.class, X_DEBUG_SESSION_IMPL_CLASS);
        }
        return null;
    }
}
