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
package com.redhat.devtools.lsp4ij.installation.download;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.Decompressor;
import com.intellij.util.io.HttpRequests;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Utility class for downloading and decompressing server archives.
 */
public class DownloadUtils {

    /**
     * Downloads a file from the specified URL to the given local path.
     *
     * @param downloadUrl the URL to download from (must be a valid HTTP or HTTPS URL).
     * @param downloadedFile the target path where the file will be saved.
     * @param progressIndicator an optional progress indicator to display download progress.
     * @throws IOException if the download fails or the file cannot be written.
     */
    public static void download(@NotNull String downloadUrl,
                                @NotNull Path downloadedFile,
                                @Nullable ProgressIndicator progressIndicator) throws IOException {
        HttpRequests.request(downloadUrl)
                .saveToFile(downloadedFile, progressIndicator, true);
    }

    @FunctionalInterface
    public interface DecompressSupport {
        /**
         * Decompresses the specified file into the given directory.
         *
         * @param filePath the path to the compressed file.
         * @param targetDir the directory where the contents will be extracted.
         * @return the root directory of the extracted content, or {@code null} if there is no single root.
         * @throws IOException if decompression fails.
         */
        @Nullable
        Path decompress(@NotNull Path filePath, @NotNull Path targetDir) throws IOException;
    }

    private static final DecompressSupport ZIP = DownloadUtils::decompressZip;
    private static final DecompressSupport TAR = DownloadUtils::decompressTar;
    private static final DecompressSupport TGZ = DownloadUtils::decompressTgz;
    private static final DecompressSupport GZ = DownloadUtils::decompressGz;
    private static final DecompressSupport TYZ = DownloadUtils::decompressTxz;

    /**
     * Determines the appropriate decompression strategy based on the file's extension.
     *
     * Supported extensions include:
     * <ul>
     *   <li>.zip</li>
     *   <li>.tar</li>
     *   <li>.tar.gz, .tgz</li>
     *   <li>.gz</li>
     *   <li>.tar.xz, .txz</li>
     * </ul>
     *
     * @param filePath the path to the compressed file.
     * @return a {@link DecompressSupport} instance for the file type, or {@code null} if the extension is unsupported.
     * @throws IOException if an error occurs when determining the file type.
     */
    public static @Nullable DecompressSupport getDecompressor(@NotNull Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".zip")) {
            return ZIP;
        } else if (fileName.endsWith(".tar")) {
            return TAR;
        } else if (fileName.endsWith("tar.gz") || fileName.endsWith(".tgz")) {
            return TGZ;
        } else if (fileName.endsWith(".gz")) {
            return GZ;
        } else if (fileName.endsWith("tar.xz") || fileName.endsWith(".txz")) {
            return TYZ;
        }
        return null;
    }

    private static Path decompressZip(@NotNull Path filePath, @NotNull Path targetDir) throws IOException {
        return decompress(new Decompressor.Zip(filePath), targetDir);
    }

    private static Path decompressTar(@NotNull Path filePath, @NotNull Path targetDir) throws IOException {
        return decompress(new Decompressor.Tar(filePath), targetDir);
    }

    private static @Nullable Path decompress(Decompressor decompressor, @NotNull Path targetDir) throws IOException {
        final Set<String> topLevel = new HashSet<>();
        decompressor
                .entryFilter(entry -> {
                    String entryName = entry != null ? entry.name : null;
                    if (entryName == null || entryName.isEmpty()) return false;
                    entryName = entryName.replace("\\", "/");
                    String[] parts = entryName.split("/");
                    if (parts.length > 0) {
                        topLevel.add(parts[0]);
                    }
                    return true;
                })
                .extract(targetDir);
        if (topLevel.size() == 1) {
            return targetDir.resolve(topLevel.iterator().next());
        } else {
            return null;
        }
    }

    /**
     * Decompresses a GZIP-compressed TAR archive (.tar.gz or .tgz).
     *
     * @param filePath the path to the .tar.gz or .tgz file.
     * @param targetDir the directory where the extracted contents should be placed.
     * @return the path to the root of the extracted content, or {@code null} if multiple root entries exist.
     * @throws IOException if an error occurs during decompression.
     */
    private static Path decompressTgz(@NotNull Path filePath, @NotNull Path targetDir) throws IOException {
        try (InputStream fis = Files.newInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gzipInputStream = new GZIPInputStream(bis)) {
            Path tarFilePath = filePath.getParent().resolve(stripExtension(filePath));
            Files.copy(gzipInputStream, tarFilePath);
            var targetFile = decompressTar(tarFilePath, targetDir);
            Files.deleteIfExists(tarFilePath);
            return targetFile;
        }
    }

    /**
     * Decompresses a GZIP file (not a TAR archive).
     *
     * @param filePath the path to the .gz file.
     * @param targetDir the directory where the decompressed file should be placed.
     * @return the path to the decompressed file.
     * @throws IOException if an error occurs during decompression.
     */
    private static Path decompressGz(@NotNull Path filePath, @NotNull Path targetDir) throws IOException {
        try (InputStream fis = Files.newInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GZIPInputStream gzipInputStream = new GZIPInputStream(bis)) {
            Path outputFile = targetDir.resolve(stripExtension(filePath));
            Files.copy(gzipInputStream, outputFile);
            return outputFile;
        }
    }

    /**
     * Decompresses a XZ-compressed TAR archive (.tar.xz or .txz).
     *
     * @param filePath the path to the .tar.xz or .txz file.
     * @param targetDir the directory where the contents will be extracted.
     * @return the path to the root of the extracted content, or {@code null} if multiple root entries exist.
     * @throws IOException if an error occurs during decompression.
     */
    private static Path decompressTxz(@NotNull Path filePath, @NotNull Path targetDir) throws IOException {
        try (InputStream fis = Files.newInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             XZInputStream xzInputStream = new XZInputStream(bis)) {
            Path tarFilePath = filePath.getParent().resolve(stripExtension(filePath));
            Files.copy(xzInputStream, tarFilePath);
            var targetFile = decompressTar(filePath, targetDir);
            Files.deleteIfExists(tarFilePath);
            return targetFile;
        }
    }

    /**
     * Removes the extension from a filename.
     *
     * @param path the file path.
     * @return the filename without extension.
     */
    private static String stripExtension(@NotNull Path path) {
        String fileName = path.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        return (index > 0) ? fileName.substring(0, index) : fileName;
    }
}
