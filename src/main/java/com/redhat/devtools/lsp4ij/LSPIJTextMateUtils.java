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

package com.redhat.devtools.lsp4ij;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.Constants;
import org.jetbrains.plugins.textmate.TextMateService;
import org.jetbrains.plugins.textmate.editor.TextMateEditorUtils;
import org.jetbrains.plugins.textmate.language.preferences.Preferences;
import org.jetbrains.plugins.textmate.language.preferences.TextMateBracePair;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.psi.TextMateFile;

import java.util.*;

@ApiStatus.Internal
public final class LSPIJTextMateUtils {

    private LSPIJTextMateUtils() {
        // Pure utility class
    }

    @NotNull
    @ApiStatus.Internal
    public static Set<TextMateBracePair> getTextMateBracePairs(@NotNull PsiFile file) {
        Set<TextMateBracePair> bracePairs = new LinkedHashSet<>();
        if (file instanceof TextMateFile) {
            Editor editor = LSPIJUtils.editorForElement(file);
            TextMateScope selector = editor instanceof EditorEx ? TextMateEditorUtils.getCurrentScopeSelector((EditorEx) editor) : null;
            ContainerUtil.addAllNotNull(bracePairs, getAllPairsForMatcher(selector));
        }
        return bracePairs;
    }

    // NOTE: Cloned from TextMateEditorUtils where this is private
    @NotNull
    private static Set<TextMateBracePair> getAllPairsForMatcher(@Nullable TextMateScope selector) {
        if (selector == null) {
            return Constants.DEFAULT_HIGHLIGHTING_BRACE_PAIRS;
        }
        Set<TextMateBracePair> result = new HashSet<>();
        List<Preferences> preferencesForSelector = TextMateService.getInstance().getPreferenceRegistry().getPreferences(selector);
        for (Preferences preferences : preferencesForSelector) {
            final Set<TextMateBracePair> highlightingPairs = preferences.getHighlightingPairs();
            if (highlightingPairs != null) {
                if (highlightingPairs.isEmpty()) {
                    // smart typing pairs can be defined in preferences but can be empty (in order to disable smart typing completely)
                    return Collections.emptySet();
                }
                result.addAll(highlightingPairs);
            }
        }
        return result;
    }

    @NotNull
    @ApiStatus.Internal
    public static Set<TextMateBracePair> getSimpleTextMateBracePairs(@NotNull PsiFile file) {
        Set<TextMateBracePair> bracePairs = getTextMateBracePairs(file);
        // We can only handle single character brace pairs here, so remove anything else
        bracePairs.removeIf(bracePair -> (bracePair.getLeft().length() != 1) || (bracePair.getRight().length() != 1));
        return bracePairs;
    }
}
