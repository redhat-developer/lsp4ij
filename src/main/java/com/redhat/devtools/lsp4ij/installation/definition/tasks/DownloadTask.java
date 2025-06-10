/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.definition.tasks;

import com.google.gson.JsonObject;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.system.CpuArch;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.download.AssetFetcher;
import com.redhat.devtools.lsp4ij.installation.download.DownloadUtils;
import com.redhat.devtools.lsp4ij.installation.download.GitHubAssetFetcher;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.function.Function;

/**
 * <pre>
 *     "download": {
 *       "name": "Download sdl-lsp",
 *       "url": "https://oss.sonatype.org/content/repositories/snapshots/io/smartdatalake/sdl-lsp/1.0-SNAPSHOT/sdl-lsp-1.0-20250503.111518-23-jar-with-dependencies.jar",
 *       "output": {
 *         "dir": "$USER_HOME$/.lsp4ij/lsp/sdl-lsp"
 *       },
 *       "onSuccess": {
 *         "configureServer": {
 *           "name": "Configure sdl-lsp server command",
 *           "command": "java -jar ${output.dir}/${output.file.name}",
 *           "update": true
 *         }
 *       }
 *     }
 * </pre>
 */
public class DownloadTask extends InstallerTask {

    private final @Nullable String downloadUrl;
    private final @Nullable DownloadTask.AssetFetcherInfo assetFetcherInfo;
    private final @Nullable DownloadTask.@Nullable OutputInfo outputInfo;

    public DownloadTask(@Nullable String id,
                        @Nullable String name,
                        @Nullable InstallerTask onFail,
                        @Nullable InstallerTask onSuccess,
                        @Nullable String downloadUrl,
                        @Nullable DownloadTask.AssetFetcherInfo assetFetcherInfo,
                        @Nullable OutputInfo outputInfo,
                        @NotNull ServerInstallerDescriptor serverInstallerDeclaration) {
        super(id, name, onFail, onSuccess, serverInstallerDeclaration);
        this.downloadUrl = downloadUrl;
        this.assetFetcherInfo = assetFetcherInfo;
        this.outputInfo = outputInfo;
    }

    private static String extractFileName(String url) {
        int slash = url.lastIndexOf('/');
        if (slash < 0 || slash == url.length() - 1) return "";
        return url.substring(slash + 1);
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        String downloadUrl = getDownloadUrl(context);
        try {
            if (downloadUrl == null) {
                // language=html
                String htmlError = """
                        <p>Unable to retrieve the download URL for your platform <code>%s</code> and architecture <code>%s</code>.</p>
                        <p>You must define a valid <a href="https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServerTemplate.md#unique-url">url</a> 
                        or <a href="https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServerTemplate.md#github-asset-download">github.asset</a> 
                        in your <code>installer.json</code>, matching your OS and architecture.</p>
                        """.formatted(LanguageServerTemplate.OS_KEY, CpuArch.CURRENT.name().toLowerCase());
                context.printError(htmlError, true);
                return false;
            }
            context.print("> Downloading asset: " + downloadUrl);

            // Extract filename from URL
            String originFileName = extractFileName(downloadUrl);
            if (originFileName.isEmpty()) {
                context.printError("Cannot extract file name from URL.");
                return false;
            }

            Path tempDir = Files.createTempDirectory("lsp4ij-downloads");
            Path downloadedAsset = tempDir.resolve(originFileName);

            // Download with progress
            DownloadUtils.download(downloadUrl, downloadedAsset, context.getProgressIndicator());
            context.print("\nDownloaded asset done in " + downloadedAsset.toString());

            // Create output directory where downloaded file must be extracted
            String dir = getDir();
            var project = context.getProject();
            String resolvedDir = CommandUtils.resolveCommandLine(dir, project);
            Path outputDir = Paths.get(resolvedDir);
            Files.createDirectories(outputDir);

            String outputFileName = outputInfo != null ? outputInfo.fileName() : null;
            Path decompressedDir = null;
            boolean extracted = false;
            var decompressor = DownloadUtils.getDecompressor(downloadedAsset);
            if (decompressor != null) {
                context.print("> Extracting asset in " + outputDir.toString());
                decompressedDir = decompressor.decompress(downloadedAsset, outputDir);
                Files.deleteIfExists(downloadedAsset);
                context.print("Extracted asset done");
                extracted = true;
            }

            if (extracted) {
                // Downloaded asset is a zip, tar, etc file
                if (decompressedDir != null && Files.isDirectory(decompressedDir)) {
                    // The extracted downloaded asset is a directory, adjust the dir where the server will be hosted
                    var decompressFolderName = decompressedDir.getName(decompressedDir.getNameCount() - 1);
                    dir = dir + "/" + decompressFolderName;
                    outputDir = outputDir.resolve(decompressFolderName);
                }
            } else {
                // Downloaded asset is a simple file (ex: *.jar file)
                // Copy downloaded file in the output dir
                File downloadedFile = downloadedAsset.toFile();
                if (outputFileName == null) {
                    // Here there is no declaration of file/name:
                    // "file" :{
                    //    "name": "foo.java"
                    // }
                    // the file name becomes the downloaded file name
                    outputFileName = downloadedFile.getName();
                }
                Path outputFile = outputDir.resolve(outputFileName);
                // Delete old server file which has been previously installed.
                Files.deleteIfExists(outputFile);

                context.print("> Copy downloaded asset '" + downloadedFile.getName() + "' in " + outputFile.toString());
                FileUtil.copy(downloadedFile, outputFile.toFile());
                Files.deleteIfExists(downloadedAsset);
            }
            // Update ${output.dir} property
            context.putProperty("output.dir", dir);

            if (outputFileName != null) {
                // Update ${output.file.name} property
                context.putProperty("output.file.name", outputFileName);

                Path exeFile = outputDir.resolve(outputFileName);
                if (!Files.exists(exeFile)) {
                    // language=HTML
                    String htmlError = """
                            <p>The file name <code>%s</code> specified in <a href='https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServerTemplate.md#output-customization'><code>output.file.name</code></a>
                             for your platform <code>%s</code> and architecture <code>%s</code> 
                            was not found in the downloaded archive at <code>%s</code>.</p>
                            <p>Please update the <code>output.file.name</code> section of your <code>installer.json</code> to correctly match your OS and architecture.</p>
                            """.formatted(outputFileName, LanguageServerTemplate.OS_KEY, CpuArch.CURRENT.name().toLowerCase(), outputDir.toString());
                    context.printError(htmlError, true);
                    return false;
                }
                if (outputInfo != null && outputInfo.executable()) {
                    // Set executable permission
                    context.print("> Setting executable permission for: " + exeFile.toString());
                    FileUtil.setExecutable(exeFile.toFile());

                }
            }
            context.addInfoMessage("Server has been downloaded correctly in '" + outputDir.toString() + "'");
            return true;
        } catch (ProcessCanceledException e) {
            // Progress indicator was cancelled
            return false;
        } catch (Exception e) {
            context.addErrorMessage("Download server failed: " + e.getMessage());
            context.printError("Download server failed: ", e);
            return false;
        }
    }

    private String getDir() {
        String dir = outputInfo != null ? outputInfo.dir() : null;
        if (dir == null) {
            dir = "$USER_HOME$/.lsp4ij/" + UUID.randomUUID();
        }
        return dir;
    }

    private String getDownloadUrl(@NotNull InstallerContext context) {
        String downloadUrl = null;
        if (assetFetcherInfo != null) {
            downloadUrl = assetFetcherInfo.assetFetcher().getDownloadUrl(assetFetcherInfo.releaseMatcher(),
                    assetFetcherInfo.assetMatcher(),
                    context);
            if (downloadUrl == null) {
                context.print("Cannot retrieve url from 'github/asset', 'maven', fallback to the 'url' JSON property");
            }
        }
        return downloadUrl != null ? downloadUrl : this.downloadUrl;
    }

    public record AssetFetcherInfo(@NotNull AssetFetcher assetFetcher,
                                   @NotNull Function<JsonObject, Boolean> releaseMatcher,
                                   @NotNull Function<JsonObject, Boolean> assetMatcher) {
    }

    public record OutputInfo(@Nullable String dir,
                             @Nullable String fileName,
                             boolean executable) {
    }

}
