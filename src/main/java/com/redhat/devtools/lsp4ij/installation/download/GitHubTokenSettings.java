/*******************************************************************************
 * Copyright (c) 2026 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.installation.download;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * The GitHub token of the LSP4IJ settings, which a {@code github} download sends to the GitHub API when neither
 * {@code GITHUB_TOKEN} nor {@code GH_TOKEN} is set (#2188). It's kept in the IDE's Password Safe, for the whole IDE,
 * never in a settings file.
 * <p>
 * The Password Safe can be slow, as the keychain of the OS, and 2026.3 requires a background thread for it: these
 * methods are never called on the EDT.
 */
@ApiStatus.Internal
public final class GitHubTokenSettings {

    /**
     * The entry of the token in the Password Safe.
     */
    static final CredentialAttributes CREDENTIAL_ATTRIBUTES =
            new CredentialAttributes(CredentialAttributesKt.generateServiceName("LSP4IJ", "GitHub token"));

    private GitHubTokenSettings() {
    }

    /**
     * Returns the GitHub token of the LSP4IJ settings, or null if there is none.
     *
     * @return the GitHub token of the LSP4IJ settings, or null.
     */
    @RequiresBackgroundThread
    public static @Nullable String getToken() {
        String token = PasswordSafe.getInstance().getPassword(CREDENTIAL_ATTRIBUTES);
        return token == null || token.isBlank() ? null : token;
    }

    /**
     * Stores the given GitHub token in the LSP4IJ settings, without the spaces around it, or removes the stored one if
     * the given one is null or blank.
     *
     * @param token the GitHub token, or null or blank to remove it.
     */
    @RequiresBackgroundThread
    public static void setToken(@Nullable String token) {
        PasswordSafe.getInstance().setPassword(CREDENTIAL_ATTRIBUTES,
                token == null || token.isBlank() ? null : token.strip());
    }
}
