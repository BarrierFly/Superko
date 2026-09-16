package dev.superko.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Independent specification model of the superko rule, deliberately implemented in the most
 * naive way possible (a full configuration copy per moment, linear comparison, no hashing,
 * no shortcuts) and sharing no code with the production judge. Randomized agreement tests
 * drive both and require identical decisions, so a data-structure change that alters the
 * semantics cannot slip through.
 *
 * <p>Keep this file dumb: do not refactor it to reuse implementation helpers, or it stops
 * being an independent check.
 */
final class SpecificationOracle {
    private final Map<Long, Integer> world = new HashMap<>();
    private final List<Map<Long, Integer>> moments = new ArrayList<>();
    private final List<long[]> producers = new ArrayList<>(); // pos, ctx, flags
    private final Set<Long> rejected = new HashSet<>();
    private boolean active = false;

    void begin() {
        active = true;
    }

    void end() {
        active = false;
        world.clear();
        moments.clear();
        producers.clear();
        rejected.clear();
    }

    int worldValue(long pos) {
        return world.getOrDefault(pos, 0);
    }

    /** Copy of the model's world, for comparing against the judge's own world tracking. */
    Map<Long, Integer> worldView() {
        return new HashMap<>(world);
    }

    /** @return true when the specification rejects the change */
    boolean before(long pos, int oldId, int newId, int flags, int ctx) {
        if (!active || oldId == newId) {
            return false;
        }
        if ((flags & 1) == 0 && (flags & 16) != 0) {
            return false; // Q8: recorded, never rejected
        }
        long actionKey = (pos * 31) ^ UpdateContext.packActionKey(ctx, flags, newId);
        if (rejected.contains(actionKey)) {
            return true;
        }
        Map<Long, Integer> candidate = new HashMap<>(world);
        candidate.put(pos, newId);
        for (int i = 0; i < moments.size(); i++) {
            if (!moments.get(i).equals(candidate)) {
                continue;
            }
            long[] producer = producers.get(i);
            if (producer[0] == pos && producer[1] == ctx && producer[2] == flags) {
                rejected.add(actionKey);
                return true;
            }
        }
        return false;
    }

    /** Records a change that was performed (call only for changes that really happened). */
    void after(long pos, int newId, int ctx, int flags) {
        if (!active) {
            return;
        }
        world.put(pos, newId);
        moments.add(new HashMap<>(world));
        producers.add(new long[]{pos, ctx, flags});
    }
}
