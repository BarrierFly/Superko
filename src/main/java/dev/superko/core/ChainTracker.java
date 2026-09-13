package dev.superko.core;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.ArrayList;

/**
 * Recording state of one chain: the set of touched blocks with their current state ids, a
 * Zobrist-style rolling hash over that map, the history of post-change snapshots and the
 * rejected (block, action) list. All positions are in {@code BlockPos.asLong()} packing
 * space and all states are integer state ids ({@code Block.getId}), so this class has no
 * dependency on Minecraft types.
 */
final class ChainTracker {
    /**
     * Performance caps (Q10): once exceeded the chain is passed through untouched and a
     * console warning is emitted. Not final so tests can shrink them.
     */
    static int maxTouched = 4096;
    static int maxHistory = 4096;
    static int maxSnapshotEntries = 1 << 20;

    /**
     * Value returned by {@code touched.get} for positions that are not (yet) touched. State
     * id 0 is a valid state (air), so a separate sentinel is required.
     */
    static final int ABSENT = -1;

    /** Rate limit for cap warnings: one per chain, but at most one per 5s across chains. */
    private static volatile long lastCapWarnNanos = 0L;

    final ChainType type;
    final String origin;

    final Long2IntOpenHashMap touched = new Long2IntOpenHashMap();
    long rollingHash;
    final ArrayList<Snapshot> history = new ArrayList<>();
    final Long2ObjectOpenHashMap<LongOpenHashSet> rejected = new Long2ObjectOpenHashMap<>();

    int storedEntries = 0;
    boolean bypass = false;

    ChainTracker(ChainType type, String origin) {
        this.type = type;
        this.origin = origin;
        this.touched.defaultReturnValue(ABSENT);
    }

    void touch(long pos, int newStateId) {
        int prev = touched.put(pos, newStateId);
        rollingHash ^= hashOf(pos, prev) ^ hashOf(pos, newStateId);
    }

    /**
     * Records a confirmed change: updates the touched map and appends a snapshot of the
     * resulting configuration, unless the chain has blown its caps.
     */
    void record(long pos, int newStateId, int ctx, int flags) {
        touch(pos, newStateId);
        if (bypass) {
            return;
        }
        boolean overCap = history.size() >= maxHistory
                || touched.size() > maxTouched
                || storedEntries + touched.size() > maxSnapshotEntries;
        if (overCap) {
            bypass = true;
            history.clear();
            long now = System.nanoTime();
            if (now - lastCapWarnNanos > 5_000_000_000L) {
                lastCapWarnNanos = now;
                SuperkoLog.warn("[Superko] Chain cap exceeded (" + type.label
                        + (origin.isEmpty() ? "" : " in " + origin)
                        + ", " + touched.size() + " touched blocks); "
                        + "the rest of this chain is passed through unjudged.");
            }
            return;
        }
        Snapshot snap = new Snapshot(rollingHash, new Long2IntOpenHashMap(touched), pos, ctx, flags);
        history.add(snap);
        storedEntries += snap.states.size();
    }

    boolean isRejected(long pos, long actionKey) {
        LongOpenHashSet set = rejected.get(pos);
        return set != null && set.contains(actionKey);
    }

    void addRejected(long pos, long actionKey) {
        rejected.computeIfAbsent(pos, k -> new LongOpenHashSet(4)).add(actionKey);
    }

    /**
     * Full comparison of the candidate configuration (the touched map with {@code pos}
     * updated to {@code newId}) against a historical snapshot. Requires identical key sets
     * and values — never compares on the intersection (Q3).
     */
    boolean matchesSnapshot(Snapshot snap, long pos, int newId) {
        int candidateSize = touched.size() + (touched.containsKey(pos) ? 0 : 1);
        if (snap.states.size() != candidateSize) {
            return false;
        }
        for (Long2IntMap.Entry e : snap.states.long2IntEntrySet()) {
            long key = e.getLongKey();
            int current = key == pos ? newId : touched.get(key);
            if (current != e.getIntValue()) {
                return false;
            }
        }
        return true;
    }

    static long hashOf(long pos, int state) {
        return mix(pos ^ (0x9E3779B97F4A7C15L * state));
    }

    private static long mix(long x) {
        x ^= (x >>> 33);
        x *= 0xFF51AFD7ED558CCDL;
        x ^= (x >>> 33);
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= (x >>> 33);
        return x;
    }
}
