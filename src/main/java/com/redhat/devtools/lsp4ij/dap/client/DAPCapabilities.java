/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.client;

import org.eclipse.lsp4j.debug.Capabilities;
import org.eclipse.lsp4j.debug.CapabilitiesEventArguments;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * The capabilities of a debug adapter: those of its 'initialize' response, updated by its DAP 'capabilities' events.
 * <p>
 * The event tells that one or more capabilities have changed (ex: once the debug adapter knows the target, after
 * 'launch' / 'attach', or when it loses it). It sends only the changed capabilities, all others keep their value:
 * a capability which the event sends replaces the known one, even with <code>false</code> or an empty array, and a
 * capability which it leaves out is unchanged.
 * <p>
 * Each update publishes a new {@link Capabilities} snapshot, which is never changed afterwards: a reader on any
 * thread sees either the capabilities before an event or after it, never a part of it. Events which arrive before
 * the 'initialize' response are kept in their order and applied on top of that response.
 * <p>
 * This state (the snapshot, its lock and the pending events) is kept out of {@link DAPClient}, so that it can be
 * tested on its own, without a debug adapter.
 */
@ApiStatus.Internal
public class DAPCapabilities {

    private static final Logger LOGGER = LoggerFactory.getLogger(DAPCapabilities.class);

    private final Object lock = new Object();

    /**
     * The current capabilities, or null before the 'initialize' response.
     */
    private volatile @Nullable Capabilities current;

    /**
     * The events received before the 'initialize' response, in their order. Guarded by {@link #lock}.
     */
    private final List<Capabilities> pendingUpdates = new ArrayList<>();

    /**
     * Stores the capabilities of the 'initialize' response, with the events received before it.
     *
     * @param response the capabilities of the 'initialize' response.
     * @return the current capabilities.
     */
    public @NotNull Capabilities initialize(@NotNull Capabilities response) {
        synchronized (lock) {
            Capabilities capabilities = merge(new Capabilities(), response);
            for (Capabilities update : pendingUpdates) {
                capabilities = merge(capabilities, update);
            }
            pendingUpdates.clear();
            current = capabilities;
            return capabilities;
        }
    }

    /**
     * Applies a DAP 'capabilities' event.
     *
     * @param args the 'capabilities' event.
     */
    public void update(@Nullable CapabilitiesEventArguments args) {
        Capabilities update = args != null ? args.getCapabilities() : null;
        if (update == null) {
            // The event has no capabilities: nothing changed
            return;
        }
        synchronized (lock) {
            Capabilities capabilities = current;
            if (capabilities == null) {
                // The 'initialize' response is not stored yet
                pendingUpdates.add(merge(new Capabilities(), update));
            } else {
                current = merge(capabilities, update);
            }
        }
    }

    /**
     * Returns the current capabilities, or default capabilities before the 'initialize' response.
     * <p>
     * The returned capabilities must not be changed.
     *
     * @return the current capabilities, or default capabilities before the 'initialize' response.
     */
    public @NotNull Capabilities get() {
        Capabilities capabilities = current;
        return capabilities != null ? capabilities : new Capabilities();
    }

    /**
     * Returns a copy of the given capabilities, with each capability which the given update sends (not null).
     * <p>
     * Every capability of LSP4J's {@link Capabilities} is an optional value with a getter and a setter: the merge
     * walks them all, so that a capability which LSP4J adds later is merged as well.
     *
     * @param capabilities the capabilities, which are not changed.
     * @param update       the changed capabilities.
     * @return the merged capabilities.
     */
    private static @NotNull Capabilities merge(@NotNull Capabilities capabilities,
                                               @NotNull Capabilities update) {
        Capabilities merged = new Capabilities();
        for (var accessor : CAPABILITY_ACCESSORS) {
            try {
                Object value = accessor.getter().invoke(update);
                if (value == null) {
                    value = accessor.getter().invoke(capabilities);
                }
                if (value != null) {
                    accessor.setter().invoke(merged, value);
                }
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Cannot update the debug adapter capability '" + accessor.getter().getName() + "'", e);
            }
        }
        return merged;
    }

    /**
     * A capability of {@link Capabilities}: its getter and its setter.
     */
    record CapabilityAccessor(@NotNull Method getter, @NotNull Method setter) {
    }

    /**
     * The getter and setter of each capability of {@link Capabilities}.
     */
    static final List<CapabilityAccessor> CAPABILITY_ACCESSORS = getCapabilityAccessors();

    private static List<CapabilityAccessor> getCapabilityAccessors() {
        List<CapabilityAccessor> accessors = new ArrayList<>();
        for (Method getter : Capabilities.class.getMethods()) {
            if (getter.getDeclaringClass() != Capabilities.class
                    || getter.getParameterCount() != 0
                    || !getter.getName().startsWith("get")) {
                continue;
            }
            try {
                Method setter = Capabilities.class.getMethod("set" + getter.getName().substring(3), getter.getReturnType());
                accessors.add(new CapabilityAccessor(getter, setter));
            } catch (NoSuchMethodException e) {
                // a getter without setter is not a capability
            }
        }
        return List.copyOf(accessors);
    }
}
