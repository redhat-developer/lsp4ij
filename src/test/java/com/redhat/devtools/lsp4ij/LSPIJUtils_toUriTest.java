package com.redhat.devtools.lsp4ij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class LSPIJUtils_toUriTest {

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void testToUriWithWslPath() {
        // Given: Create a File object from a UNC path string
        // Note: File object may normalize the path, so we test with the actual behavior
        String wslPath = "\\\\wsl$\\Ubuntu\\home\\user\\project\\file.txt";
        File wslFile = new File(wslPath);

        // When
        URI resultUri = LSPIJUtils.toUri(wslFile);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        
        // The URI should either:
        // 1. Have authority="wsl$" and path="/Ubuntu/home/user/project/file.txt" (correct UNC handling)
        // 2. Or at minimum, not produce the old broken format with 4 slashes
        String uriString = resultUri.toString();
        assertNotNull(uriString);
        
        // Verify it doesn't have the broken 4-slash format
        if (uriString.contains("file:////")) {
            fail("URI should not contain file://// (broken UNC format): " + uriString);
        }
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

    @Test
    void testToUriWithSpaces() {
        // Given
        File fileWithSpaces;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            fileWithSpaces = new File("C:\\Users\\user name\\project name\\file.txt");
        } else {
            fileWithSpaces = new File("/home/user name/project name/file.txt");
        }

        // When
        URI resultUri = LSPIJUtils.toUri(fileWithSpaces);

        // Then
        assertNotNull(resultUri);
        assertEquals("file", resultUri.getScheme());
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            assertEquals("/C:/Users/user%20name/project%20name/file.txt", resultUri.getRawPath());
        } else {
            assertEquals("/home/user%20name/project%20name/file.txt", resultUri.getRawPath());
        }
    }
}
