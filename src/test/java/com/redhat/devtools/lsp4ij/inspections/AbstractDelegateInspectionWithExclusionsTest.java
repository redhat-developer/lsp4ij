/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.inspections;

import com.intellij.codeInspection.options.OptStringList;
import com.intellij.codeInspection.ui.OptionPaneRenderer;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jdom.Element;

import java.util.List;

/**
 * The exclusions of an {@link AbstractDelegateInspectionWithExclusions} are saved as before, and its options edit
 * {@link AbstractDelegateInspectionWithExclusions#excludeList}, which Quarkus Tools and Liberty Tools read and change.
 */
public class AbstractDelegateInspectionWithExclusionsTest extends BasePlatformTestCase {

    /** The exclusions saved in an inspection profile, as LSP4IJ writes them */
    private static final String SAVED_EXCLUSIONS = """
            <tool>
              <option name="excludeList">
                <list>
                  <option value="quarkus.http.*" />
                  <option value="*/mp-rest/url" />
                </list>
              </option>
            </tool>""";

    public void testTheExclusionsAreSavedAsBefore() {
        var inspection = new ExclusionsInspection();
        inspection.excludeList.addAll(List.of("quarkus.http.*", "*/mp-rest/url"));

        var element = new Element("tool");
        inspection.writeSettings(element);

        assertEquals(SAVED_EXCLUSIONS, JDOMUtil.write(element));
    }

    public void testSavedExclusionsAreReadAsBefore() throws Exception {
        var inspection = new ExclusionsInspection();

        inspection.readSettings(JDOMUtil.load(SAVED_EXCLUSIONS));

        assertEquals(List.of("quarkus.http.*", "*/mp-rest/url"), inspection.excludeList);
    }

    /**
     * Quarkus Tools' {@code MicroProfilePropertiesUnknownInspection} adds default exclusions in its constructor:
     * they aren't saved until they change.
     */
    public void testDefaultExclusionsAreSavedOnlyOnceChanged() {
        var inspection = new DefaultExclusionsInspection();
        var unchanged = new Element("tool");
        inspection.writeSettings(unchanged);
        assertEquals("<tool />", JDOMUtil.write(unchanged));

        inspection.excludeList.add("quarkus.http.*");
        var changed = new Element("tool");
        inspection.writeSettings(changed);
        assertEquals("""
                <tool>
                  <option name="excludeList">
                    <list>
                      <option value="camel.*" />
                      <option value="quarkus.http.*" />
                    </list>
                  </option>
                </tool>""", JDOMUtil.write(changed));
    }

    public void testTheOptionsEditTheExcludeList() {
        var inspection = new ExclusionsInspection();

        assertInstanceOf(inspection.getOptionsPane().findControl("excludeList"), OptStringList.class);
        // The list that the options panel edits in place
        assertSame(inspection.excludeList, inspection.getOptionController().getOption("excludeList"));
    }

    public void testTheOptionsPanelShows() {
        var inspection = new ExclusionsInspection();

        assertTrue(OptionPaneRenderer.hasSettings(inspection));
        assertNotNull(OptionPaneRenderer.createOptionsPanel(inspection, getTestRootDisposable(), getProject()));
    }

    /**
     * Like Quarkus Tools' {@code MicroProfilePropertiesUnassignedInspection}.
     */
    private static class ExclusionsInspection extends AbstractDelegateInspectionWithExclusions {

        ExclusionsInspection() {
            super("Excluded properties:");
        }
    }

    /**
     * Like Quarkus Tools' {@code MicroProfilePropertiesUnknownInspection}.
     */
    private static class DefaultExclusionsInspection extends AbstractDelegateInspectionWithExclusions {

        DefaultExclusionsInspection() {
            super("Excluded properties:");
            excludeList.add("camel.*");
        }
    }
}
