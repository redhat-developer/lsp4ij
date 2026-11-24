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
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.ThreadEventArgumentsReason;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether each thread of a DAP session runs, is stopped, or has exited, as the debug adapter's events and the
 * session's own requests which resume the program say:
 * <ul>
 *     <li>'stopped' stops its thread, or all threads with {@code allThreadsStopped: true};</li>
 *     <li>'continued' runs its thread with {@code allThreadsContinued: false}, and all threads otherwise;</li>
 *     <li>a resume or a step of the session runs all threads, or only the thread which steps for a single-thread step
 *     ('singleThread'), and gives them their run states back when the debug adapter refuses it;</li>
 *     <li>'thread' with the reason 'exited' ends its thread, and with 'started' runs it.</li>
 * </ul>
 * A thread which none of them named has the run state of all threads, which is running until the debug adapter says
 * otherwise, as in VS Code: the program runs once it is launched or attached. A thread which exited stays so when all
 * threads stop or run, until the debug adapter no longer reports it ({@link #retain(Collection)}).
 * <p>
 * The run states are immutable: each change gives new ones, which a {@link DAPClient} keeps per DAP session, since the
 * sessions of a tree ('startDebugging') number their threads each. The events arrive on the thread which reads the
 * messages of the debug adapter, the requests are sent from other threads, and the Threads tab reads the run states on
 * the EDT, so that the client swaps them atomically.
 * <p>
 * Each change has a version, which a stop takes as it arrives: {@link #leftStopSince(int, int)} tells whether a thread
 * left that stop since, so that the Frames view no longer shows the frames of its stop.
 */
public final class DAPThreadRunStates {

    /**
     * The run states of a DAP session whose debug adapter hasn't said anything of its threads yet: all of them run.
     */
    public static final @NotNull DAPThreadRunStates INITIAL =
            new DAPThreadRunStates(0, new Entry(DAPThreadRunState.RUNNING, 0), Map.of());

    /**
     * The run state of a thread, and the version of the run states which gave it.
     */
    private record Entry(@NotNull DAPThreadRunState state, int version) {
    }

    private final int version;
    // The run state of the threads which have none of their own
    private final @NotNull Entry allThreads;
    // The run states of the threads which were named alone since all threads last changed, and of those which exited
    private final @NotNull Map<Integer, Entry> threads;

    private DAPThreadRunStates(int version, @NotNull Entry allThreads, @NotNull Map<Integer, Entry> threads) {
        this.version = version;
        this.allThreads = allThreads;
        this.threads = Map.copyOf(threads);
    }

    /**
     * Returns the run state of the given thread.
     *
     * @param threadId the DAP thread.
     * @return the run state of the given thread.
     */
    public @NotNull DAPThreadRunState getState(int threadId) {
        return getEntry(threadId).state();
    }

    /**
     * Returns the version of these run states, which each change increases. A stop takes it as it arrives, for
     * {@link #leftStopSince(int, int)}.
     *
     * @return the version of these run states.
     */
    public int getVersion() {
        return version;
    }

    /**
     * Returns whether the given thread left a stop since the run states had the given version: it runs, as when it
     * continued alone, or it exited. A thread which already ran at that version, such as one which a stop without
     * {@code allThreadsStopped} didn't stop, didn't leave it.
     *
     * @param version  the version of the run states when the program stopped.
     * @param threadId the DAP thread.
     * @return true if the thread left the stop since that version and false otherwise.
     */
    public boolean leftStopSince(int version, int threadId) {
        Entry entry = getEntry(threadId);
        return entry.state() != DAPThreadRunState.STOPPED && entry.version() > version;
    }

    /**
     * Returns the run states once the debug adapter said that the program stopped: its thread, or all threads with
     * {@code allThreadsStopped: true}.
     *
     * @param args the 'stopped' event.
     * @return the new run states.
     */
    @NotNull DAPThreadRunStates stopped(@NotNull StoppedEventArguments args) {
        if (Boolean.TRUE.equals(args.getAllThreadsStopped())) {
            return withAllThreads(DAPThreadRunState.STOPPED);
        }
        Integer threadId = args.getThreadId();
        return threadId != null ? withThread(threadId, DAPThreadRunState.STOPPED) : this;
    }

    /**
     * Returns the run states once the debug adapter said that the program continued: all threads, unless it says that
     * only its thread continued ({@code allThreadsContinued: false}).
     *
     * @param args the 'continued' event.
     * @return the new run states.
     */
    @NotNull DAPThreadRunStates continued(@NotNull ContinuedEventArguments args) {
        return Boolean.FALSE.equals(args.getAllThreadsContinued()) ?
                withThread(args.getThreadId(), DAPThreadRunState.RUNNING) :
                withAllThreads(DAPThreadRunState.RUNNING);
    }

    /**
     * Returns the run states once the session resumed the program, with Resume, a step, or a breakpoint which doesn't
     * suspend: all threads run, or only the given thread for a request with 'singleThread'.
     *
     * @param threadId     the DAP thread which the request resumes.
     * @param singleThread true if the request resumes only that thread, and false if it resumes all threads.
     * @return the new run states.
     */
    @NotNull DAPThreadRunStates resumed(int threadId, boolean singleThread) {
        return singleThread ?
                withThread(threadId, DAPThreadRunState.RUNNING) :
                withAllThreads(DAPThreadRunState.RUNNING);
    }

    /**
     * Returns the run states once the debug adapter refused a request of the session which resumed the program: the
     * threads which {@link #resumed(int, boolean)} showed running have the run states which they had before the request
     * again, unless a newer event changed them since, as when a thread exited. When all threads stopped or continued
     * since, as the debug adapter said, nothing is restored.
     *
     * @param before  the run states before the request.
     * @param resumed the run states which {@link #resumed(int, boolean)} gave for the request.
     * @return the new run states, or these when all threads changed since the request.
     */
    @NotNull DAPThreadRunStates resumeRefused(@NotNull DAPThreadRunStates before, @NotNull DAPThreadRunStates resumed) {
        if (allThreads.version() > resumed.version) {
            return this;
        }
        // The run states of before, but those which changed after the request
        Map<Integer, Entry> restored = new HashMap<>(before.threads);
        threads.forEach((threadId, entry) -> {
            if (entry.version() > resumed.version) {
                restored.put(threadId, entry);
            }
        });
        return new DAPThreadRunStates(version + 1, before.allThreads, restored);
    }

    /**
     * Returns the run states once the debug adapter said that a thread started, which runs, or exited.
     *
     * @param args the 'thread' event.
     * @return the new run states, or these for another reason.
     */
    @NotNull DAPThreadRunStates thread(@NotNull ThreadEventArguments args) {
        if (ThreadEventArgumentsReason.EXITED.equals(args.getReason())) {
            return withThread(args.getThreadId(), DAPThreadRunState.EXITED);
        }
        if (ThreadEventArgumentsReason.STARTED.equals(args.getReason())) {
            return withThread(args.getThreadId(), DAPThreadRunState.RUNNING);
        }
        return this;
    }

    /**
     * Returns the run states without those of the threads which the debug adapter no longer reports, such as the
     * threads which exited: the run states of a long session don't grow with each thread which exits. Only the run
     * states which haven't changed since the given version go. The client gives the older of:
     * <ul>
     *     <li>the version when the 'threads' request was sent: the run state of a thread which changed after it, as when
     *     a thread which started meanwhile stopped, stays, although the answer doesn't list it;</li>
     *     <li>the version of the stop where the session is suspended: the run state of a thread which left that stop
     *     since, as when it exited, stays while the stacks of the stop show it ({@link #leftStopSince(int, int)}).</li>
     * </ul>
     *
     * @param threadIds        the DAP threads which the debug adapter reports.
     * @param keepChangedAfter the version after which the run states of the threads which aren't reported changed, and
     *                         stay.
     * @return the run states without those of the threads which the answer no longer lists, or these when there are
     * none.
     */
    @NotNull DAPThreadRunStates retain(@NotNull Collection<Integer> threadIds, int keepChangedAfter) {
        Set<Integer> reported = Set.copyOf(threadIds);
        Map<Integer, Entry> retained = threads.entrySet().stream()
                .filter(thread -> reported.contains(thread.getKey()) || thread.getValue().version() > keepChangedAfter)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return retained.size() == threads.size() ? this : new DAPThreadRunStates(version, allThreads, retained);
    }

    private @NotNull Entry getEntry(int threadId) {
        return threads.getOrDefault(threadId, allThreads);
    }

    private @NotNull DAPThreadRunStates withThread(int threadId, @NotNull DAPThreadRunState state) {
        int next = version + 1;
        Map<Integer, Entry> changed = new HashMap<>(threads);
        changed.put(threadId, new Entry(state, next));
        return new DAPThreadRunStates(next, allThreads, changed);
    }

    private @NotNull DAPThreadRunStates withAllThreads(@NotNull DAPThreadRunState state) {
        int next = version + 1;
        // A thread which exited doesn't stop or run again
        Map<Integer, Entry> exited = threads.entrySet().stream()
                .filter(thread -> thread.getValue().state() == DAPThreadRunState.EXITED)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new DAPThreadRunStates(next, new Entry(state, next), exited);
    }
}
