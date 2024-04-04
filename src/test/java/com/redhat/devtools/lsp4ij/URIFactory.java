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
package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.util.SystemInfo;

import java.net.URI;

/**
 * File uri factory used for tests.
 */
public class URIFactory {

    public static URI createFileUri(String filePath) {
        return URI.create(getBaseUri() + filePath);
    }

    public static String getBaseUri() {
        return (SystemInfo.isWindows ? "file:///" : "file://") + getBaseDir();
    }

    public static String getBaseDir() {
        String baseDir = System.getProperty("user.home")
                .replace('\\', '/');
        if (!baseDir.endsWith("/")) {
            return baseDir + "/";
        }
        return baseDir;
    }
}
