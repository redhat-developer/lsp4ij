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
package com.redhat.devtools.lsp4ij.features.completion;

import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Compares {@link CompletionItem}s by their sortText property (falls back to comparing labels)
 */
public class CompletionItemComparator implements Comparator<CompletionItem> {
	private final String currentWord;
	private final boolean caseSensitive;

	public CompletionItemComparator(@Nullable String currentWord, boolean caseSensitive) {
		this.currentWord = currentWord;
		this.caseSensitive = caseSensitive;
	}

	@Override
	public int compare(CompletionItem item1, CompletionItem item2) {
		if (item1 == item2) {
			return 0;
		} else if (item1 == null) {
			return -1;
		} else if (item2 == null) {
			return 1;
		}

		// If one is a better match for the current word than the other, prioritize it higher
		// TODO: Take into account case-sensitivity with the following priorities:
		//  1. Case-sensitive exact match
		//  2. Case-insensitive exact match if a case-insensitive language
		//  3. Prefix case-insensitive match if the all upper-case prefix
		//  4. Prefix case-sensitive camel-hump match
		//  5. Prefix case-insensitive camel-hump match if a case-insensitive language
		int comparison = compareAgainstCurrentWord(item1, item2);
		if (comparison != 0) {
			return comparison;
		}

		comparison = compare(item1.getSortText(), item2.getSortText());
		if (comparison != 0) {
			return comparison;
		}

		// If sortText is equal, fall back to comparing labels
		return compare(item1.getLabel(), item2.getLabel());
	}

	private boolean startsWith(@Nullable String prefix) {
		if ((currentWord == null) || (prefix == null)) {
			return false;
		}
		return caseSensitive ? StringUtil.startsWith(currentWord, prefix) : StringUtil.startsWithIgnoreCase(currentWord, prefix);
	}

	private int compare(@Nullable String s1, @Nullable String s2) {
		return StringUtil.compare(s1, s2, !caseSensitive);
	}

	private int compareAgainstCurrentWord(@NotNull CompletionItem item1, @NotNull CompletionItem item2) {
		if (currentWord != null) {
			String label1 = item1.getLabel();
			String label2 = item2.getLabel();
			if ((label1 != null) && startsWith(label1) &&
				((label2 == null) || !startsWith(label2))) {
				return -1;
			} else if ((label2 != null) && startsWith(label2) &&
					   ((label1 == null) || !startsWith(label1))) {
				return 1;
			}
		}

		return 0;
	}
}