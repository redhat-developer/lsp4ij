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

package com.redhat.devtools.lsp4ij;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reference to a {@link Comparable} value that simplifies mutation of that value in lambdas/inner classes.
 */
@ApiStatus.Internal
public class ComparableRef<T extends Comparable<T>> extends MutableRef<T> {
    @NotNull
    public static <T extends Comparable<T>> ComparableRef<T> create(@Nullable T value) {
        return new ComparableRef<>(value);
    }

    private ComparableRef(@Nullable T value) {
        super(value);
    }

    /**
     * Updates the value to the highest ranked of the current value and the provided value.
     *
     * @param otherValue the other value
     */
    public void highestOf(@Nullable T otherValue) {
        T value = getValue();
        if ((value == null) || ((otherValue != null) && (value.compareTo(otherValue) < 0))) {
            set(otherValue);
        }
    }

    /**
     * Updates the value to the lowest ranked of the current value and the provided value.
     *
     * @param otherValue the other value
     */
    public void lowestOf(@Nullable T otherValue) {
        T value = getValue();
        if ((value == null) || ((otherValue != null) && (value.compareTo(otherValue) > 0))) {
            set(otherValue);
        }
    }
}
