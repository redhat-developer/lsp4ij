/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation.markdown;

import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.features.documentation.MarkdownConverter;
import com.vladsch.flexmark.ast.Image;
import com.vladsch.flexmark.ast.Link;
import com.vladsch.flexmark.ast.Reference;
import com.vladsch.flexmark.html.LinkResolver;
import com.vladsch.flexmark.html.LinkResolverFactory;
import com.vladsch.flexmark.html.renderer.LinkResolverBasicContext;
import com.vladsch.flexmark.html.renderer.LinkStatus;
import com.vladsch.flexmark.html.renderer.ResolvedLink;
import com.vladsch.flexmark.util.ast.Node;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Custom link resolver used to resolve relative path by using the {@link com.intellij.psi.PsiFile}
 * path which triggers the MarkDown converter for hover and completion documentation.
 */
public class LSPLinkResolver implements LinkResolver {

    private final Path fileBaseDir;
    //supports both file:/ and file:///
    private static final String fileRegex = "^file:/{1,3}";
    private static final Pattern pattern = Pattern.compile(fileRegex);
    public static final String FILE_PROTOCOL_RFC = "file:///";

    private enum FileUrlKind {
        RELATIVE,
        ABSOLUTE,
        NONE,
        FILE;
    }

    public LSPLinkResolver(LinkResolverBasicContext context) {
        this.fileBaseDir = MarkdownConverter.FILE_BASE_DIR.get(context.getOptions());
    }

    @Override
    public @NotNull ResolvedLink resolveLink(@NotNull Node node, @NotNull LinkResolverBasicContext context, @NotNull ResolvedLink link) {
        if (node instanceof Image || node instanceof Link || node instanceof Reference) {
            String url = link.getUrl();
            FileUrlKind fileUrlKind = getFileUrlKind(url);
            if ((fileUrlKind == FileUrlKind.RELATIVE && fileBaseDir != null) || fileUrlKind == FileUrlKind.ABSOLUTE) {
                String position = "";
                int hashIndex= url.indexOf("#");
                if (hashIndex != -1) {
                    position = url.substring(hashIndex, url.length());
                    url = url.substring(0, hashIndex);
                }
                try {
                    File resolvedFile = getResolvedFile(url, fileUrlKind);
                    String resolvedUri = LSPIJUtils.toUri(resolvedFile).toASCIIString() + position;
                    return link.withStatus(LinkStatus.VALID)
                            .withUrl(resolvedUri);
                }
                catch(Exception e) {

                }
            }
            else if(FileUrlKind.FILE.equals(fileUrlKind)){
                if(url.contains(FILE_PROTOCOL_RFC)){
                    return link.withStatus(LinkStatus.VALID)
                            .withUrl(url);
                }
                // convert to RFC 8089 standard
                // sometimes URL will contain only file:/
                // this needs to be converted so that markdown will be resolved properly
                return link.withStatus(LinkStatus.VALID)
                        .withUrl(url.replace("file:/", FILE_PROTOCOL_RFC));
            }
        }
        return link;
    }

    private @NotNull File getResolvedFile(String url, FileUrlKind fileUrlKind) {
        if (fileUrlKind == FileUrlKind.RELATIVE) {
            return fileBaseDir.resolve(url).toFile();
        }
        return Paths.get(url).toFile();
    }


    private static FileUrlKind getFileUrlKind(String url) {
        if (url.isBlank()) {
            return FileUrlKind.NONE;
        }
        if (url.charAt(0) == '/') {
            // ex : /path/to/file.txt
            return FileUrlKind.ABSOLUTE;
        }
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            // ex : file:///C:/path/to/file.txt -> RFC 8089 standard
            // ex : file:/path/to/file.txt -> standard format for local file system without authority specified
            return FileUrlKind.FILE;
        }
        int index = url.indexOf("://");
        if (index == -1) {
            // ex : path/to/file.txt
            return FileUrlKind.RELATIVE;
        }

        // ex : https://github.com/redhat-developer/lsp4ij
        return FileUrlKind.NONE;
    }


    public static class Factory implements LinkResolverFactory {

        @Nullable
        @Override
        public Set<Class<?>> getAfterDependents() {
            return null;
        }

        @Nullable
        @Override
        public Set<Class<?>> getBeforeDependents() {
            return null;
        }

        @Override
        public boolean affectsGlobalScope() {
            return false;
        }

        @NotNull
        @Override
        public LinkResolver apply(@NotNull LinkResolverBasicContext context) {
            return new LSPLinkResolver(context);
        }
    }

}
