package dev.superko.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Recording state of one chain: the set of touched blocks with their current state ids, a
 * Zobrist-style rolling hash over that map, the history of recorded moments, and the
 * rejected (block, action) list. All positions are in {@code BlockPos.asLong()} packing
 * space and all states are integer state ids ({@code Block.getId}), so this class has no
 * dependency on Minecraft types.
 *
 * <p>Moments are <em>not</em> stored as configuration copies (that cost O(touched) time and
 * memory per change). Instead:
 * <ul>
 *   <li>every recorded moment is indexed by its rolling hash, so a candidate is matched in
 *       O(1) instead of scanning the whole history;</li>
 *   <li>each position keeps a write history (step, value), so the configuration of a past
 *       moment is reconstructed on demand — only when a hash bucket is hit and the full
 *       comparison actually runs.</li>
 * </ul>
 * The matching semantics are unchanged: identical touched key sets, identical values, and
 * the same producing action (position, update context, flags).
 */
final class ChainTracker {
    /**
     * Performance caps (Q10): once exceeded the chain is passed through untouched and a
     * console warning is emitted. Recording a change now costs a few words instead of a
     * configuration copy, so these can be far larger than the original 4096. Not final so
     * tests can shrink them.
     */
    static int maxTouched = 65536;
    static int maxHistory = 65536;

    /**
     * Value returned by {@code touched.get} for positions that are not (yet) touched. State
     * id 0 is a valid state (air), so a separate sentinel is required.
     */
    static final int ABSENT = -1;

    private static final int NO_MOMENT = -1;

    /** Rate limit for cap warnings: one per chain, but at most one per 5s across chains. */
    private static volatile long lastCapWarnNanos = 0L;

    final ChainType type;
    final String origin;

    final Long2IntOpenHashMap touched = new Long2IntOpenHashMap();
    long rollingHash;

    /** pos -> write history, each entry packed as {@code step << 32 | value}, steps ascending. */
    private final Long2ObjectOpenHashMap<LongArrayList> writes = new Long2ObjectOpenHashMap<>();

    private final IntArrayList momentStep = new IntArrayList();
    private final IntArrayList momentSize = new IntArrayList();
    private final LongArrayList momentActor = new LongArrayList();
    private final IntArrayList momentCtx = new IntArrayList();
    private final IntArrayList momentFlags = new IntArrayList();
    /** rolling hash -> indices of the moments with that hash (usually one). */
    private final Long2ObjectOpenHashMap<IntArrayList> momentsByHash = new Long2ObjectOpenHashMap<>();

    private final Long2ObjectOpenHashMap<LongOpenHashSet> rejected = new Long2ObjectOpenHashMap<>();

    private int step = 0;
    private boolean hasRejections = false;
    boolean bypass = false;

    ChainTracker(ChainType type, String origin) {
        this.type = type;
        this.origin = origin;
        this.touched.defaultReturnValue(ABSENT);
    }

    // ---- recording ----

    /**
     * Records a confirmed change: updates the touched map and the rolling hash, appends the
     * position's write history and, unless the chain has blown its caps, indexes a new
     * moment for later comparisons.
     */
    void record(long pos, int newStateId, int ctx, int flags) {
        step++;
        int prev = touched.put(pos, newStateId);
        rollingHash ^= hashOf(pos, prev) ^ hashOf(pos, newStateId);
        LongArrayList history = writes.get(pos);
        if (history == null) {
            history = new LongArrayList(4);
            writes.put(pos, history);
        }
        history.add(((long) step << 32) | (newStateId & 0xFFFFFFFFL));

        if (bypass) {
            return;
        }
        boolean overCap = momentStep.size() >= maxHistory || touched.size() > maxTouched;
        if (overCap) {
            bypass = true;
            long now = System.nanoTime();
            if (now - lastCapWarnNanos > 5_000_000_000L) {
                lastCapWarnNanos = now;
                SuperkoLog.warn("[Superko] Chain cap exceeded (" + type.label
                        + (origin.isEmpty() ? "" : " in " + origin)
                        + ", " + touched.size() + " touched blocks, " + momentStep.size() + " moments); "
                        + "the rest of this chain is passed through unjudged.");
            }
            return;
        }
        int moment = momentStep.size();
        momentStep.add(step);
        momentSize.add(touched.size());
        momentActor.add(pos);
        momentCtx.add(ctx);
        momentFlags.add(flags);
        momentsByHash.computeIfAbsent(rollingHash, k -> new IntArrayList(2)).add(moment);
    }

    int momentCount() {
        return momentStep.size();
    }

    // ---- matching ----

    boolean isRejected(long pos, long actionKey) {
        if (!hasRejections) {
            return false;
        }
        LongOpenHashSet set = rejected.get(pos);
        return set != null && set.contains(actionKey);
    }

    void addRejected(long pos, long actionKey) {
        hasRejections = true;
        rejected.computeIfAbsent(pos, k -> new LongOpenHashSet(4)).add(actionKey);
    }

    /**
     * Looks for a recorded moment whose configuration equals the candidate (the touched map
     * with {@code pos} set to {@code newId}) and whose producing action equals this one.
     *
     * @return the matching moment index for logging, or -1 when the change is not a superko
     */
    int findMatchingMoment(long pos, int newId, int ctx, int flags) {
        if (bypass) {
            return NO_MOMENT;
        }
        int candidateSize = touched.size() + (touched.containsKey(pos) ? 0 : 1);
        long candidateHash = rollingHash ^ hashOf(pos, touched.get(pos)) ^ hashOf(pos, newId);
        IntArrayList bucket = momentsByHash.get(candidateHash);
        if (bucket == null) {
            return NO_MOMENT;
        }
        for (int i = 0; i < bucket.size(); i++) {
            int moment = bucket.getInt(i);
            if (momentSize.getInt(moment) != candidateSize) {
                continue;
            }
            if (!matchesMoment(moment, pos, newId)) {
                continue;
            }
            if (momentActor.getLong(moment) == pos
                    && momentCtx.getInt(moment) == ctx
                    && momentFlags.getInt(moment) == flags) {
                return moment;
            }
        }
        return NO_MOMENT;
    }

    /**
     * Full comparison of the candidate against the configuration recorded after the given
     * moment. Requires identical key sets — never compares on the intersection (Q3).
     */
    private boolean matchesMoment(int moment, long pos, int newId) {
        int atStep = momentStep.getInt(moment);
        for (Long2IntMap.Entry e : touched.long2IntEntrySet()) {
            long key = e.getLongKey();
            if (key == pos) {
                continue;
            }
            if (valueAt(key, atStep) != e.getIntValue()) {
                return false;
            }
        }
        return valueAt(pos, atStep) == newId;
    }

    /** Value of a position right after the given step, or {@link #ABSENT} if untouched then. */
    private int valueAt(long key, int atStep) {
        LongArrayList history = writes.get(key);
        if (history == null) {
            return ABSENT;
        }
        if ((int) (history.getLong(0) >>> 32) > atStep) {
            return ABSENT;
        }
        int lo = 0;
        int hi = history.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if ((int) (history.getLong(mid) >>> 32) <= atStep) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return (int) history.getLong(lo);
    }

    // ---- hashing ----

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
