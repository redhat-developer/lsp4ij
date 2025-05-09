/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and declaration
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.internal.uri;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Function;

/**
 * This class is a translation of the vscode-uri _asFormatted method
 * <a href="https://github.com/microsoft/vscode-uri/blob/edfdccd976efaf4bb8fdeca87e97c47257721729/src/uri.ts#L594">uri.ts#L594</a>
 * to support encoded URI vscode style (ex : file:///c%3A/Users....)
 */
public class UriFormatter {

    public static String asFormatted(URI uri, boolean skipEncoding) {
        // Choose the encoder based on skipEncoding
    	Function<String, String> encoder = skipEncoding
            ? UriFormatter::encodeURIComponentMinimal
            : UriFormatter::encodeURIComponentFast;

        StringBuilder res = new StringBuilder();
        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        String path = uri.getRawPath();
        String query = uri.getRawQuery();
        String fragment = uri.getRawFragment();

        if (scheme != null) {
            res.append(scheme).append(':');
        }

        if ((authority != null && !authority.isEmpty()) || "file".equals(scheme)) {
            res.append("//");
        }

        if (authority != null && !authority.isEmpty()) {
            res.append(authority.toLowerCase(Locale.ROOT));
        }

        if (path != null && !path.isEmpty()) {
            path = normalizeDriveLetter(path);
            res.append(encode(path, encoder));
        }

        if (query != null) {
            res.append('?').append(encode(query, encoder));
        }

        if (fragment != null) {
            res.append('#').append(encode(fragment, encoder));
        }

        return res.toString();
    }

    /**
     * Normalize the drive letter for Windows paths (e.g., /C:/ to /c:/)
     */
    private static String normalizeDriveLetter(String path) {
        if (path.length() >= 3 && path.charAt(0) == '/' && path.charAt(2) == ':') {
            char drive = path.charAt(1);
            if (Character.isUpperCase(drive)) {
                return "/" + Character.toLowerCase(drive) + ":" + path.substring(3);
            }
        } else if (path.length() >= 2 && path.charAt(1) == ':') {
            char drive = path.charAt(0);
            if (Character.isUpperCase(drive)) {
                return Character.toLowerCase(drive) + ":" + path.substring(2);
            }
        }
        return path;
    }

    /**
     * Encoder for the URI component, using strict encoding (with minimal encoding)
     */
    public static String encodeURIComponentFast(String input) {
        return encodeURIComponentWithRules(input, false, false);
    }

    /**
     * Encoder for the URI component, using permissive encoding
     */
    public static String encodeURIComponentMinimal(String input) {
        return encodeURIComponentWithRules(input, true, true);
    }

    /**
     * Main encoding function, rule-based: encode only reserved characters
     */
    private static String encodeURIComponentWithRules(String input, boolean minimal, boolean preserveColon) {
        if (input == null) return null;
        StringBuilder result = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (isSafe(c, minimal, preserveColon)) {
                result.append(c);
            } else {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    result.append('%');
                    result.append(String.format("%02X", b));
                }
            }
        }
        return result.toString();
    }

    /**
     * Check whether a character is safe according to the rules
     */
    private static boolean isSafe(char c, boolean minimal, boolean preserveColon) {
        if (preserveColon && c == ':') return true;
        if (c == '/') return true;

        // Same set of unreserved characters per RFC 3986
        return (c >= 'A' && c <= 'Z') ||
               (c >= 'a' && c <= 'z') ||
               (c >= '0' && c <= '9') ||
               c == '-' || c == '_' || c == '.' || c == '~';
    }

    /**
     * Encoding function for URI components
     */
    private static String encode(String input, Function<String, String> encoder) {
        return encoder.apply(input);
    }

   
}
