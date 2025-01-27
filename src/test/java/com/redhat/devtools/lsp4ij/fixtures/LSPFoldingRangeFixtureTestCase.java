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
package com.redhat.devtools.lsp4ij.fixtures;

import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.TextRangeUtil;
import com.redhat.devtools.lsp4ij.JSONUtils;
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.lsp4ij.mock.MockLanguageServer;
import org.eclipse.lsp4j.FoldingRange;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class test case to test LSP 'textDocument/foldingRange' feature.
 */
public abstract class LSPFoldingRangeFixtureTestCase extends LSPCodeInsightFixtureTestCase {

    private static final String START_TOKEN_TEXT = "start";
    private static final String END_TOKEN_TEXT = "end";
    // For simplicity's sake, we only support up to 10 start/end token pairs
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?ms)<(" + START_TOKEN_TEXT + "|" + END_TOKEN_TEXT + ")(\\d)>");
    private static final int START_TOKEN_LENGTH = START_TOKEN_TEXT.length() + 3;
    private static final int END_TOKEN_LENGTH = END_TOKEN_TEXT.length() + 3;

    protected LSPFoldingRangeFixtureTestCase(String... fileNamePatterns) {
        super(fileNamePatterns);
    }

    /**
     * Verifies that the folding ranges are as-expected by matching them against expected
     * <code>&lt;start<i>N</i>&gt;</code> / <code>&lt;end<i>N</i>&gt;</code> tag pairs that represent the start/end
     * offsets of the corresponding {@link FoldRegion fold regions}.
     *
     * @param fileName                       the file name
     * @param fileBody                       the file body with embedded tag pairs for expected fold regions
     * @param mockFoldingRangesJson          the mock <code>textDocument/foldingRange</code> response
     * @param collapsedByDefaultRangeNumbers the optional 1-based numbers of the expected ranges which should be
     *                                       collapsed initially
     */
    protected void assertFoldingRanges(@NotNull String fileName,
                                       @NotNull String fileBody,
                                       @NotNull String mockFoldingRangesJson,
                                       @NotNull Integer... collapsedByDefaultRangeNumbers) {
        MockLanguageServer.INSTANCE.setTimeToProceedQueries(100);
        List<FoldingRange> mockFoldingRanges = JSONUtils.getLsp4jGson().fromJson(mockFoldingRangesJson, new TypeToken<List<FoldingRange>>() {
        }.getType());
        // Sort for stability
        mockFoldingRanges.sort((foldingRange1, foldingRange2) -> {
            int startLine1 = foldingRange1.getStartLine();
            int startLine2 = foldingRange2.getStartLine();
            if (startLine1 != startLine2) {
                return startLine1 - startLine2;
            }

            int startCharacter1 = foldingRange1.getStartCharacter();
            int startCharacter2 = foldingRange2.getStartCharacter();
            if (startCharacter1 != startCharacter2) {
                return startCharacter1 - startCharacter2;
            }

            return 0;
        });
        MockLanguageServer.INSTANCE.setFoldingRanges(mockFoldingRanges);

        PsiFile file = myFixture.configureByText(fileName, stripTokens(fileBody));
        Editor editor = myFixture.getEditor();

        // Initialize the language server
        try {
            LanguageServiceAccessor.getInstance(file.getProject())
                    .getLanguageServers(file.getVirtualFile(), null, null)
                    .get(5000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            fail(e.getMessage());
        }

        EditorTestUtil.buildInitialFoldingsInBackground(editor);
        FoldingModel foldingModel = editor.getFoldingModel();
        FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();

        // Derive the expected text ranges from the tokenized file body
        List<TextRange> expectedTextRanges = getExpectedTextRanges(fileBody);
        assertEquals(expectedTextRanges.size(), foldRegions.length);

        for (int i = 0; i < foldRegions.length; i++) {
            FoldRegion actualFoldRegion = foldRegions[i];

            // Check the text range
            TextRange expectedTextRange = expectedTextRanges.get(i);
            TextRange actualTextRange = actualFoldRegion.getTextRange();
            assertEquals(expectedTextRange, actualTextRange);

            // Check the placeholder text
            FoldingRange mockFoldingRange = mockFoldingRanges.get(i);
            String mockCollapsedText = mockFoldingRange.getCollapsedText();
            String expectedPlaceholderText = StringUtil.isNotEmpty(mockCollapsedText) ? mockCollapsedText : "...";
            String actualPlaceholderText = actualFoldRegion.getPlaceholderText();
            assertEquals(expectedPlaceholderText, actualPlaceholderText);

            // Check the initial expansion state
            assertEquals("Incorrect expansion state for region " + (i + 1) + ".", ArrayUtil.contains(i + 1, collapsedByDefaultRangeNumbers), !actualFoldRegion.isExpanded());
        }
    }

    @NotNull
    private static String stripTokens(@NotNull String fileBody) {
        return fileBody.replaceAll(TOKEN_PATTERN.pattern(), "");
    }

    @NotNull
    private static List<TextRange> getExpectedTextRanges(@NotNull String fileBody) {
        // Gather raw start and end token offsets
        Map<Integer, Integer> rawStartOffsetsByIndex = new LinkedHashMap<>();
        Map<Integer, Integer> rawEndOffsetsByIndex = new LinkedHashMap<>();
        Matcher tokenMatcher = TOKEN_PATTERN.matcher(fileBody);
        while (tokenMatcher.find()) {
            String tokenText = tokenMatcher.group(1);
            int tokenIndex = Integer.parseInt(tokenMatcher.group(2));
            int rawStartOffset = tokenMatcher.start();
            if (tokenText.contains(START_TOKEN_TEXT)) {
                if (rawStartOffsetsByIndex.containsKey(tokenIndex)) {
                    fail("Multiple start tokens were found for index " + tokenIndex + ".");
                }
                rawStartOffsetsByIndex.put(tokenIndex, rawStartOffset);
            } else {
                if (rawEndOffsetsByIndex.containsKey(tokenIndex)) {
                    fail("Multiple end tokens were found for index " + tokenIndex + ".");
                }
                rawEndOffsetsByIndex.put(tokenIndex, rawStartOffset);
            }
        }
        assertFalse("No start tokens found.", rawStartOffsetsByIndex.isEmpty());
        assertFalse("No end tokens found.", rawEndOffsetsByIndex.isEmpty());
        assertEquals("Start and end tokens do not match in length.", rawStartOffsetsByIndex.size(), rawEndOffsetsByIndex.size());
        assertEquals("Start and end tokens do not have paired indexes.", rawStartOffsetsByIndex.keySet(), rawEndOffsetsByIndex.keySet());

        // Align the raw start and end offset collections
        List<Integer> rawStartOffsets = new ArrayList<>(rawStartOffsetsByIndex.values());
        List<Integer> rawEndOffsets = new ArrayList<>(rawStartOffsetsByIndex.size());
        for (Integer rawStartOffsetIndex : rawStartOffsetsByIndex.keySet()) {
            Integer rawEndOffset = rawEndOffsetsByIndex.get(rawStartOffsetIndex);
            assertNotNull("Failed to find the end offset with index " + rawStartOffsetIndex + ".", rawEndOffset);
            rawEndOffsets.add(rawEndOffset);
        }

        // Adjust final offsets as appropriate based on relative token positioning
        List<Integer> startOffsets = new ArrayList<>(rawStartOffsets.size());
        for (int i = 0; i < rawStartOffsets.size(); i++) {
            int currentRawStartOffset = rawStartOffsets.get(i);
            int startOffset = currentRawStartOffset;
            for (Integer rawStartOffset : rawStartOffsets) {
                if (currentRawStartOffset > rawStartOffset) startOffset -= START_TOKEN_LENGTH;
            }
            for (int rawEndOffset : rawEndOffsets) {
                if (currentRawStartOffset > rawEndOffset) startOffset -= END_TOKEN_LENGTH;
            }
            startOffsets.add(startOffset);
        }
        List<Integer> endOffsets = new ArrayList<>(rawEndOffsets.size());
        for (int i = 0; i < rawEndOffsets.size(); i++) {
            int currentRawEndOffset = rawEndOffsets.get(i);
            int endOffset = currentRawEndOffset;
            for (int rawStartOffset : rawStartOffsets) {
                if (currentRawEndOffset > rawStartOffset) endOffset -= START_TOKEN_LENGTH;
            }
            for (Integer rawEndOffset : rawEndOffsets) {
                if (currentRawEndOffset > rawEndOffset) endOffset -= END_TOKEN_LENGTH;
            }
            endOffsets.add(endOffset);
        }

        // Create text ranges from the start and end offset pairs
        List<TextRange> expectedTextRanges = new ArrayList<>(startOffsets.size());
        for (int i = 0; i < startOffsets.size(); i++) {
            int startOffset = startOffsets.get(i);
            int endOffset = endOffsets.get(i);
            expectedTextRanges.add(TextRange.create(startOffset, endOffset));
        }

        // Sort for stability
        expectedTextRanges.sort(TextRangeUtil.RANGE_COMPARATOR);
        return expectedTextRanges;
    }
}
