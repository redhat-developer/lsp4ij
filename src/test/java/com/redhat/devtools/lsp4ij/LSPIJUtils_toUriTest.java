package com.redhat.devtools.lsp4ij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class LSPIJUtils_toUriTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testToUriWithWslPath() {
        // Given
        String wslPath = "\\\\wsl$\\Ubuntu\\home\\user\\project\\file.txt";
        File wslFile = new File(wslPath);

        // When
        URI resultUri = LSPIJUtils.toUri(wslFile);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        assertEquals("wsl$", resultUri.getAuthority());
        assertEquals("/Ubuntu/home/user/project/file.txt", resultUri.getPath());
        assertEquals("file://wsl$/Ubuntu/home/user/project/file.txt", resultUri.toString());
    }

    @Test
    void testToUriWithLocalPath() {
        // Given
        File localFile = new File("/home/user/project/file.txt");
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            localFile = new File("C:\\Users\\user\\project\\file.txt");
        }


        // When
        URI resultUri = LSPIJUtils.toUri(localFile);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            assertEquals("/C:/Users/user/project/file.txt", resultUri.getPath());
        } else {
            assertEquals("/home/user/project/file.txt", resultUri.getPath());
        }
    }
}
