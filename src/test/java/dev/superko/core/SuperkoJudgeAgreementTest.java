package dev.superko.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Randomized agreement test: the production judge and the independent naive specification
 * model must return the same decision for every change of every random chain. This is the
 * safety net for data-structure work on the core — a faster matcher that changes the
 * semantics fails here instead of in a player's contraption.
 */
class SuperkoJudgeAgreementTest {
    private static final int[] FLAG_SETS = {2, 3, 11, 18};

    private record Change(long pos, int newId, int flags, int ctx) {
    }

    private record Agreement(int rejections, int mismatches, String details) {
    }

    @BeforeEach
    @AfterEach
    void cleanState() {
        ChainTracker.maxTouched = 65536;
        ChainTracker.maxHistory = 65536;
        SuperkoJudge.enabled = true;
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "cleanup");
        SuperkoJudge.endChain();
    }

    private static long pack(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | ((y & 0xFFFL));
    }

    @Test
    void randomChainsMatchTheSpecification() {
        int seeds = 400;
        int decisions = 0;
        int rejections = 0;
        for (int seed = 0; seed < seeds; seed++) {
            final int seedNumber = seed;
            Random random = new Random(seed);
            int length = 20 + random.nextInt(380);
            int positionCount = 1 + random.nextInt(5);
            List<Change> changes = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                changes.add(new Change(
                        pack(random.nextInt(positionCount), 64, 0),
                        random.nextInt(4),
                        FLAG_SETS[random.nextInt(FLAG_SETS.length)],
                        random.nextInt(3)));
            }
            Agreement agreement = runBoth(changes);
            decisions += length;
            rejections += agreement.rejections();
            assertEquals(0, agreement.mismatches(), () -> "seed " + seedNumber + "\n" + agreement.details());
        }
        assertTrue(rejections > decisions / 20,
                "sanity: the generated traces should contain a healthy number of rejections, got " + rejections);
    }

    @Test
    void periodicWaveLoopMatchesTheSpecification() {
        // Positions cycling through two values: a real loop with a large touched set,
        // exactly the case the old history scan made expensive.
        int positionCount = 128;
        int length = positionCount * 16;
        List<Change> changes = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            changes.add(new Change(pack(i % positionCount, 64, 0), (i / positionCount) % 2, 3, 1));
        }
        Agreement agreement = runBoth(changes);
        assertEquals(0, agreement.mismatches(), agreement::details);
        // Each round re-creates the configuration of the round before last, so the closing
        // change of a round is rejected (and afterwards short-circuited by the rejected
        // list); the rest of the loop keeps running by design, so this is a few per round,
        // not one per change.
        assertTrue(agreement.rejections() > 10,
                "the periodic wave must be rejected repeatedly, got " + agreement.rejections());
    }

    @Test
    void longNeverRepeatingChainStaysUnrejectedInBoth() {
        int positionCount = 128;
        int length = 8_000;
        List<Change> changes = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            changes.add(new Change(pack(i % positionCount, 64, 0), 1000 + i, 3, 1));
        }
        Agreement agreement = runBoth(changes);
        assertEquals(0, agreement.mismatches(), agreement::details);
        assertEquals(0, agreement.rejections());
    }

    /** Drives the production judge and the oracle over the same trace. */
    private static Agreement runBoth(List<Change> changes) {
        SpecificationOracle oracle = new SpecificationOracle();
        Map<Long, Integer> judgeWorld = new HashMap<>();
        int rejections = 0;
        int mismatches = 0;
        String details = "";

        oracle.begin();
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "agreement");
        for (int i = 0; i < changes.size(); i++) {
            Change change = changes.get(i);
            int old = oracle.worldValue(change.pos());

            boolean expected = oracle.before(change.pos(), old, change.newId(), change.flags(), change.ctx());

            boolean pushed = change.ctx() != UpdateContext.SELF;
            if (pushed) {
                SuperkoJudge.pushContext(change.ctx());
            }
            boolean actual = SuperkoJudge.beforeSetBlock(change.pos(), old, change.newId(), change.flags());
            if (actual && pushed) {
                SuperkoJudge.popContext();
            }
            // Realistic model: vanilla returns before the chunk write when old == new, so
            // such calls never reach the recording hook.
            if (!actual && old != change.newId()) {
                SuperkoJudge.afterSetBlock(change.pos(), change.newId(), change.flags());
                judgeWorld.put(change.pos(), change.newId());
            }
            if (!actual && pushed) {
                SuperkoJudge.popContext();
            }
            if (!expected && old != change.newId()) {
                oracle.after(change.pos(), change.newId(), change.ctx(), change.flags());
            }
            assertEquals(oracle.worldView(), judgeWorld, "world models diverged at step " + i);

            if (expected) {
                rejections++;
            }
            if (expected != actual) {
                mismatches++;
                if (mismatches == 1) {
                    details = describe(changes, i, old, expected, actual);
                }
            }
        }
        SuperkoJudge.endChain();
        oracle.end();
        return new Agreement(rejections, mismatches, details);
    }

    private static String describe(List<Change> changes, int step, int old,
                                   boolean expected, boolean actual) {
        StringBuilder sb = new StringBuilder();
        sb.append("first mismatch at step ").append(step)
                .append(": ").append(changes.get(step))
                .append(" old=").append(old)
                .append(" spec=").append(expected)
                .append(" judge=").append(actual)
                .append("\nrecent trace:");
        for (int j = Math.max(0, step - 12); j <= step; j++) {
            sb.append("\n  #").append(j).append(' ').append(changes.get(j));
        }
        return sb.toString();
    }
}
