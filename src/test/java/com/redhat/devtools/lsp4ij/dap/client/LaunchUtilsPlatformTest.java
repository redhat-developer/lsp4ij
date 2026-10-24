package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;

public class LaunchUtilsPlatformTest extends BasePlatformTestCase {

    public void testProjectDirMacroIsExpandedInLaunchParameters() {
        Project project = myFixture.getProject();

        DAPRunConfigurationOptions options = new DAPRunConfigurationOptions();
        options.setLaunchConfiguration("""
                {
                    "program": "$PROJECT_DIR$/src/main.js"
                }
                """);

        String expectedPath = project.getBasePath() + "/src/main.js";

        var parameters = LaunchUtils.getDapParameters(options, project);

        assertEquals(expectedPath, parameters.get("program"));
    }
}