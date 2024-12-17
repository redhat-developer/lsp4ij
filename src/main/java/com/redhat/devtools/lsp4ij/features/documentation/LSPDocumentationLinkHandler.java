/*******************************************************************************
 * Copyright (c) 2024 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.features.documentation;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.platform.backend.documentation.DocumentationLinkHandler;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.LinkResolveResult;
import com.redhat.devtools.lsp4ij.LSPIJUtils;
import com.redhat.devtools.lsp4ij.client.features.FileUriSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * LSP {@link DocumentationLinkHandler} to open file in an
 * IJ Editor (and with a given position if declared) declared as link in HTML.
 */
@ApiStatus.Internal
public class LSPDocumentationLinkHandler implements DocumentationLinkHandler {

    @Override
    public @Nullable LinkResolveResult resolveLink(@NotNull DocumentationTarget target,
                                                   @NotNull String url) {
        if (target instanceof LSPDocumentationTarget lspTarget && url.startsWith("file://")) {
            ApplicationManager.getApplication()
                    .executeOnPooledThread(() -> {
                        var file = lspTarget.getFile();
                        FileUriSupport fileUriSupport = lspTarget.getLanguageServer().getClientFeatures();;
                        LSPIJUtils.openInEditor(url, null, true, true, fileUriSupport, file.getProject());
                    });
            return LinkResolveResult.resolvedTarget(target);
        }
        return null;
    }

}
