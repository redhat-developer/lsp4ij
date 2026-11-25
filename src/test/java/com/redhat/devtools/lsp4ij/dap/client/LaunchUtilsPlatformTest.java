package com.redhat.devtools.lsp4ij.dap.client;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.redhat.devtools.lsp4ij.dap.configurations.DAPRunConfigurationOptions;

import java.nio.file.Files;
import java.nio.file.Path;
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

    public void testWorkspaceFolderIsTheProjectDirectoryWithoutAWorkingDirectory() {
        Project project = myFixture.getProject();
        VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
        assertNotNull(projectDir);

        DAPRunConfigurationOptions options = new DAPRunConfigurationOptions();
        options.setFile("/project/sub/test.js");
        options.setLaunchConfiguration("""
                {
                    "cwd": "${workspaceFolder}"
                }
                """);

        // as when a configuration created from a file stored the project's directory as its working directory
        assertEquals(projectDir.getPath(), LaunchUtils.getDapParameters(options, project).get("cwd"));

        options.setWorkingDirectory("/working/directory");

        assertEquals("/working/directory", LaunchUtils.getDapParameters(options, project).get("cwd"));
    }

    public void testAProjectDirectoryWhichIsNotOnDiskAndTheDefaultProjectGiveNoWorkingDirectory() {
        VirtualFile projectDir = ProjectUtil.guessProjectDir(myFixture.getProject());
        assertNotNull(projectDir);
        assertFalse("the light project of the tests has its directory in memory, not on disk: " + projectDir,
                Files.isDirectory(Path.of(projectDir.getPath())));
        var defaultProject = ProjectManager.getInstance().getDefaultProject();

        // so that the Debug Adapter still starts, in the IDE's working directory, as before
        assertNull(LaunchUtils.getWorkingDirectory("", null, myFixture.getProject()));
        assertNull(LaunchUtils.getWorkingDirectory(null, null, defaultProject));
        assertNull(LaunchUtils.getWorkspaceFolder(null, defaultProject));
    }
}
