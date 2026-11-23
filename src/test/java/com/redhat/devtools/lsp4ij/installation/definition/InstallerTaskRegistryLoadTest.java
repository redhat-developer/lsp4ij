/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The registry creates the check and the run of an installer once each.
 */
public class InstallerTaskRegistryLoadTest {

    @Test
    void theCheckAndTheRunAreCreatedOnceEach() {
        List<String> created = new CopyOnWriteArrayList<>();
        var registry = new InstallerTaskRegistry();
        registry.registerFactory("task", (json, descriptor) -> {
            String name = json.get("name").getAsString();
            created.add(name);
            return new InstallerTask(null, name, null, null, descriptor) {
                @Override
                public boolean run(@NotNull InstallerContext context) {
                    return true;
                }
            };
        });

        var installer = registry.loadInstaller("""
                {
                  "name": "server",
                  "check": {"task": {"name": "check"}},
                  "run": {"task": {"name": "run"}}
                }""");

        assertEquals(List.of("check", "run"), created);
        assertNotNull(installer.getCheck());
        assertEquals("check", installer.getCheck().getName());
        assertNotNull(installer.getRun());
        assertEquals("run", installer.getRun().getName());
    }
}
