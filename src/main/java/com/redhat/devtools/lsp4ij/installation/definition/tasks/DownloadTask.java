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
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.system.CpuArch;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerContext;
import com.redhat.devtools.lsp4ij.installation.definition.InstallerTask;
import com.redhat.devtools.lsp4ij.installation.definition.SecretRedactor;
import com.redhat.devtools.lsp4ij.installation.definition.ServerInstallerDescriptor;
import com.redhat.devtools.lsp4ij.installation.download.AssetFetcher;
import com.redhat.devtools.lsp4ij.installation.download.DownloadUtils;
import com.redhat.devtools.lsp4ij.launching.templates.LanguageServerTemplate;
import com.redhat.devtools.lsp4ij.server.definition.launching.CommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * <pre>
 *     "download": {
 *       "name": "Download Camel Language Server",
 *       "url": "https://repo1.maven.org/maven2/com/github/camel-tooling/camel-lsp-server/1.31.0/camel-lsp-server-1.31.0.jar",
 *       "output": {
 *         "dir": "$USER_HOME$/.lsp4ij/lsp/camel-lsp-server"
 *       },
 *       "onSuccess": {
 *         "configureServer": {
 *           "name": "Configure Camel Language Server command",
 *           "command": "java -jar \"${output.dir}/${output.file.name}\"",
 *           "update": true
 *         }
 *       }
 *     }
 * </pre>
 */
public class DownloadTask extends InstallerTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadTask.class);

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
        // The query and the fragment of the URL, as a token, aren't part of the name: a '?' can't be in a file name on
        // Windows, and an archive is recognized by the extension of its name (#2206)
        int end = url.length();
        int query = url.indexOf('?');
        if (query >= 0) {
            end = query;
        }
        int fragment = url.indexOf('#');
        if (fragment >= 0 && fragment < end) {
            end = fragment;
        }
        String path = url.substring(0, end);
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) return "";
        return path.substring(slash + 1);
    }

    @Override
    public boolean run(@NotNull InstallerContext context) {
        String installer = getServerInstallerDeclaration().getName();
        long lookupStart = System.nanoTime();
        DownloadUrl found = getDownloadUrl(context);
        long lookupMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lookupStart);
        String downloadUrl = found.url();
        try {
            if (downloadUrl == null) {
                LOGGER.warn("Installer '{}': no download URL for the OS {} and the architecture {}",
                        installer, LanguageServerTemplate.OS_KEY, CpuArch.CURRENT.name().toLowerCase());
                // language=html
                String htmlError = """
                        <p>Unable to retrieve the download URL for your platform <code>%s</code> and architecture <code>%s</code>.</p>
                        <p>You must define a valid <a href="https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServerTemplate.md#unique-url">url</a>
                        or <a href="https://github.com/redhat-developer/lsp4ij/blob/main/docs/UserDefinedLanguageServerTemplate.md#github-asset-download">github.asset</a>
                        in your <code>installer.json</code>, matching your OS and architecture.</p>
                        """.formatted(LanguageServerTemplate.OS_KEY, CpuArch.CURRENT.name().toLowerCase());
                printLoggedError(htmlError, context);
                context.addErrorMessage(htmlError);
                return false;
            }
            context.print("> Downloading asset: " + downloadUrl);

            // Extract filename from URL
            String originFileName = extractFileName(downloadUrl);
            if (originFileName.isEmpty()) {
                printLoggedError("Cannot extract file name from URL.", context);
                LOGGER.warn("Installer '{}': cannot extract the file name from the download URL {}", installer,
                        SecretRedactor.redact(downloadUrl));
                return false;
            }

            Path tempDir = Files.createTempDirectory("lsp4ij-downloads");
            Path downloadedAsset = tempDir.resolve(originFileName);

            // Download with progress
            long downloadStart = System.nanoTime();
            DownloadUtils.download(downloadUrl, downloadedAsset, context.getProgressIndicator());
            LOGGER.info("Installer '{}' downloaded {} ({} bytes) in {} ms; its URL comes from {} ({} ms)",
                    installer,
                    // A URL can hold a secret, as a token in its query or a password in its user info
                    SecretRedactor.redact(downloadUrl),
                    Files.size(downloadedAsset),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - downloadStart),
                    found.source(),
                    lookupMillis);
            context.print("\nDownloaded asset done in " + downloadedAsset.toString());

            // Create output directory where downloaded file must be extracted
            String dir = getDir();
            // Resolve ${key} variables before IntelliJ macros so that properties
            // like ${serverDist} defined in installer.json can be used in output.dir
            dir = context.resolveValues(dir);
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
                    if (outputInfo != null && outputInfo.stripRootFolder()) {
                        // The content of the root folder of the archive, as perllsp-0.17.0-x86_64-apple-darwin/, goes
                        // into the dir itself, so the server is found at the same place from one version to the next
                        moveContentInto(decompressedDir, outputDir);
                    } else {
                        // The extracted downloaded asset is a directory, adjust the dir where the server will be hosted
                        var decompressFolderName = decompressedDir.getName(decompressedDir.getNameCount() - 1);
                        dir = dir + "/" + decompressFolderName;
                        outputDir = outputDir.resolve(decompressFolderName);
                    }
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
                    printLoggedError(htmlError, context);
                    context.addErrorMessage(htmlError);
                    LOGGER.warn("Installer '{}': the file {} of output.file.name isn't in {}", installer,
                            outputFileName, outputDir);
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
            printLoggedError("Download server failed: " + ExceptionUtil.getThrowableText(e), context);
            // The text of the exception, with its stack, can have the URL too
            LOGGER.warn("Installer '{}': the download of {} failed\n{}", installer, SecretRedactor.redact(downloadUrl),
                    SecretRedactor.redact(ExceptionUtil.getThrowableText(e)));
            return false;
        }
    }

    /**
     * Prints the given error, which this task logs itself, with what it downloaded: the IDE's log gets the printed
     * error at DEBUG only, so that it doesn't have it twice.
     *
     * @param error   the error.
     * @param context the installer context.
     */
    private static void printLoggedError(@NotNull String error, @NotNull InstallerContext context) {
        context.print(error, ConsoleViewContentType.ERROR_OUTPUT, Level.FINE);
    }

    /**
     * Moves the content of the given folder into the given parent folder, replacing what is there under the same names,
     * as the files of an earlier install, and deletes the folder.
     */
    private static void moveContentInto(@NotNull Path folder, @NotNull Path parent) throws IOException {
        // Renamed first, since it can hold a file or a folder of its own name
        Path renamed = Files.move(folder, folder.resolveSibling(folder.getFileName() + "-" + UUID.randomUUID()));
        try (Stream<Path> children = Files.list(renamed)) {
            for (Path child : children.toList()) {
                Path target = parent.resolve(child.getFileName().toString());
                FileUtil.delete(target);
                Files.move(child, target);
            }
        }
        Files.delete(renamed);
    }

    private String getDir() {
        String dir = outputInfo != null ? outputInfo.dir() : null;
        if (dir == null) {
            dir = "$USER_HOME$/.lsp4ij/" + UUID.randomUUID();
        }
        return dir;
    }

    private @NotNull DownloadUrl getDownloadUrl(@NotNull InstallerContext context) {
        if (assetFetcherInfo == null) {
            return new DownloadUrl(downloadUrl, "its 'url'");
        }
        AssetFetcher assetFetcher = assetFetcherInfo.assetFetcher();
        String url = assetFetcher.getDownloadUrl(assetFetcherInfo.releaseMatcher(),
                assetFetcherInfo.assetMatcher(),
                context);
        if (url != null) {
            return new DownloadUrl(url, assetFetcher.getSource());
        }
        context.print("Cannot retrieve url from 'github/asset', 'maven', fallback to the 'url' JSON property");
        LOGGER.info("Installer '{}': no download URL from {}; falling back to the 'url' of the download: {}",
                getServerInstallerDeclaration().getName(),
                assetFetcher.getSource(),
                downloadUrl != null ? SecretRedactor.redact(downloadUrl) : "none");
        return new DownloadUrl(downloadUrl, "its 'url', since its 'github' or 'maven' gave none");
    }

    /**
     * The URL of a download, and where it comes from, for the log.
     *
     * @param url    the URL of the download, or null if there is none.
     * @param source where the URL comes from.
     */
    private record DownloadUrl(@Nullable String url, @NotNull String source) {
    }

    public record AssetFetcherInfo(@NotNull AssetFetcher assetFetcher,
                                   @NotNull Function<JsonObject, Boolean> releaseMatcher,
                                   @NotNull Function<JsonObject, Boolean> assetMatcher) {
    }

    public record OutputInfo(@Nullable String dir,
                             @Nullable String fileName,
                             boolean executable,
                             boolean stripRootFolder) {
    }

}
