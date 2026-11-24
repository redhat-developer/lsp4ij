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
package com.redhat.devtools.lsp4ij.dap.client;

import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArgumentsCategory;
import org.eclipse.lsp4j.debug.OutputEventArgumentsGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.eclipse.lsp4j.debug.OutputEventArgumentsGroup.END;
import static org.eclipse.lsp4j.debug.OutputEventArgumentsGroup.START;
import static org.eclipse.lsp4j.debug.OutputEventArgumentsGroup.START_COLLAPSED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link DAPOutputGroups} tests: the debug console indents the lines of the group of an 'output' event by its nesting
 * level, as VS Code shows the output of {@code console.group()}. A group's header is at the level the group starts at,
 * and the output of its end at the outer level. Each line is indented where it starts, whether an event gives a part
 * of a line or several lines.
 */
public class DAPOutputGroupsTest {

    private final DAPOutputGroups groups = new DAPOutputGroups();

    @Test
    void outsideAGroup_outputIsPrintedAsSent() {
        assertEquals("Name? ", print(output("Name? ")));
        assertEquals("Ada\nBob", print(output("Ada\nBob")));
    }

    @Test
    void aGroup_indentsItsLinesButNotItsHeader() {
        assertEquals("""
                        Group
                          inside
                        outside
                        """,
                print(output("Group\n", START), output("inside\n"), output("", END), output("outside\n")));
    }

    @Test
    void nestedGroups_indentTheirLinesByTheirLevel() {
        assertEquals("""
                        Outer
                          in outer
                          Inner
                            in inner
                          back in outer
                        outside
                        """,
                print(output("Outer\n", START),
                        output("in outer\n"),
                        output("Inner\n", START),
                        output("in inner\n"),
                        output("", END),
                        output("back in outer\n"),
                        output("", END),
                        output("outside\n")));
    }

    @Test
    void aCollapsedGroup_isIndentedAsAnExpandedOne() {
        assertEquals("""
                        Details
                          hidden in VS Code
                        """,
                print(output("Details\n", START_COLLAPSED), output("hidden in VS Code\n")));
    }

    @Test
    void theOutputOfAnEnd_isAtTheOuterLevel() {
        assertEquals("""
                        Group
                          inside
                        Group done
                        """,
                print(output("Group\n", START), output("inside\n"), output("Group done\n", END)));
    }

    @Test
    void anEndWithoutAGroup_doesNotGoBelowLevel0() {
        assertEquals("""
                        not in a group
                        Group
                          inside
                        """,
                print(output("", END),
                        output("", END),
                        output("not in a group\n"),
                        // at a level below 0, the lines of this group would not be indented
                        output("Group\n", START),
                        output("inside\n")));
    }

    @Test
    void aLineInChunks_isIndentedOnceWhereItStarts() {
        assertEquals("""
                        Count
                          1, 2, 3
                        """,
                print(output("Count\n", START), output("1, "), output("2, "), output("3"), output("\n")));
    }

    @Test
    void aChunkWhichEndsALine_leavesTheNextLineToTheLevelOfItsOwnChunk() {
        assertEquals("""
                        Group
                          inside
                        outside
                        """,
                print(output("Group\n", START), output("ins"), output("ide\n"), output("", END), output("outside\n")));
    }

    @Test
    void multiLineOutput_isIndentedOnEachLineButItsEmptyOnes() {
        assertEquals("Group\n  a\n  b\n\n  c\r\n  d", print(output("Group\n", START), output("a\nb\n\nc\r\nd")));
    }

    @Test
    void aMultiLineHeader_isAtTheLevelOfItsGroupStart() {
        assertEquals("""
                        Outer
                          Inner
                          header
                            inside
                        """,
                print(output("Outer\n", START), output("Inner\nheader", START), output("inside\n")));
    }

    @Test
    void aCarriageReturn_startsTheLineAgain() {
        // a progress which a program rewrites in place
        assertEquals("Progress\n  50%\r  100%\n",
                print(output("Progress\n", START), output("50%\r"), output("100%\n")));
    }

    @Test
    void aGroup_startsAndEndsOnALineOfItsOwn() {
        assertEquals("""
                        Name? Ada
                        Group
                          Age?\s
                        outside
                        """,
                print(output("Name? "), output("Ada"), output("Group", START), output("Age? "), output("", END),
                        output("outside\n")));
    }

    @Test
    void anEmptyHeader_printsNoLine() {
        assertEquals("  inside\n", print(output("", START), output("inside\n")));
    }

    @Test
    void telemetry_isNotIndentedAndDoesNotStartAGroup() {
        assertEquals("""
                        Group
                        {"event":"launch"}
                          inside
                        """,
                print(output("Group\n", START),
                        telemetry("{\"event\":\"launch\"}\n", START),
                        telemetry("", END),
                        output("inside\n")));
    }

    /**
     * Returns what the console prints for the given events, one after the other.
     */
    private @NotNull String print(OutputEventArguments... events) {
        return Arrays.stream(events).map(groups::indent).collect(Collectors.joining());
    }

    private static @NotNull OutputEventArguments output(@NotNull String output) {
        return output(output, null);
    }

    private static @NotNull OutputEventArguments output(@NotNull String output, @Nullable OutputEventArgumentsGroup group) {
        var args = new OutputEventArguments();
        args.setCategory(OutputEventArgumentsCategory.STDOUT);
        args.setOutput(output);
        args.setGroup(group);
        return args;
    }

    private static @NotNull OutputEventArguments telemetry(@NotNull String output, @Nullable OutputEventArgumentsGroup group) {
        var args = output(output, group);
        args.setCategory(OutputEventArgumentsCategory.TELEMETRY);
        return args;
    }
}
