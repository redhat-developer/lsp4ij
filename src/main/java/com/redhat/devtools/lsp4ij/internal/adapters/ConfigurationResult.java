/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.adapters;

import java.util.ArrayList;

/**
 * The settings answered to a workspace/configuration request, one for each requested item, which
 * {@link JsonSettingsTypeAdapterFactory} writes with the null members of the settings given as JSON.
 */
public final class ConfigurationResult extends ArrayList<Object> {
}
