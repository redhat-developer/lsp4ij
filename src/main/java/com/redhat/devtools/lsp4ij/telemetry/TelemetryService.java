/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
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
package com.redhat.devtools.lsp4ij.telemetry;

import java.util.Map;

/**
 * Telemetry Service facade
 */
public interface TelemetryService {

    /**
     * Sends a tracking event without additional properties.
     * @param eventName the name of the event
     */
    default void send(String eventName){
        send(eventName, null);
    }

    /**
     * Sends a tracking event with additional properties.
     * @param eventName the name of the event
     * @param properties the properties of the event
     */
    default void send(String eventName, Map<String, String> properties){}
}
