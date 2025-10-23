/*******************************************************************************
 * Copyright (c) 2025 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.lsp4ij.dap.disassembly;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Utility class providing helper methods for disassembly operations used by the
 * Debug Adapter Protocol (DAP) integration.
 * <p>
 * This class provides a set of static utility methods commonly required when
 * dealing with memory addresses, instruction offsets, and array-like manipulations
 * that are inspired by JavaScript behavior, such as {@code BigInt} parsing and
 * {@code Array.splice}.
 * </p>
 *
 * <p><strong>Main features:</strong></p>
 * <ul>
 *   <li>Parsing string representations of numbers with different prefixes
 *       (hexadecimal, octal, binary, decimal) into {@link BigInteger}.</li>
 *   <li>Emulating JavaScript's {@code Array.splice} behavior for modifying lists.</li>
 *   <li>Performing efficient binary searches using a comparator function.</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Parsing different numeric representations
 * BigInteger hex = DisassemblyUtils.parse("0xFF");
 * BigInteger binary = DisassemblyUtils.parse("0b1010");
 * BigInteger octal = DisassemblyUtils.parse("0o755");
 * BigInteger decimal = DisassemblyUtils.parse("1234");
 *
 * // Using splice to modify a list
 * List<String> list = new ArrayList<>(List.of("A", "B", "C", "D"));
 * List<String> removed = DisassemblyUtils.splice(list, 1, 2, List.of("X", "Y"));
 * // list becomes ["A", "X", "Y", "D"], removed contains ["B", "C"]
 *
 * // Performing a binary search
 * int index = DisassemblyUtils.binarySearch2(10, i -> Integer.compare(i, 7));
 * }</pre>
 *
 * @author Red Hat
 */
public class DisassemblyUtils {

    /**
     * Parses a numeric string and converts it into a {@link BigInteger} instance.
     * <p>
     * This method mimics the behavior of the TypeScript {@code BigInt} constructor by
     * supporting multiple numeric notations:
     * </p>
     * <ul>
     *   <li><b>Hexadecimal</b>: Strings starting with {@code "0x"} or {@code "0X"} → base 16</li>
     *   <li><b>Octal</b>: Strings starting with {@code "0o"} or {@code "0O"} → base 8</li>
     *   <li><b>Binary</b>: Strings starting with {@code "0b"} or {@code "0B"} → base 2</li>
     *   <li><b>Decimal</b>: Any other numeric string → base 10</li>
     * </ul>
     *
     * @param value the string representation of the number to parse (must not be {@code null}).
     * @return a {@link BigInteger} representing the parsed value.
     * @throws NumberFormatException if the string is {@code null}, empty, or not a valid number
     *                               according to the detected base.
     */
    public static BigInteger parseBigInteger(String value) {
        if (value == null) {
            throw new NumberFormatException("null");
        }

        value = value.trim();

        if (value.startsWith("0x") || value.startsWith("0X")) {
            return new BigInteger(value.substring(2), 16);
        }
        if (value.startsWith("0o") || value.startsWith("0O")) {
            return new BigInteger(value.substring(2), 8);
        }
        if (value.startsWith("0b") || value.startsWith("0B")) {
            return new BigInteger(value.substring(2), 2);
        }

        // Default to decimal
        return new BigInteger(value, 10);
    }

    /**
     * Emulates the behavior of JavaScript's {@code Array.splice} method.
     * <p>
     * This method modifies a given list by removing a certain number of elements starting
     * at a specified index, and optionally inserting new elements at that position.
     * It returns a list containing the removed elements.
     * </p>
     *
     * <p><strong>Behavior details:</strong></p>
     * <ul>
     *   <li>If {@code start} is negative, it is treated as {@code size + start}, with a minimum of 0.</li>
     *   <li>If {@code start} is greater than the list size, it is clamped to the list size.</li>
     *   <li>{@code deleteCount} is clamped between 0 and the number of elements from {@code start} to the end of the list.</li>
     *   <li>If {@code items} is provided, those elements are inserted at the {@code start} position after the removal.</li>
     * </ul>
     *
     * @param list        the list to modify (must be modifiable, e.g., an {@link ArrayList}).
     * @param start       the starting index for removal or insertion. Can be negative.
     * @param deleteCount the number of elements to remove.
     * @param items       the elements to insert at the start index. Can be {@code null} or empty if no insertion is needed.
     * @param <T>         the type of elements in the list.
     * @return a list containing the elements that were removed.
     *
     * @throws NullPointerException if {@code list} is {@code null}.
     * @see List#subList(int, int)
     */
    public static <T> List<T> splice(List<T> list, int start, int deleteCount, List<T> items) {
        int size = list.size();

        // Adjust negative index like in JavaScript
        if (start < 0) {
            start = Math.max(size + start, 0);
        } else {
            start = Math.min(start, size);
        }

        // Clamp deleteCount
        deleteCount = Math.max(0, Math.min(deleteCount, size - start));

        // 1. Copy the elements to be removed
        List<T> removed = new ArrayList<>(list.subList(start, start + deleteCount));

        // 2. Remove these elements from the original list
        list.subList(start, start + deleteCount).clear();

        // 3. Insert the new elements, if provided
        if (items != null && !items.isEmpty()) {
            list.addAll(start, items);
        }

        return removed;
    }

    /**
     * Performs a binary search on a conceptual list of given {@code length}.
     * <p>
     * Instead of passing an actual list, this method uses a provided function to compare
     * an index to the target key. This allows efficient searches on virtual or generated
     * data without building a concrete list.
     * </p>
     *
     * <p><strong>Return value:</strong></p>
     * <ul>
     *   <li>If the target is found, returns the index of the matching element.</li>
     *   <li>If the target is not found, returns {@code -(insertion point + 1)}, where
     *       insertion point is the index where the key should be inserted to maintain order.</li>
     * </ul>
     *
     * <p>This behavior is consistent with {@link java.util.Collections#binarySearch(List, Object)}.</p>
     *
     * @param length       the size of the conceptual list to search.
     * @param compareToKey a function that compares the element at a given index to the target key.
     *                     <ul>
     *                       <li>Should return a negative value if the element is less than the key.</li>
     *                       <li>Zero if the element matches the key.</li>
     *                       <li>A positive value if the element is greater than the key.</li>
     *                     </ul>
     * @return the index of the found element, or {@code -(insertion point + 1)} if not found.
     */
    public static int binarySearch2(int length, IntFunction<Integer> compareToKey) {
        int low = 0;
        int high = length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int comp = compareToKey.apply(mid);

            if (comp < 0) {
                low = mid + 1;
            } else if (comp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }

        return -(low + 1);
    }
}
