package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.*;
import com.redhat.devtools.lsp4ij.launching.ServerMappingSettings;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LanguageServerTemplateDeserializer implements JsonDeserializer<LanguageServerTemplate> {
    @Override
    public LanguageServerTemplate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        LanguageServerTemplate languageServerTemplate = new LanguageServerTemplate();

        languageServerTemplate.setName(jsonObject.get("name").getAsString());
        JsonObject programArgs = jsonObject.get("programArgs").getAsJsonObject();
        if (programArgs != null) {
            languageServerTemplate.setDefaultProgramArg(programArgs.get("default").getAsString());
        }
        JsonArray fileTypeMappings = jsonObject.getAsJsonArray("fileTypeMappings");
        if (fileTypeMappings != null && !fileTypeMappings.isEmpty()) {
            for (JsonElement ftm : fileTypeMappings) {
                String languageId = ftm.getAsJsonObject().get("languageId").getAsString();
                List<String> patterns = new ArrayList<>();
                JsonObject fileType = ftm.getAsJsonObject().getAsJsonObject("fileType");
                JsonArray patternArray = fileType.getAsJsonArray("patterns");
                for (JsonElement pattern : patternArray) {
                    patterns.add(pattern.getAsString());
                }
                if (!patterns.isEmpty()) {
                    languageServerTemplate.addFileTypeMapping(ServerMappingSettings.createFileNamePatternsMappingSettings(patterns, languageId));
                }

                String language = fileType.get("name").getAsString();
                if (language != null) {
                    languageServerTemplate.addFileTypeMapping(ServerMappingSettings.createFileTypeMappingSettings(language, languageId));
                }
            }
        }
        JsonArray languageMappings = jsonObject.getAsJsonArray("languageMappings");
        if (languageMappings != null && !languageMappings.isEmpty()) {
            for (JsonElement language : languageMappings) {
                String lang = language.getAsJsonObject().get("language").getAsString();
                String languageId = language.getAsJsonObject().get("languageId").getAsString();
                languageServerTemplate.addLanguageMapping(ServerMappingSettings.createLanguageMappingSettings(lang, languageId));
            }
        }

        return languageServerTemplate;
    }
}