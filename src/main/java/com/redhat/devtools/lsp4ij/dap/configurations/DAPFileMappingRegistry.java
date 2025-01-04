package com.redhat.devtools.lsp4ij.dap.configurations;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DAPFileMappingRegistry {

    public static DAPFileMappingRegistry getInstance(@NotNull Project project) {
        return project.getService(DAPFileMappingRegistry.class);
    }

    private final @NotNull Project project;

    public DAPFileMappingRegistry(@NotNull Project project) {
        this.project = project;
    }

    public boolean isSupported(@NotNull VirtualFile file) {
        List<RunConfiguration> all = RunManager.getInstance(project).getAllConfigurationsList();
        for (var runConfiguration : all) {
            if (runConfiguration instanceof DAPRunConfiguration dapConfig) {
                String program= dapConfig.getProgram();
                int index = program.lastIndexOf('.');
                if (index != -1) {
                    String fileExtension = program.substring(index+1, program.length());
                    if (file.getExtension().equals(fileExtension)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
