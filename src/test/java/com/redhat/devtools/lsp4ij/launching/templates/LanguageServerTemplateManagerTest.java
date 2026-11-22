package com.redhat.devtools.lsp4ij.launching.templates;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestApplicationManager;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import com.redhat.devtools.lsp4ij.server.definition.LanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedLanguageServerDefinition;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
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
            template = templateManager.importServerTemplate(virtualFile);
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
        assertThrows(IOException.class, () -> templateManager.importServerTemplate(virtualFile));
    }

    @Test
    public void testImportWithoutProgramArgs() throws IOException {
        // a language server which runs already, which LSP4IJ doesn't start, needs no command
        File ioFile = new File("src/test/resources/templates/test_template3");
        VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(ioFile);
        assert virtualFile != null;
        LanguageServerTemplate template = templateManager.importServerTemplate(virtualFile);
        assert template != null;
        assertEquals("Running Language Server", template.getName());
        assertNull(template.getProgramArgs());
        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, "127.0.0.1", 6005), template.getTransport());
    }

    @Test
    public void testImportOfAnInvalidTemplateFailsWithAnIOException() {
        // fileTypeMappings is an object instead of an array
        File ioFile = new File("src/test/resources/templates/test_template4");
        VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(ioFile);
        assert virtualFile != null;
        IOException error = assertThrows(IOException.class, () -> templateManager.importServerTemplate(virtualFile));
        assertTrue(error.getMessage().startsWith("template.json of test_template4 is invalid"), error.getMessage());
    }

    @Test
    public void testImportOfAnEmptyTemplateFailsWithAnIOException() {
        // template.json is an empty file
        File ioFile = new File("src/test/resources/templates/test_template5");
        VirtualFile virtualFile = localFileSystem.refreshAndFindFileByIoFile(ioFile);
        assert virtualFile != null;
        assertThrows(IOException.class, () -> templateManager.importServerTemplate(virtualFile));
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
                null,
                "cmd",
                null,
                new HashMap<>(),
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}