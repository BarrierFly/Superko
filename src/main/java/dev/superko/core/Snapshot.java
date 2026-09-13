package dev.superko.core;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

/**
 * One recorded moment of a chain: the full pos → stateId map of every touched block at
 * that instant, plus the action (position, update context, flags) that produced it.
 * A full copy is kept so comparisons require identical key sets (Q3: never compare on
 * the intersection). Memory is bounded by {@link ChainTracker#maxSnapshotEntries}.
 */
final class Snapshot {
    final long hash;
    final Long2IntOpenHashMap states;
    final long actorPos;
    final int ctx;
    final int flags;

    Snapshot(long hash, Long2IntOpenHashMap states, long actorPos, int ctx, int flags) {
        this.hash = hash;
        this.states = states;
        this.actorPos = actorPos;
        this.ctx = ctx;
        this.flags = flags;
    }
}
