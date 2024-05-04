package com.redhat.devtools.lsp4ij.console.explorer.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportServerAction extends AnAction {
    private final List<LanguageServerDefinition> languageServerDefinitions;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExportServerAction.class);

    public ExportServerAction(List<LanguageServerDefinition> languageServerDefinitions) {
        this.languageServerDefinitions = languageServerDefinitions;
        if (this.languageServerDefinitions.size() == 1) {
            getTemplatePresentation().setText(LanguageServerBundle.message("action.lsp.console.explorer.export.server.text"));
            getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.console.explorer.export.server.description"));
        } else {
            getTemplatePresentation().setText(LanguageServerBundle.message("action.lsp.console.explorer.export.servers.text"));
            getTemplatePresentation().setDescription(LanguageServerBundle.message("action.lsp.console.explorer.export.servers.description"));
        }
        getTemplatePresentation().setIcon(AllIcons.ToolbarDecorator.Export);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        FileChooserFactory fileChooserFactory = FileChooserFactory.getInstance();
        FileSaverDescriptor fileSaverDescriptor = new FileSaverDescriptor("Save LS Definition", "Choose location for the LS definition export");
        FileSaverDialog fileSaverDialog = fileChooserFactory.createSaveFileDialog(fileSaverDescriptor, e.getProject());
        VirtualFileWrapper fileWrapper = fileSaverDialog.save("export.zip");

        if (fileWrapper != null) {
            VirtualFile virtualFile = fileWrapper.getVirtualFile(true);
            if (virtualFile != null) {
                ApplicationManager.getApplication().runWriteAction(() -> {
                    try {
                        virtualFile.setBinaryContent(createZipFromStrings());
                    } catch (IOException ex) {
                        LOGGER.warn(ex.getLocalizedMessage(), e);
                    }
                });
            }
        }
    }

    private byte[] createZipFromStrings() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (LanguageServerDefinition lsDefinition : languageServerDefinitions) {
            ZipEntry entry = new ZipEntry(lsDefinition.getDisplayName() + ".json");
            zos.putNextEntry(entry);
            zos.write("Hello".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        zos.close();
        return baos.toByteArray();
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}