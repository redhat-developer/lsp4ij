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
package com.redhat.devtools.lsp4ij.dap.sources;

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.eclipse.lsp4j.debug.Module;
import org.eclipse.lsp4j.debug.ModuleEventArguments;
import org.eclipse.lsp4j.debug.ModuleEventArgumentsReason;

import java.util.List;

/**
 * The Modules panel shows the modules which the DAP server reports, in its order, and its 'module' events add, change
 * and remove them by id.
 */
public class ModulesPanelTest extends BasePlatformTestCase {

    public void testShowsTheModulesAndTheEventsUpdateThemById() {
        var panel = new ModulesPanel();
        panel.refresh(new Module[]{module("app", "1.0"), module("lib", "2.0")});
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals(List.of("app", "lib"), names(panel));

        // a changed module stays where it is, a new one comes last, and a removed one goes
        panel.update(event(ModuleEventArgumentsReason.CHANGED, module("lib", "2.1")));
        panel.update(event(ModuleEventArgumentsReason.NEW, module("extra", null)));
        panel.update(event(ModuleEventArgumentsReason.REMOVED, module("app", null)));
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
        assertEquals(List.of("lib", "extra"), names(panel));
        assertEquals("2.1", panel.getModules().get(0).getVersion());
    }

    private static List<String> names(ModulesPanel panel) {
        return panel.getModules().stream().map(Module::getName).toList();
    }

    private static Module module(String name, String version) {
        var module = new Module();
        module.setId(name);
        module.setName(name);
        module.setVersion(version);
        return module;
    }

    private static ModuleEventArguments event(ModuleEventArgumentsReason reason, Module module) {
        var args = new ModuleEventArguments();
        args.setReason(reason);
        args.setModule(module);
        return args;
    }
}
