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

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DisassemblyUtils}.
 * Covers parsing of BigInteger values, splice behavior, and custom binary search logic.
 */
class DisassemblyUtilsTest {

    // ------------------------------------------------------------------------
    // Tests for parseBigInteger
    // ------------------------------------------------------------------------

    @Test
    void testParseBigInteger_HexadecimalLowercase() {
        // Should correctly parse lowercase hexadecimal strings
        assertEquals(new BigInteger("255"), DisassemblyUtils.parseBigInteger("0xff"));
    }

    @Test
    void testParseBigInteger_HexadecimalUppercase() {
        // Should correctly parse uppercase hexadecimal strings
        assertEquals(new BigInteger("255"), DisassemblyUtils.parseBigInteger("0XFF"));
    }

    @Test
    void testParseBigInteger_OctalLowercase() {
        // Should correctly parse lowercase octal strings
        assertEquals(new BigInteger("8"), DisassemblyUtils.parseBigInteger("0o10"));
    }

    @Test
    void testParseBigInteger_OctalUppercase() {
        // Should correctly parse uppercase octal strings
        assertEquals(new BigInteger("8"), DisassemblyUtils.parseBigInteger("0O10"));
    }

    @Test
    void testParseBigInteger_BinaryLowercase() {
        // Should correctly parse lowercase binary strings
        assertEquals(new BigInteger("10"), DisassemblyUtils.parseBigInteger("0b1010"));
    }

    @Test
    void testParseBigInteger_BinaryUppercase() {
        // Should correctly parse uppercase binary strings
        assertEquals(new BigInteger("10"), DisassemblyUtils.parseBigInteger("0B1010"));
    }

    @Test
    void testParseBigInteger_Decimal() {
        // Should correctly parse decimal strings
        assertEquals(new BigInteger("12345"), DisassemblyUtils.parseBigInteger("12345"));
    }

    @Test
    void testParseBigInteger_WithWhitespaces() {
        // Should trim leading and trailing whitespaces
        assertEquals(new BigInteger("255"), DisassemblyUtils.parseBigInteger("   0xFF   "));
    }

    @Test
    void testParseBigInteger_InvalidInput_Null() {
        // Should throw exception for null input
        assertThrows(NumberFormatException.class, () -> DisassemblyUtils.parseBigInteger(null));
    }

    @Test
    void testParseBigInteger_InvalidInput_NotANumber() {
        // Should throw exception for non-numeric string
        assertThrows(NumberFormatException.class, () -> DisassemblyUtils.parseBigInteger("hello"));
    }

    @Test
    void testParseBigInteger_InvalidBinaryFormat() {
        // Should throw exception for invalid binary string
        assertThrows(NumberFormatException.class, () -> DisassemblyUtils.parseBigInteger("0b102"));
    }

    // ------------------------------------------------------------------------
    // Tests for splice
    // ------------------------------------------------------------------------

    @Test
    void testSplice_RemoveElements() {
        // Should remove two elements starting from index 1
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
        List<String> removed = DisassemblyUtils.splice(list, 1, 2, null);

        assertEquals(Arrays.asList("B", "C"), removed);
        assertEquals(Arrays.asList("A", "D"), list);
    }

    @Test
    void testSplice_InsertElements() {
        // Should insert two elements without removing any
        List<String> list = new ArrayList<>(Arrays.asList("A", "D"));
        List<String> removed = DisassemblyUtils.splice(list, 1, 0, Arrays.asList("B", "C"));

        assertTrue(removed.isEmpty());
        assertEquals(Arrays.asList("A", "B", "C", "D"), list);
    }

    @Test
    void testSplice_ReplaceElements() {
        // Should replace two elements with two new elements
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
        List<String> removed = DisassemblyUtils.splice(list, 1, 2, Arrays.asList("X", "Y"));

        assertEquals(Arrays.asList("B", "C"), removed);
        assertEquals(Arrays.asList("A", "X", "Y", "D"), list);
    }

    @Test
    void testSplice_StartNegative() {
        // Negative start index should be counted from the end
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C", "D"));
        List<String> removed = DisassemblyUtils.splice(list, -2, 1, Arrays.asList("X"));

        assertEquals(List.of("C"), removed);
        assertEquals(Arrays.asList("A", "B", "X", "D"), list);
    }

    @Test
    void testSplice_DeleteCountTooLarge() {
        // Delete count larger than remaining elements should be adjusted
        List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
        List<String> removed = DisassemblyUtils.splice(list, 1, 10, null);

        assertEquals(Arrays.asList("B", "C"), removed);
        assertEquals(List.of("A"), list);
    }

    @Test
    void testSplice_StartBeyondEnd() {
        // Start index beyond list size should append elements at the end
        List<String> list = new ArrayList<>(Arrays.asList("A", "B"));
        List<String> removed = DisassemblyUtils.splice(list, 10, 2, Arrays.asList("C"));

        assertTrue(removed.isEmpty());
        assertEquals(Arrays.asList("A", "B", "C"), list);
    }

    // ------------------------------------------------------------------------
    // Tests for binarySearch2
    // ------------------------------------------------------------------------

    @Test
    void testBinarySearch2_Found() {
        // Should return the index of an existing element
        int[] array = {1, 3, 5, 7, 9};
        int index = DisassemblyUtils.binarySearch2(array.length, i -> Integer.compare(array[i], 5));

        assertEquals(2, index); // element found at index 2
    }

    @Test
    void testBinarySearch2_NotFound_InsertPosition() {
        // Should return a negative value indicating the insertion position
        int[] array = {1, 3, 5, 7, 9};
        int index = DisassemblyUtils.binarySearch2(array.length, i -> Integer.compare(array[i], 4));

        assertEquals(-3, index); // -(insertion position + 1)
    }

    @Test
    void testBinarySearch2_EmptyArray() {
        // Empty array should always return -1
        int index = DisassemblyUtils.binarySearch2(0, i -> 0);

        assertEquals(-1, index); // insertion at position 0
    }

    @Test
    void testBinarySearch2_FoundAtStart() {
        // Should find element at the very beginning
        int[] array = {1, 2, 3};
        int index = DisassemblyUtils.binarySearch2(array.length, i -> Integer.compare(array[i], 1));

        assertEquals(0, index);
    }

    @Test
    void testBinarySearch2_FoundAtEnd() {
        // Should find element at the very end
        int[] array = {1, 2, 3};
        int index = DisassemblyUtils.binarySearch2(array.length, i -> Integer.compare(array[i], 3));

        assertEquals(2, index);
    }
}
