package com.redhat.devtools.lsp4ij.settings.jsonSchema;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;

public class LSPSettingsJsonSchemaProviderFactory implements JsonSchemaProviderFactory {
    @NotNull
    @Override
    public List<JsonSchemaFileProvider> getProviders(@NotNull Project project) {
        List<JsonSchemaFileProvider> providers = new LinkedList<>();
        providers.add(new TypeScriptLanguageServerConfigurationJsonSchemaFileProvider());
        return providers;
    }

    private static abstract class AbstractIlluminatedCloudJsonSchemaFileProvider implements JsonSchemaFileProvider {
        private final String jsonSchemaPath;
        private final String jsonFilename;
        private VirtualFile jsonSchemaFile = null;

        protected AbstractIlluminatedCloudJsonSchemaFileProvider(@NotNull String jsonSchemaPath, @NotNull String jsonFilename) {
            this.jsonSchemaPath = jsonSchemaPath;
            this.jsonFilename = jsonFilename;
        }

        @Nullable
        @Override
        public final VirtualFile getSchemaFile() {
            if (jsonSchemaFile == null) {
                URL jsonSchemaUrl = getClass().getResource(jsonSchemaPath);
                String jsonSchemaFileUrl = jsonSchemaUrl != null ? VfsUtil.convertFromUrl(jsonSchemaUrl) : null;
                jsonSchemaFile = jsonSchemaFileUrl != null ? VirtualFileManager.getInstance().findFileByUrl(jsonSchemaFileUrl) : null;
                // Make sure that the IDE is using the absolute latest version of the JSON schema
                if (jsonSchemaFile != null) {
                    jsonSchemaFile.refresh(true, false);
                }
            }
            return jsonSchemaFile;
        }

        @Override
        public boolean isAvailable(@NotNull VirtualFile file) {
            return StringUtil.equalsIgnoreCase(jsonFilename, file.getName());
        }

        @NotNull
        @Override
        public final String getName() {
            return jsonFilename;
        }

        @NotNull
        @Override
        public final SchemaType getSchemaType() {
            return SchemaType.schema;
        }

        @Override
        public final JsonSchemaVersion getSchemaVersion() {
            return JsonSchemaVersion.SCHEMA_7;
        }

        @NotNull
        @Override
        public final String getPresentableName() {
            return getName();
        }
    }

    public static class TypeScriptLanguageServerConfigurationJsonSchemaFileProvider extends AbstractIlluminatedCloudJsonSchemaFileProvider {
        private static final String TYPESCRIPT_LANGUAGE_SERVER_SETTINGS_SCHEMA_JSON_PATH = "/templates/typescript-language-server/settings.schema.json";
        public static final String TYPESCRIPT_LANGUAGE_SERVER_SETTINGS_JSON_FILENAME = "typescript-language-server-settings.json";

        private TypeScriptLanguageServerConfigurationJsonSchemaFileProvider() {
            super(TYPESCRIPT_LANGUAGE_SERVER_SETTINGS_SCHEMA_JSON_PATH, TYPESCRIPT_LANGUAGE_SERVER_SETTINGS_JSON_FILENAME);
        }
    }
}
