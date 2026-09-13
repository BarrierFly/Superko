package dev.superko.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-JVM tests of the judgment core (test plan §9.1). The hooks layer is stubbed out by
 * driving {@link SuperkoJudge} directly with packed positions and state ids.
 */
class SuperkoJudgeTest {
    private static final int FLAGS = 3;      // UPDATE_NEIGHBORS | UPDATE_CLIENTS
    private static final int NO_UPDATE = 18; // UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE

    private static final long P = pack(0, 64, 0);
    private static final long Q = pack(1, 64, 0);

    @BeforeEach
    @AfterEach
    void cleanState() {
        ChainTracker.maxTouched = 4096;
        ChainTracker.maxHistory = 4096;
        ChainTracker.maxSnapshotEntries = 1 << 20;
        SuperkoJudge.enabled = true;
        // force-closes anything a failed test may have left behind, then discards it
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "cleanup");
        SuperkoJudge.endChain();
    }

    /** Judges an attempt; when not rejected, confirms the change landed. */
    private static void setBlock(long pos, int oldId, int newId, int flags) {
        assertFalse(SuperkoJudge.beforeSetBlock(pos, oldId, newId, flags), "expected the change to be allowed");
        commitPending();
    }

    private static void commitPending() {
        SuperkoJudge.Pending p = SuperkoJudge.popPending();
        assertNotNull(p, "expected a pending record");
        SuperkoJudge.commitPending(p, p.pos, p.newStateId);
    }

    private static long pack(int x, int y, int z) {
        // vanilla BlockPos.asLong() layout: x bits 38-63, z bits 12-37, y bits 0-11
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | ((y & 0xFFFL));
    }

    @Test
    void packingRoundTrip() {
        long pos = pack(123456, 320, -654321);
        assertEquals(123456, SuperkoJudge.unpackX(pos));
        assertEquals(320, SuperkoJudge.unpackY(pos));
        assertEquals(-654321, SuperkoJudge.unpackZ(pos));
    }

    @Test
    void plainCallsOutsideChainsAreNeverJudged() {
        assertFalse(SuperkoJudge.beforeSetBlock(P, 0, 1, FLAGS));
        assertNull(SuperkoJudge.popPending());
    }

    @Test
    void sameStateNoopIsIgnored() {
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        assertFalse(SuperkoJudge.beforeSetBlock(P, 1, 1, FLAGS));
        assertNull(SuperkoJudge.popPending());
        SuperkoJudge.endChain();
    }

    @Test
    void repeatedOscillationIsRejected() {
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        setBlock(P, 0, 1, FLAGS); // moment 0: {P=1}
        setBlock(P, 1, 0, FLAGS); // moment 1: {P=0}
        // would recreate moment 0 with the same action -> superko
        assertTrue(SuperkoJudge.beforeSetBlock(P, 0, 1, FLAGS));
        // rejected list short-circuits any further identical attempts
        assertTrue(SuperkoJudge.beforeSetBlock(P, 0, 1, FLAGS));
        assertNull(SuperkoJudge.popPending());
        SuperkoJudge.endChain();
    }

    @Test
    void chainEndResetsEverything() {
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        setBlock(P, 0, 1, FLAGS);
        SuperkoJudge.endChain();

        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        assertFalse(SuperkoJudge.beforeSetBlock(P, 0, 1, FLAGS));
        SuperkoJudge.endChain();
    }

    @Test
    void keySetEqualityRule() {
        // Q3: a configuration that includes a block the earlier moment did not touch is
        // NOT identical, even if that block was meanwhile changed back to its old state.
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        setBlock(P, 0, 1, FLAGS); // {P=1}
        setBlock(Q, 0, 1, FLAGS); // {P=1, Q=1}
        setBlock(P, 1, 0, FLAGS); // {P=0, Q=1}
        // P: 0->1 would give {P=1, Q=1}; the only earlier moment with P=1 also had no Q
        assertFalse(SuperkoJudge.beforeSetBlock(P, 0, 1, FLAGS));
        commitPending();          // {P=1, Q=1} is now moment 3
        // P: 1->0 would give {P=0, Q=1} = moment 2, produced by the same action -> superko
        assertTrue(SuperkoJudge.beforeSetBlock(P, 1, 0, FLAGS));
        SuperkoJudge.endChain();
    }

    @Test
    void sameConfigurationFromDifferentActionIsAllowed() {
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        SuperkoJudge.pushContext(UpdateContext.NEIGHBOR);
        setBlock(P, 0, 1, FLAGS); // moment 0 produced by a neighbor update
        SuperkoJudge.popContext();
        setBlock(P, 1, 0, FLAGS); // moment 1 produced by self logic
        // self-logic 0->1 recreates moment 0's configuration, but a different action
        // produced it; that action may still diverge (§2.3)
        assertFalse(SuperkoJudge.beforeSetBlock(P, 0, 1, FLAGS));
        SuperkoJudge.endChain();
    }

    @Test
    void noUpdateFlagsAreRecordedButNeverRejected() {
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        assertFalse(SuperkoJudge.beforeSetBlock(P, 0, 1, NO_UPDATE));
        commitPending();          // {P=1} produced by a no-update action
        setBlock(P, 1, 0, FLAGS); // {P=0}
        assertFalse(SuperkoJudge.beforeSetBlock(P, 0, 1, NO_UPDATE)); // never rejected (Q8)
        commitPending();          // {P=1} again
        // {P=0} equals moment 1, produced by the same with-update action -> superko
        assertTrue(SuperkoJudge.beforeSetBlock(P, 1, 0, FLAGS));
        SuperkoJudge.endChain();
    }

    @Test
    void passengerChainsNestInsideVehicleChains() {
        SuperkoJudge.beginChain(ChainType.ENTITY, "test");
        setBlock(P, 0, 1, FLAGS); // vehicle moment 0: {P=1}
        SuperkoJudge.beginChain(ChainType.ENTITY_PASSENGER, "test");
        setBlock(Q, 0, 1, FLAGS); // passenger's own chain, empty history
        SuperkoJudge.endChain();
        // back in the vehicle chain: its history survived the passenger nesting
        assertTrue(SuperkoJudge.beforeSetBlock(P, 0, 1, FLAGS));
        SuperkoJudge.endChain();
    }

    @Test
    void capExceededPassesThrough() {
        ChainTracker.maxHistory = 2;
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        setBlock(P, 0, 1, FLAGS);
        setBlock(P, 1, 0, FLAGS);
        setBlock(Q, 0, 1, FLAGS); // recording this blows the history cap -> bypass
        assertFalse(SuperkoJudge.beforeSetBlock(P, 0, 1, FLAGS));
        SuperkoJudge.endChain();
    }

    @Test
    void stalePendingsAreDroppedOnCommit() {
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, "test");
        SuperkoJudge.Pending stale = new SuperkoJudge.Pending(P, 7, FLAGS, UpdateContext.SELF);
        SuperkoJudge.commitPending(stale, Q, 7); // identity mismatch -> dropped
        setBlock(P, 0, 1, FLAGS);
        SuperkoJudge.endChain();
    }
}
