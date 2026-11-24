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

import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stop which a 'continued' event continued while it was handled is over: a 'continued' event of all threads, or of
 * the stopped thread, which arrived after the 'stopped' event. A 'continued' event which arrived before it, or which
 * continued another thread alone, doesn't end it.
 */
public class DAPContinuedEventsTest {

    private static final int STOPPED_THREAD = 1;
    private static final int OTHER_THREAD = 2;

    private final DAPContinuedEvents events = new DAPContinuedEvents();

    @Test
    void aStopIsNotContinuedWithoutAContinuedEvent() {
        int stop = events.mark();

        assertFalse(events.continuedSince(stop, STOPPED_THREAD));
    }

    @Test
    void aContinuedEventOfAllThreadsContinuesAStop() {
        int stop = events.mark();
        events.continued(event(OTHER_THREAD, null));

        assertTrue(events.continuedSince(stop, STOPPED_THREAD));
        assertTrue(events.continuedSince(stop, OTHER_THREAD));
    }

    @Test
    void aContinuedEventWhichSaysThatAllThreadsContinuedContinuesAStop() {
        int stop = events.mark();
        events.continued(event(OTHER_THREAD, true));

        assertTrue(events.continuedSince(stop, STOPPED_THREAD));
    }

    @Test
    void aContinuedEventOfTheStoppedThreadAloneContinuesItsStop() {
        int stop = events.mark();
        events.continued(event(STOPPED_THREAD, false));

        assertTrue(events.continuedSince(stop, STOPPED_THREAD));
    }

    @Test
    void aContinuedEventOfAnotherThreadAloneDoesNotContinueAStop() {
        int stop = events.mark();
        events.continued(event(OTHER_THREAD, false));

        assertFalse(events.continuedSince(stop, STOPPED_THREAD));
        assertTrue(events.continuedSince(stop, OTHER_THREAD));
    }

    @Test
    void aContinuedEventBeforeTheStopDoesNotContinueIt() {
        // as when a step typed in the Debug console continues the program, and it stops again
        events.continued(event(STOPPED_THREAD, null));
        events.continued(event(STOPPED_THREAD, false));
        int stop = events.mark();

        assertFalse(events.continuedSince(stop, STOPPED_THREAD));
    }

    @Test
    void aContinuedEventOfAnotherThreadAloneAfterTheStopLeavesAnEarlierOneOfTheStoppedThreadBehind() {
        events.continued(event(STOPPED_THREAD, false));
        int stop = events.mark();
        events.continued(event(OTHER_THREAD, false));

        assertFalse(events.continuedSince(stop, STOPPED_THREAD));
    }

    private static ContinuedEventArguments event(int threadId, @Nullable Boolean allThreadsContinued) {
        var args = new ContinuedEventArguments();
        args.setThreadId(threadId);
        args.setAllThreadsContinued(allThreadsContinued);
        return args;
    }
}
