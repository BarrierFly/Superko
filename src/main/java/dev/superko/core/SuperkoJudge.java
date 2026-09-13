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

    private static final ThreadLocal<ArrayDeque<Scope>> CHAINS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<Pending>> PENDINGS = ThreadLocal.withInitial(ArrayDeque::new);
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

    /**
     * A setBlock attempt that passed judgment and whose result is not yet known. Kept
     * alongside the call and committed once the change is confirmed to have landed.
     */
    public static final class Pending {
        public final long pos;
        public final int newStateId;
        public final int flags;
        final int ctx;

        Pending(long pos, int newStateId, int flags, int ctx) {
            this.pos = pos;
            this.newStateId = newStateId;
            this.flags = flags;
            this.ctx = ctx;
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
            PENDINGS.get().clear();
            CONTEXTS.get().reset();
        }
        stack.push(new Scope(type, origin));
    }

    public static void endChain() {
        ArrayDeque<Scope> stack = CHAINS.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
        PENDINGS.get().clear();
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
        int ctx = CONTEXTS.get().top();
        if ((flags & 1) == 0 && (flags & 16) != 0) {
            // Q8: flag sets like 2|16 (structure placement) emit no neighbor/shape updates,
            // so they cannot form an instantaneous loop. Record them for snapshot
            // integrity, but never reject based on them.
            PENDINGS.get().push(new Pending(pos, newId, flags, ctx));
            return false;
        }
        long actionKey = UpdateContext.packActionKey(ctx, flags, newId);
        if (t.isRejected(pos, actionKey)) {
            return true; // already reported when it was first rejected
        }
        long candHash = t.rollingHash ^ ChainTracker.hashOf(pos, t.touched.get(pos)) ^ ChainTracker.hashOf(pos, newId);
        int historySize = t.history.size();
        for (int i = 0; i < historySize; i++) {
            Snapshot snap = t.history.get(i);
            if (snap.hash != candHash) {
                continue;
            }
            if (!t.matchesSnapshot(snap, pos, newId)) {
                continue;
            }
            // Identical post-change configuration. Reject only when the very same action
            // (same block, same update context, same flags) produced that earlier moment;
            // different actions on an identical configuration may still diverge (§2.3).
            if (snap.actorPos == pos && snap.ctx == ctx && snap.flags == flags) {
                t.addRejected(pos, actionKey);
                logReject(t, pos, newId, ctx, flags, i);
                return true;
            }
        }
        PENDINGS.get().push(new Pending(pos, newId, flags, ctx));
        return false;
    }

    /**
     * Pops the pending record of a completed setBlock call, or null when the call was not
     * judged (outside a chain, no-op, exempt, ...).
     */
    public static Pending popPending() {
        ArrayDeque<Pending> q = PENDINGS.get();
        return q.isEmpty() ? null : q.pop();
    }

    /**
     * Commits a confirmed change (the hook verified the world state equals the requested
     * one). Stale pendings from aborted calls are dropped by the pos/id identity check.
     */
    public static void commitPending(Pending p, long pos, int newStateId) {
        if (p.pos != pos || p.newStateId != newStateId) {
            return;
        }
        Scope scope = CHAINS.get().peek();
        if (scope == null || scope.tracker == null) {
            return;
        }
        scope.tracker.record(p.pos, p.newStateId, p.ctx, p.flags);
    }

    // ---- helpers ----

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
