package com.redhat.devtools.lsp4ij.console.explorer.actions;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.redhat.devtools.lsp4ij.LanguageServerBundle;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerDefinitionSerializer;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
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
//        FileChooserFactory fileChooserFactory = FileChooserFactory.getInstance();
//        FileSaverDescriptor fileSaverDescriptor = new FileSaverDescriptor(
//                LanguageServerBundle.message("action.lsp.console.explorer.export.servers.zip.save.title"), LanguageServerBundle.message("action.lsp.console.explorer.export.servers.zip.save.description"));
//        FileSaverDialog fileSaverDialog = fileChooserFactory.createSaveFileDialog(fileSaverDescriptor, e.getProject());
//        VirtualFileWrapper fileWrapper = fileSaverDialog.save("export.zip");
        printJson();
//        if (fileWrapper != null) {
//            VirtualFile virtualFile = fileWrapper.getVirtualFile(true);
//            if (virtualFile != null) {
//                ApplicationManager.getApplication().runWriteAction(() -> {
//                    try {
//                        virtualFile.setBinaryContent(createZipFromStrings());
//                    } catch (IOException ex) {
//                        LOGGER.warn(ex.getLocalizedMessage(), e);
//                    }
//                });
//            }
//        }
    }

    private void printJson() {
        for (LanguageServerDefinition lsDefinition : languageServerDefinitions) {
            if (lsDefinition instanceof UserDefinedLanguageServerDefinition) {
                System.out.println("Is user defined!");
            }
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(UserDefinedLanguageServerDefinition.class, new LanguageServerDefinitionSerializer())
                    .create();
            String json = gson.toJson(lsDefinition);
            System.out.println(json);
        }
    }

    private byte[] createZipFromStrings() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);

        for (LanguageServerDefinition lsDefinition : languageServerDefinitions) {
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LanguageServerDefinition.class, new LanguageServerDefinitionSerializer())
                    .create();
            String json = gson.toJson(lsDefinition);
            String lsName = lsDefinition.getDisplayName();
            ZipEntry entry = new ZipEntry(lsName + "/" + lsName + ".json");

            zos.putNextEntry(entry);
            zos.write(json.getBytes(StandardCharsets.UTF_8));
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