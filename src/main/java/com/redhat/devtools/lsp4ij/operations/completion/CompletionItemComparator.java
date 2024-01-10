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
package com.redhat.devtools.lsp4ij.operations.completion;

import java.util.Comparator;

import org.eclipse.lsp4j.CompletionItem;
import org.jetbrains.annotations.Nullable;

/**
 * Compares {@link CompletionItem}s by their sortText property (falls back to comparing labels)
 */
public class CompletionItemComparator implements Comparator<CompletionItem> {
	@Override
	public int compare(CompletionItem item1, CompletionItem item2) {
		if (item1 == item2) {
			return 0;
		} else if (item1 == null) {
			return -1;
		} else if (item2 == null) {
			return 1;
		}

		int comparison = compareNullable(item1.getSortText(), item2.getSortText());

		// If sortText is equal, fall back to comparing labels
		if (comparison == 0) {
			comparison = item1.getLabel().compareTo(item2.getLabel());
		}

		return comparison;
	}

	private int compareNullable(@Nullable String s1, @Nullable String s2) {
		if (s1 == s2) {
			return 0;
		} else if (s1 == null) {
			return -1;
		} else if (s2 == null) {
			return 1;
		}
		return s1.compareTo(s2);
	}
}