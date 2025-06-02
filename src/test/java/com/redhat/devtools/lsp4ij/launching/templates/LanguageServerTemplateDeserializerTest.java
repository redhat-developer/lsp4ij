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
}