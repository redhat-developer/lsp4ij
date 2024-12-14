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

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.openapi.util.text.StringUtil;
import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Compares {@link CompletionItem}s by their sortText property (falls back to comparing labels)
 */
public class CompletionItemComparator implements Comparator<CompletionItem> {
	private final PrefixMatcher prefixMatcher;
	private final String currentWord;
	private final boolean caseSensitive;

	public CompletionItemComparator(@Nullable PrefixMatcher prefixMatcher,
									@Nullable String currentWord,
									boolean caseSensitive) {
		this.prefixMatcher = prefixMatcher;
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

		// If one is a better match for the current word than the other, sort it higher
		int comparison = compareAgainstCurrentWord(item1, item2);
		if (comparison != 0) {
			return comparison;
		}

		// If one is a better completion for the current prefix than the other, sort it higher
		comparison = compareAgainstPrefix(item1, item2);
		if (comparison != 0) {
			return comparison;
		}

		// Order by language server-provided sort text
		comparison = compare(item1.getSortText(), item2.getSortText());
		if (comparison != 0) {
			return comparison;
		}

		// If sortText is equal, fall back to comparing labels
		return compare(item1.getLabel(), item2.getLabel());
	}

	private int compare(@Nullable String string1, @Nullable String string2) {
		return StringUtil.compare(string1, string2, !caseSensitive);
	}

	private boolean equals(@Nullable String string1, @Nullable String string2) {
		return StringUtil.compare(string1, string2, !caseSensitive) == 0;
	}

	private boolean startsWith(@Nullable String string, @Nullable String prefix) {
		if ((string == null) || (prefix == null)) {
			return false;
		}
		return caseSensitive ? StringUtil.startsWith(string, prefix) : StringUtil.startsWithIgnoreCase(string, prefix);
	}

	private boolean contains(@Nullable String string, @Nullable String substring) {
		if ((string == null) || (substring == null)) {
			return false;
		}
		return caseSensitive ? StringUtil.contains(string, substring) : StringUtil.containsIgnoreCase(string, substring);
	}

	private int compareAgainstCurrentWord(@NotNull CompletionItem item1, @NotNull CompletionItem item2) {
		if (currentWord != null) {
			String label1 = item1.getLabel();
			String label2 = item2.getLabel();

			// Exact match
			if (equals(currentWord, label1) &&
				((label2 == null) || !equals(currentWord, label2))) {
				return -1;
			} else if (equals(currentWord, label2) &&
					   ((label1 == null) || !equals(currentWord, label1))) {
				return 1;
			}
			// Starts with
			else if ((startsWith(currentWord, label1) || startsWith(label1, currentWord)) &&
				((label2 == null) || !(startsWith(currentWord, label2) || startsWith(label2, currentWord)))) {
				return -1;
			} else if ((startsWith(currentWord, label2) || startsWith(label2, currentWord)) &&
					   ((label1 == null) || !(startsWith(currentWord, label1) || startsWith(label1, currentWord)))) {
				return 1;
			}
			// Contains
			else if (contains(currentWord, label1) &&
					 ((label2 == null) || !contains(currentWord, label2))) {
				return -1;
			} else if (contains(currentWord, label2) &&
					   ((label1 == null) || !contains(currentWord, label1))) {
				return 1;
			}
		}

		return 0;
	}

	private int compareAgainstPrefix(@NotNull CompletionItem item1, @NotNull CompletionItem item2) {
		if (prefixMatcher != null) {
			String prefix = prefixMatcher.getPrefix();
			String label1 = item1.getLabel();
			String label2 = item2.getLabel();

			// Start starts with
			if (startsWith(label1, prefix) &&
				(!startsWith(label2, prefix))) {
				return -1;
			} else if (startsWith(label2, prefix) &&
					   (!startsWith(label1, prefix))) {
				return 1;
			}
			// Loose/camel-hump starts with
			else if ((label1 != null) && prefixMatcher.isStartMatch(label1) &&
					 ((label2 == null) || !prefixMatcher.isStartMatch(label2))) {
				return -1;
			} else if ((label2 != null) && prefixMatcher.isStartMatch(label2) &&
					   ((label1 == null) || !prefixMatcher.isStartMatch(label1))) {
				return 1;
			}
		}

		return 0;
	}
}