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
package com.redhat.devtools.lsp4ij.installation.definition;

import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Replaces the values which can be secrets in a line that an installer prints with {@link #REDACTED}, before the IDE's
 * log gets it. The console of the installation shows the line as it is.
 * <p>
 * A line can have one in a command, as {@code --token=...}, in a URL, as {@code https://user:password@host} or
 * {@code ?access_token=...}, in an HTTP header, as {@code Authorization: Bearer ...}, or in the output of a command,
 * as a GitHub token or the value of an environment variable such as {@code GITHUB_TOKEN}.
 */
@ApiStatus.Internal
public final class SecretRedactor {

    /**
     * What the log has instead of a value which can be a secret.
     */
    static final String REDACTED = "<redacted>";

    // The words which end the name of a value which can be a secret, as GITHUB_TOKEN, _authToken, client_secret,
    // api-key or X-Amz-Signature
    private static final String SECRET_WORDS = "token|secret|password|passwd|api[-_]?key|credentials?|signature";

    // The name of an environment variable whose value can be a secret, as GITHUB_TOKEN or AWS_SECRET_ACCESS_KEY
    private static final Pattern SECRET_VARIABLE = Pattern.compile("(?i)token|secret|password|passwd|api_?key|credential");

    // A shorter value of such a variable, as 1 or true, is no secret, and replacing it would garble the log
    private static final int MIN_SECRET_LENGTH = 8;

    // Authorization: Bearer ..., Proxy-Authorization: Basic ..., {"Authorization":"Basic ..."}: with any scheme, and
    // quotes around the name and the value, up to the end of the line or of a quoted argument or value
    private static final Pattern AUTHORIZATION =
            Pattern.compile("(?i)\\b((?:proxy-)?authorization[\"']?\\s*[:=]\\s*[\"']?)[^\\r\\n'\"]+");

    // The credentials of the Bearer scheme without the header, as in a command
    private static final Pattern BEARER = Pattern.compile("(?i)\\b(bearer\\s+)[A-Za-z0-9\\-._~+/]+=*");

    // The user and the password of a URL, as https://user:password@host or https://token@host
    private static final Pattern URL_USER_INFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.\\-]*://)[^\\s/?#@'\"]+@");

    // name=value whose name ends with a secret word: GITHUB_TOKEN=..., --password=..., ?access_token=..., _authToken = ...,
    // and a property of an installer as InstallerContext logs it, ${api.token}=...
    private static final Pattern ASSIGNMENT = Pattern.compile("(?i)(?<![a-z0-9_.\\-])((?:--?)?[a-z0-9_.\\-]*(?:"
            + SECRET_WORDS + ")}?\\s*=\\s*)(\"[^\"]*\"|'[^']*'|[^\\s&#'\"]+)");

    // An option whose name ends with a secret word, followed by its value: --token ..., -password ...
    private static final Pattern OPTION = Pattern.compile("(?i)(?<![a-z0-9_.\\-])(--?[a-z0-9_.\\-]*(?:"
            + SECRET_WORDS + ")\\s+)(\"[^\"]*\"|'[^']*'|[^\\s\\-'\"][^\\s'\"]*)");

    // The parameters of a URL whose short name is a secret: the signature of an Azure SAS URL, a Google API key
    private static final Pattern QUERY_PARAMETER = Pattern.compile("(?i)([?&](?:sig|key)=)[^&\\s#'\"]+");

    // A GitHub token, as ghp_..., or a fine-grained one, as github_pat_...
    private static final Pattern GITHUB_TOKEN =
            Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})\\b");

    private SecretRedactor() {
    }

    /**
     * Returns the given line with {@link #REDACTED} instead of each value in it which can be a secret, among them the
     * values of the IDE's environment variables whose name says that they hold a secret, as {@code GITHUB_TOKEN}.
     *
     * @param line a line that LSP4IJ logs, as a URL that an installer downloads.
     * @return the line without its values which can be secrets.
     */
    public static @NotNull String redact(@NotNull String line) {
        return redact(line, EnvironmentUtil.getEnvironmentMap());
    }

    /**
     * Returns the given line with {@link #REDACTED} instead of each value in it which can be a secret.
     *
     * @param line        a line that an installer prints.
     * @param environment the environment variables of the commands of the installer, whose values are secrets when
     *                    their name says so, as {@code GITHUB_TOKEN}.
     * @return the line without its values which can be secrets.
     */
    static @NotNull String redact(@NotNull String line, @NotNull Map<String, String> environment) {
        String redacted = line;
        for (var variable : environment.entrySet()) {
            String value = variable.getValue() != null ? variable.getValue().strip() : "";
            if (value.length() >= MIN_SECRET_LENGTH && SECRET_VARIABLE.matcher(variable.getKey()).find()) {
                redacted = redacted.replace(value, REDACTED);
            }
        }
        redacted = AUTHORIZATION.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = BEARER.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = URL_USER_INFO.matcher(redacted).replaceAll("$1" + REDACTED + "@");
        redacted = ASSIGNMENT.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = OPTION.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = QUERY_PARAMETER.matcher(redacted).replaceAll("$1" + REDACTED);
        return GITHUB_TOKEN.matcher(redacted).replaceAll(REDACTED);
    }
}
