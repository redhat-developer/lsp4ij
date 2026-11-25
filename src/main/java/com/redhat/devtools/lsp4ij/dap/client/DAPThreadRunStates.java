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
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Whether each thread of a DAP session runs, is stopped, or has exited, as the debug adapter's events and the
 * session's own requests which resume the program say:
 * <ul>
 *     <li>'stopped' stops its thread, or all threads with {@code allThreadsStopped: true};</li>
 *     <li>'continued' runs its thread with {@code allThreadsContinued: false}, and all threads otherwise;</li>
 *     <li>a resume or a step of the session runs all threads, or only the thread which steps for a single-thread step
 *     ('singleThread'), and gives them their run states back when the debug adapter refuses it, in whichever order
 *     it refuses several ({@link #resumeRefused});</li>
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
            new DAPThreadRunStates(0, new Entry(DAPThreadRunState.RUNNING, 0, false), Map.of(), Map.of());

    /**
     * The run state of a thread, the version of the run states which gave it, and whether a request of the session
     * which resumes the program gave it ({@link #resumed(int, boolean)}), rather than an event of the debug adapter:
     * the version is then that of the request, which a refusal undoes.
     */
    private record Entry(@NotNull DAPThreadRunState state, int version, boolean resumed) {
    }

    private final int version;
    // The run state of the threads which have none of their own
    private final @NotNull Entry allThreads;
    // The run states of the threads which were named alone since all threads last changed, and of those which exited
    private final @NotNull Map<Integer, Entry> threads;
    // The run states before each request which the debug adapter refused, by the version of the request, kept while a
    // newer request whose change these run states show may be refused too: the run states before that newer request
    // may still show what the refused one changed, which the refusal of the newer one then undoes as well
    private final @NotNull Map<Integer, DAPThreadRunStates> refused;

    private DAPThreadRunStates(int version,
                               @NotNull Entry allThreads,
                               @NotNull Map<Integer, Entry> threads,
                               @NotNull Map<Integer, DAPThreadRunStates> refused) {
        this.version = version;
        this.allThreads = allThreads;
        this.threads = Map.copyOf(threads);
        this.refused = refused.isEmpty() ? Map.of() : withoutUnreachable(refused);
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
            return withAllThreads(DAPThreadRunState.STOPPED, false);
        }
        Integer threadId = args.getThreadId();
        return threadId != null ? withThread(threadId, DAPThreadRunState.STOPPED, false) : this;
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
                withThread(args.getThreadId(), DAPThreadRunState.RUNNING, false) :
                withAllThreads(DAPThreadRunState.RUNNING, false);
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
                withThread(threadId, DAPThreadRunState.RUNNING, true) :
                withAllThreads(DAPThreadRunState.RUNNING, true);
    }

    /**
     * Returns the run states once the debug adapter refused a request of the session which resumed the program: the run
     * states as if the session had sent neither it nor any other request which the debug adapter refused. Each thread
     * which still has the run state that {@link #resumed(int, boolean)} gave it for a refused request has the run state
     * which it had before that request again, and so on back to a run state which an event of the debug adapter gave,
     * or a request which it accepted or hasn't answered yet. So:
     * <ul>
     *     <li>a newer event, as when all threads stopped or continued, or a thread exited, stays;</li>
     *     <li>what a newer request which the debug adapter hasn't answered yet changed stays, as it may still resume
     *     the program. When the debug adapter refuses it too, the run states before it, which may show what the first
     *     request changed, are restored back to before the first request: two refusals give the same run states in
     *     either order.</li>
     * </ul>
     *
     * @param before  the run states before the request.
     * @param resumed the run states which {@link #resumed(int, boolean)} gave for the request.
     * @return the new run states, or these when the refusal changes nothing, as when all threads stopped or continued
     * since the request.
     */
    @NotNull DAPThreadRunStates resumeRefused(@NotNull DAPThreadRunStates before, @NotNull DAPThreadRunStates resumed) {
        Map<Integer, DAPThreadRunStates> refused = new HashMap<>(this.refused);
        // Without the refused requests which it had: these have each one whose change it may still show
        refused.put(resumed.version,
                new DAPThreadRunStates(before.version, before.allThreads, before.threads, Map.of()));
        // The run state of all threads before each refused request which gave it, and the threads which had their own
        Entry restoredAllThreads = allThreads;
        Set<Integer> threadIds = new HashSet<>(threads.keySet());
        for (var states = refusedBefore(allThreads, refused); states != null;
             states = refusedBefore(states.allThreads, refused)) {
            restoredAllThreads = states.allThreads;
            threadIds.addAll(states.threads.keySet());
        }
        Map<Integer, Entry> restored = threadIds.stream()
                .collect(Collectors.toMap(Function.identity(), threadId -> restoredEntry(threadId, refused)));
        if (restoredAllThreads.equals(allThreads) && restored.equals(threads)) {
            // Nothing to restore yet: a newer event, or a newer request which the debug adapter hasn't answered yet,
            // changed the threads since
            var kept = new DAPThreadRunStates(version, allThreads, threads, refused);
            return kept.refused.equals(this.refused) ? this : kept;
        }
        return new DAPThreadRunStates(version + 1, restoredAllThreads, restored, refused);
    }

    /**
     * Returns the run states once the debug adapter said that a thread started, which runs, or exited.
     *
     * @param args the 'thread' event.
     * @return the new run states, or these for another reason.
     */
    @NotNull DAPThreadRunStates thread(@NotNull ThreadEventArguments args) {
        if (ThreadEventArgumentsReason.EXITED.equals(args.getReason())) {
            return withThread(args.getThreadId(), DAPThreadRunState.EXITED, false);
        }
        if (ThreadEventArgumentsReason.STARTED.equals(args.getReason())) {
            return withThread(args.getThreadId(), DAPThreadRunState.RUNNING, false);
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
        return retained.size() == threads.size() ? this :
                new DAPThreadRunStates(version, allThreads, retained, refused);
    }

    private @NotNull Entry getEntry(int threadId) {
        return threads.getOrDefault(threadId, allThreads);
    }

    /**
     * Returns the run state of the given thread as if the debug adapter had not been sent the given refused requests:
     * the run state which the thread had before the refused request which gave its run state, and so on back.
     *
     * @param threadId the DAP thread.
     * @param refused  the run states before each refused request, by the version of the request.
     * @return the run state of the given thread which no refused request gave.
     */
    private @NotNull Entry restoredEntry(int threadId, @NotNull Map<Integer, DAPThreadRunStates> refused) {
        Entry entry = getEntry(threadId);
        var before = refusedBefore(entry, refused);
        return before != null ? before.restoredEntry(threadId, refused) : entry;
    }

    /**
     * Returns the run states before the refused request which gave the given run state, if a refused request gave it.
     *
     * @param entry   the run state of a thread, or of all threads.
     * @param refused the run states before each refused request, by the version of the request.
     * @return the run states before the request which gave the given run state, or null if an event of the debug
     * adapter, or a request which it didn't refuse, gave it.
     */
    private static @Nullable DAPThreadRunStates refusedBefore(@NotNull Entry entry,
                                                              @NotNull Map<Integer, DAPThreadRunStates> refused) {
        return entry.resumed() ? refused.get(entry.version()) : null;
    }

    /**
     * Returns the run states before the given refused requests which a later refusal may still restore: only the
     * refusal of a request newer than a refused one goes back to the run states before it, and only of a request whose
     * change these run states show, since a refusal restores only the threads which still have a run state that a
     * refused request gave. Once all threads stopped or continued, as the debug adapter said, there are none.
     *
     * @param refused the run states before each refused request, by the version of the request.
     * @return those which a later refusal may still restore.
     */
    private @NotNull Map<Integer, DAPThreadRunStates> withoutUnreachable(
            @NotNull Map<Integer, DAPThreadRunStates> refused) {
        int newestRequest = Stream.concat(Stream.of(allThreads), threads.values().stream())
                .filter(Entry::resumed)
                .mapToInt(Entry::version)
                .max()
                .orElse(Integer.MIN_VALUE);
        return refused.entrySet().stream()
                .filter(request -> request.getKey() < newestRequest)
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private @NotNull DAPThreadRunStates withThread(int threadId, @NotNull DAPThreadRunState state, boolean resumed) {
        int next = version + 1;
        Map<Integer, Entry> changed = new HashMap<>(threads);
        changed.put(threadId, new Entry(state, next, resumed));
        return new DAPThreadRunStates(next, allThreads, changed, refused);
    }

    private @NotNull DAPThreadRunStates withAllThreads(@NotNull DAPThreadRunState state, boolean resumed) {
        int next = version + 1;
        // A thread which exited doesn't stop or run again
        Map<Integer, Entry> exited = threads.entrySet().stream()
                .filter(thread -> thread.getValue().state() == DAPThreadRunState.EXITED)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new DAPThreadRunStates(next, new Entry(state, next, resumed), exited, refused);
    }
}
