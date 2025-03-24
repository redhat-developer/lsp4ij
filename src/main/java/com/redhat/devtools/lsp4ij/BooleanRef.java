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

/**
 * Reference to a boolean value that simplifies mutation of that value in lambdas/inner classes.
 */
@ApiStatus.Internal
public class BooleanRef extends MutableRef<Boolean> {
    @NotNull
    public static BooleanRef create(boolean value) {
        return new BooleanRef(value);
    }

    @NotNull
    public static BooleanRef create() {
        return create(false);
    }

    private BooleanRef(boolean value) {
        super(value);
    }

    @Override
    @NotNull
    public Boolean getValue() {
        //noinspection DataFlowIssue
        return super.getValue();
    }

    /**
     * Updates the value to the logical AND of the current value and the provided value.
     *
     * @param otherValue the other value for the logical AND operation
     */
    public void andEquals(boolean otherValue) {
        set(getValue() && otherValue);
    }

    /**
     * Updates the value to the logical OR of the current value and the provided value.
     *
     * @param otherValue the other value for the logical OR operation
     */
    public void orEquals(boolean otherValue) {
        set(getValue() || otherValue);
    }
}
