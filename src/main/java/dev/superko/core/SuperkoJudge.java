package dev.superko.core;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * The judgment core. Owns the per-thread chain stack (a passenger's tick legitimately
 * nests inside its vehicle's chain, everything else must not nest), the pending-record
 * stack for in-flight setBlock calls, and the update-context tag stack. All world access
 * is kept in the hooks layer; this class works purely on packed positions and state ids.
 */
public final class SuperkoJudge {
    /** Global switch, flipped by the config / command. */
    public static volatile boolean enabled = true;

    /** Cumulative counters for /superko status diagnostics. */
    public static volatile long chainsStarted = 0;
    public static volatile long judgedSetBlocks = 0;
    public static volatile long rejectedSetBlocks = 0;
    public static volatile long recordedSetBlocks = 0;
    /** Chains that were passed through unjudged because they exceeded a cost cap. */
    public static volatile long bypassedChains = 0;
    /** Touched/snapshot counts of the most recently ended chain (diagnostics). */
    public static volatile int lastChainTouched = 0;
    public static volatile int lastChainHistory = 0;

    private static final ThreadLocal<ArrayDeque<Scope>> CHAINS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<CtxStack> CONTEXTS = ThreadLocal.withInitial(CtxStack::new);

    private SuperkoJudge() {
    }

    static final class Scope {
        final ChainType type;
        final String origin;
        ChainTracker tracker;

        Scope(ChainType type, String origin) {
            this.type = type;
            this.origin = origin;
        }
    }

    static final class CtxStack {
        int[] values = new int[8];
        int size = 0;

        void push(int v) {
            if (size == values.length) {
                values = Arrays.copyOf(values, size * 2);
            }
            values[size++] = v;
        }

        int top() {
            return size == 0 ? UpdateContext.SELF : values[size - 1];
        }

        void reset() {
            size = 0;
        }
    }

    // ---- chain boundaries (called from mixins) ----

    public static void beginChain(ChainType type, String origin) {
        ArrayDeque<Scope> stack = CHAINS.get();
        if (!stack.isEmpty()) {
            if (type == ChainType.ENTITY_PASSENGER) {
                // passengers tick inside their vehicle's tickNonPassenger call stack
                stack.push(new Scope(type, origin));
                return;
            }
            // A previous chain start was aborted before its RETURN (e.g. an update
            // suppression exception unwound through it) — discard the stale state.
            stack.clear();
            CONTEXTS.get().reset();
        }
        stack.push(new Scope(type, origin));
        chainsStarted++;
    }

    public static void endChain() {
        ArrayDeque<Scope> stack = CHAINS.get();
        if (!stack.isEmpty()) {
            Scope scope = stack.pop();
            if (scope.tracker != null) {
                lastChainTouched = scope.tracker.touched.size();
                lastChainHistory = scope.tracker.momentCount();
            }
        }
        CONTEXTS.get().reset();
    }

    // ---- update context tagging (called from mixins) ----

    public static void pushContext(int ctx) {
        CONTEXTS.get().push(ctx);
    }

    public static void popContext() {
        CtxStack s = CONTEXTS.get();
        if (s.size > 0) {
            s.size--;
        }
    }

    public static int currentContext() {
        return CONTEXTS.get().top();
    }

    // ---- setBlock judgment (called from the Level.setBlock hook) ----

    /**
     * Called at the HEAD of a server-side {@code Level.setBlock}. The caller has already
     * checked enabled/client-side/exemptions.
     *
     * @return true if the change must be rejected (the caller cancels the setBlock)
     */
    public static boolean beforeSetBlock(long pos, int oldId, int newId, int flags) {
        Scope scope = CHAINS.get().peek();
        if (scope == null) {
            return false;
        }
        if (oldId == newId) {
            return false; // no state change: not an action (Q9)
        }
        if (scope.tracker == null) {
            scope.tracker = new ChainTracker(scope.type, scope.origin);
        }
        ChainTracker t = scope.tracker;
        if (t.bypass) {
            return false; // caps blown: pass through unrecorded and unjudged
        }
        judgedSetBlocks++;
        int ctx = CONTEXTS.get().top();
        if ((flags & 1) == 0 && (flags & 16) != 0) {
            // Q8: flag sets like 2|16 (structure placement) emit no neighbor/shape updates,
            // so they cannot form an instantaneous loop. They are still recorded (by the
            // write hook), but never rejected.
            if (SuperkoLog.isDebug()) {
                SuperkoLog.debug(trace(pos, oldId, newId, ctx, flags, t, "judge: record-only (no update flags)"));
            }
            return false;
        }
        long actionKey = UpdateContext.packActionKey(ctx, flags, newId);
        if (t.isRejected(pos, actionKey)) {
            rejectedSetBlocks++;
            if (SuperkoLog.isDebug()) {
                SuperkoLog.debug(trace(pos, oldId, newId, ctx, flags, t, "judge: reject (rejected list)"));
            }
            return true; // already reported when it was first rejected
        }
        // Identical post-change configuration found in the history: reject only when the
        // very same action (same block, same update context, same flags) produced that
        // earlier moment; different actions on an identical configuration may still
        // diverge (§2.3).
        int moment = t.findMatchingMoment(pos, newId, ctx, flags);
        if (moment >= 0) {
            t.addRejected(pos, actionKey);
            rejectedSetBlocks++;
            if (SuperkoLog.isDebug()) {
                SuperkoLog.debug(trace(pos, oldId, newId, ctx, flags, t, "judge: reject (superko, moment #" + moment + ")"));
            }
            logReject(t, pos, newId, ctx, flags, moment);
            return true;
        }
        if (SuperkoLog.isDebug()) {
            SuperkoLog.debug(trace(pos, oldId, newId, ctx, flags, t, "judge: pass"));
        }
        return false;
    }

    /**
     * Called by the write hook right after a judged setBlock landed in the world. The
     * context is taken from the HEAD-phase marker (see the hooks layer), not from the
     * current tag stack, to stay immune to post-write tag changes.
     */
    public static void afterSetBlock(long pos, int newStateId, int flags) {
        afterSetBlock(pos, newStateId, flags, CONTEXTS.get().top());
    }

    public static void afterSetBlock(long pos, int newStateId, int flags, int ctx) {
        Scope scope = CHAINS.get().peek();
        if (scope == null || scope.tracker == null) {
            return; // change happened outside a judged chain
        }
        ChainTracker t = scope.tracker;
        if (t.bypass) {
            return;
        }
        recordedSetBlocks++;
        if (SuperkoLog.isDebug()) {
            SuperkoLog.debug("[Superko][debug] record (" + unpackX(pos) + ", " + unpackY(pos) + ", " + unpackZ(pos) + ")"
                    + " -> " + newStateId + " ctx=" + SuperkoLog.contextName(ctx) + " flags=" + flags
                    + " during " + t.type.label + " chain (touched=" + t.touched.size()
                    + ", moments=" + t.momentCount() + ")");
        }
        t.record(pos, newStateId, ctx, flags);
    }

    // ---- helpers ----

    private static String trace(long pos, int oldId, int newId, int ctx, int flags, ChainTracker t, String decision) {
        return "[Superko][debug] setBlock (" + unpackX(pos) + ", " + unpackY(pos) + ", " + unpackZ(pos) + ")"
                + " " + oldId + "->" + newId
                + " ctx=" + SuperkoLog.contextName(ctx) + " flags=" + flags
                + " during " + t.type.label + " chain: " + decision;
    }

    private static void logReject(ChainTracker t, long pos, int newId, int ctx, int flags, int moment) {
        SuperkoLog.intervention("[Superko] Rejected setBlock at (" + unpackX(pos) + ", " + unpackY(pos) + ", " + unpackZ(pos) + ")"
                + (t.origin.isEmpty() ? "" : " [" + t.origin + "]")
                + " during " + t.type.label + " chain"
                + ": ctx=" + SuperkoLog.contextName(ctx) + ", flags=" + flags + ", newStateId=" + newId
                + " — would recreate the configuration of moment #" + moment + " (same action); superko (global sameness) detected.");
    }

    /** Vanilla BlockPos packing: x = bits 38-63, z = bits 12-37, y = bits 0-11. */
    public static int unpackX(long pos) {
        return (int) (pos >> 38);
    }

    public static int unpackY(long pos) {
        return (int) (pos << 52 >> 52);
    }

    public static int unpackZ(long pos) {
        return (int) (pos << 26 >> 38);
    }
}
