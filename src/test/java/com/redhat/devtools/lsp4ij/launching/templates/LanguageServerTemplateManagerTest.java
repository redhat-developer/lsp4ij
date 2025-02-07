package com.redhat.devtools.lsp4ij.launching.templates;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LanguageServerTemplateManagerTest {
    @SuppressWarnings("unused") // This instance call is required for LocalFileSystem.getInstance();
    TestApplicationManager testApplicationManager = TestApplicationManager.getInstance();
    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    LanguageServerTemplateManager templateManager = LanguageServerTemplateManager.getInstance();

    @Test
    public void testExportSingleServer() throws IOException {
        UserDefinedLanguageServerDefinition definition = createUserDefinedLanguageServerDefinition("test");
        runExport(Collections.singletonList(definition), 1);
    }

    @Test
    public void testExportMultipleServers() throws IOException {
        UserDefinedLanguageServerDefinition definition1 = createUserDefinedLanguageServerDefinition("test1");
        UserDefinedLanguageServerDefinition definition2 = createUserDefinedLanguageServerDefinition("test2");
        UserDefinedLanguageServerDefinition definition3 = createUserDefinedLanguageServerDefinition("test3");

        List<LanguageServerDefinition> definitions = List.of(definition1, definition2, definition3);
        runExport(definitions, 3);
    }

    @Test
    public void testImport() {
        File ioFile = new File("src/test/resources/templates/test_template1");
        VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(ioFile);
        assert virtualFile != null;
        LanguageServerTemplate template = null;
        try {
            template = templateManager.importLsTemplate(virtualFile);
        } catch (IOException ex) {
            fail(ex);
        }
        assert template != null;
        assertNotNull(template.getDescription());
        assertNotEquals("", template.getDescription());
        assertEquals("Rust Language Server", template.getName());
        if (SystemInfo.isWindows) {
            assertEquals("rust-analyzer", template.getProgramArgs());
        } else {
            assertEquals("sh -c rust-analyzer", template.getProgramArgs());
        }

        List<ServerMappingSettings> fileTypeMappings = template.getFileTypeMappings();
        assertEquals(2, fileTypeMappings.size());
        assertEquals(1, fileTypeMappings.get(0).getFileNamePatterns().size());
        assertEquals("*.rs", fileTypeMappings.get(0).getFileNamePatterns().get(0));

        assertNotNull(template.getInitializationOptions());
        assertNotEquals("", template.getInitializationOptions());
        assertNotNull(template.getConfiguration());
        assertNotEquals("", template.getConfiguration());
        assertNotNull(template.getClientConfiguration());
        assertNotEquals("", template.getClientConfiguration());
    }

    @Test
    public void testImportNoFiles() {
        File ioFile = new File("src/test/resources/templates/test_template2");
        VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(ioFile);
        assert virtualFile != null;
        assertThrows(IOException.class, () -> templateManager.importLsTemplate(virtualFile));
    }

    private void runExport(List<LanguageServerDefinition> templates, int expectedCount) throws IOException {
        File ioFile = new File("src/test/resources/templates/export.zip");
        assert !ioFile.exists() || ioFile.delete();
        assert ioFile.createNewFile();
        VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(ioFile);

        assert virtualFile != null;
        WriteCommandAction.runWriteCommandAction(null, () -> {
            int count = templateManager.exportLsTemplates(virtualFile, templates);
            assertEquals(expectedCount, count);
            try {
                virtualFile.delete(null);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private UserDefinedLanguageServerDefinition createUserDefinedLanguageServerDefinition(String identifier) {
        return new UserDefinedLanguageServerDefinition(
                identifier,
                null,
                identifier,
                null,
                "cmd",
                new HashMap<>(),
                false,
                null,
                null,
                null,
                null);
    }
}