package com.redhat.devtools.lsp4ij.launching.templates;

import com.google.gson.*;

import java.lang.reflect.Type;

public class LanguageServerTemplateDeserializer implements JsonDeserializer<LanguageServerTemplate> {
    @Override
    public LanguageServerTemplate deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        LanguageServerTemplate languageServerTemplate = new LanguageServerTemplate();

        languageServerTemplate.setName(jsonObject.get("name").getAsString());
        languageServerTemplate.setDefaultProgramArg(jsonObject.get("commandLine").getAsString());
        // jsonObject.get("programArgs").getAsJsonObject().get("default").getAsString()

        return languageServerTemplate;
    }
}