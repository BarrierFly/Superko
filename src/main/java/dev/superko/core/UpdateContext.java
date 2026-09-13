package dev.superko.core;

/**
 * Update context type carried by a setBlock attempt; part of the action key (plan A, §5.3
 * of the design doc). {@code SELF} means the change originates from the driving logic of
 * the chain itself (tick bodies, block events, player actions, ...); {@code NEIGHBOR} means
 * it happens while a queued neighbor update is being executed; the shape contexts identify
 * the face ({@code Direction#ordinal()}) whose shape update is being executed.
 */
public final class UpdateContext {
    public static final int SELF = 0;
    public static final int NEIGHBOR = 1;
    public static final int SHAPE_BASE = 2; // SHAPE_BASE + Direction.ordinal(), 0..5

    private UpdateContext() {
    }

    /**
     * Packs (context, flags, newStateId) into one long so the rejected list can be a plain
     * set of longs. Flags use the full 32 bits, state ids stay below 2^26 in practice.
     */
    public static long packActionKey(int ctx, int flags, int newStateId) {
        return ((ctx & 0xFL) << 60) | ((flags & 0xFFFFFFFFL) << 26) | (newStateId & 0x3FFFFFFL);
    }
}
