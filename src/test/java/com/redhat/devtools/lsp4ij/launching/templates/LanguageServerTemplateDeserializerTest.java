/*******************************************************************************
 * Copyright (c) 2024 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *  Mitja Leino <mitja.leino@hotmail.com> - Initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.util.SystemInfo;
import com.redhat.devtools.lsp4ij.server.definition.launching.UserDefinedTransport;
import com.redhat.devtools.lsp4ij.templates.ServerMappingSettings;
import org.junit.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LanguageServerTemplateDeserializerTest {
    @Test
    public void basicDeserializerTest() {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(LanguageServerTemplate.class, new LanguageServerTemplateDeserializer());
        Gson gson = builder.create();

        String json = """
            { "name": "Java LS",
                "programArgs": {
                    "windows": "./start.bat",
                    "default": "./start.sh"
                },
                "languageMappings": [
                    {
                        "language": "JAVA",
                        "languageId": "java"
                    }
                ],
                "fileTypeMappings": [
                    {
                    "fileType": {
                        "name": "JAVA",
                        "patterns": [
                            "*.java",
                            "*.jv"
                        ]
                    },
                    "languageId": "java"
                    }
                ]
            }""".stripIndent();

        LanguageServerTemplate template = gson.fromJson(json, LanguageServerTemplate.class);
        if (SystemInfo.isWindows) {
            assertEquals("./start.bat", template.getProgramArgs());
        } else {
            assertEquals("./start.sh", template.getProgramArgs());
        }
        assertEquals("Java LS", template.getName());

        ServerMappingSettings langMapping = template.getLanguageMappings().get(0);
        assertEquals("java", langMapping.getLanguageId());
        assertEquals("JAVA", langMapping.getLanguage());

        List<ServerMappingSettings> fileTypeMappings = template.getFileTypeMappings();
        ServerMappingSettings ftmPattern = fileTypeMappings.get(0);
        assertEquals("java", langMapping.getLanguageId());
        assertEquals("*.java", ftmPattern.getFileNamePatterns().get(0));
        assertEquals("*.jv", ftmPattern.getFileNamePatterns().get(1));

        ServerMappingSettings ftm = fileTypeMappings.get(1);
        assertEquals("java", ftm.getLanguageId());
        assertEquals("JAVA", ftm.getFileType());
    }

    @Test
    public void templateWithoutProgramArgsHasNoCommand() {
        // a language server which runs already, which LSP4IJ doesn't start
        LanguageServerTemplate template = createGson().fromJson("""
            { "name": "Running LS",
                "socket": { "mode": "attach", "port": 6005 }
            }""", LanguageServerTemplate.class);

        assertNull(template.getProgramArgs());
        assertEquals(new UserDefinedTransport(UserDefinedTransport.Mode.ATTACH, null, 6005), template.getTransport());
    }

    @Test
    public void urlNullGivesNoLink() {
        assertNull(url("\"url\": null"));
        // without url, the page of the template in the docs
        assertEquals("https://github.com/redhat-developer/lsp4ij/tree/main/docs/user-defined-ls/my-ls.md", url(null));
        assertEquals("https://example.com/my-ls", url("\"url\": \"https://example.com/my-ls\""));
    }

    @Test
    public void aTemplateIsPromotableUnlessItSaysPromotableFalse() {
        assertFalse(promotable("\"promotable\": false"), "a template with \"promotable\": false");
        assertTrue(promotable("\"promotable\": true"), "a template with \"promotable\": true");
        // without the key, as the templates before it
        assertTrue(promotable(null), "a template without \"promotable\"");
        // a value which isn't a boolean is ignored
        assertTrue(promotable("\"promotable\": \"no\""), "a template with \"promotable\": \"no\"");
    }

    private static boolean promotable(String promotableProperty) {
        String json = """
            { "id": "my-ls",
                "name": "My LS",
                %s"programArgs": {
                    "default": "./start.sh"
                }
            }""".formatted(promotableProperty != null ? promotableProperty + ",\n" : "");
        return createGson().fromJson(json, LanguageServerTemplate.class).isPromotable();
    }

    private static String url(String urlProperty) {
        String json = """
            { "id": "my-ls",
                "name": "My LS",
                %s"programArgs": {
                    "default": "./start.sh"
                }
            }""".formatted(urlProperty != null ? urlProperty + ",\n" : "");
        return createGson().fromJson(json, LanguageServerTemplate.class).getUrl();
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(LanguageServerTemplate.class, new LanguageServerTemplateDeserializer())
                .create();
    }
}
