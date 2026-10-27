package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;

import java.util.Map;

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

        Map<String, Object> parameters =
                LaunchUtils.getDapParameters(options, project);

        assertEquals(expectedPath, parameters.get("program"));
    }

    public void testFilePathMacroUsesSelectedEditorFile() {
        Project project = myFixture.getProject();

        PsiFile psiFile = myFixture.addFileToProject(
                "src/selected.js",
                "console.log('selected');"
        );

        VirtualFile selectedFile = psiFile.getVirtualFile();

        DAPRunConfigurationOptions options = new DAPRunConfigurationOptions();
        options.setLaunchConfiguration("""
                {
                    "program": "$FilePath$"
                }
                """);

        Map<String, Object> parameters =
                LaunchUtils.getDapParameters(options, project, selectedFile);

        assertEquals(
                selectedFile.getPath().replace('\\', '/'),
                parameters.get("program").toString().replace('\\', '/')
        );
    }
}
