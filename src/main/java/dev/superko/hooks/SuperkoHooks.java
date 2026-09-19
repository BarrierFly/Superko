package dev.superko.hooks;

import dev.superko.config.SuperkoConfig;
import dev.superko.core.SuperkoJudge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The only place where Minecraft types meet the version-independent core. Called from the
 * mixins; keeps the core working purely on packed positions and state ids.
 */
public final class SuperkoHooks {
    private SuperkoHooks() {
    }

    /**
     * Per-call marker handed from the HEAD judge to the write hook. A single slot is
     * enough: the recorded write happens before any nested setBlock can run, and the
     * marker is cleared at the start of every judge call, so stale markers cannot leak.
     */
    private static final class WriteMark {
        boolean judged;
        long pos;
        int flags;
        int ctx;
    }

    private static final ThreadLocal<WriteMark> WRITE = ThreadLocal.withInitial(WriteMark::new);

    /** Log origin label for a server level, shared by the chain-boundary mixins. */
    public static String originOf(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /**
     * Judgment for the HEAD of {@code Level.setBlock(BlockPos, BlockState, int, int)}.
     *
     * @return true when the change must be rejected (the mixin cancels the setBlock)
     */
    public static boolean judgeSetBlock(Level level, BlockPos pos, BlockState newState, int flags) {
        WriteMark mark = WRITE.get();
        mark.judged = false;
        if (level.isClientSide || !SuperkoJudge.enabled) {
            return false;
        }
        BlockState oldState = level.getBlockState(pos);
        // BlockState instances are canonical, so identity is the cheapest possible
        // "no state change" test — and no-ops are common during cascades. Keep in sync
        // with the Q9 guard in SuperkoJudge.beforeSetBlock (both encode "no state change
        // => not an action"); this layer just skips the subsequent id/exemption lookups.
        if (oldState == newState) {
            return false;
        }
        if (SuperkoConfig.isExempt(oldState.getBlock()) || SuperkoConfig.isExempt(newState.getBlock())) {
            return false;
        }
        boolean reject = SuperkoJudge.beforeSetBlock(pos.asLong(), Block.getId(oldState), Block.getId(newState), flags);
        if (!reject) {
            mark.judged = true;
            mark.pos = pos.asLong();
            mark.flags = flags;
            mark.ctx = SuperkoJudge.currentContext();
        }
        return reject;
    }

    /**
     * Called by the mixin at the {@code getBlockState(pos)} call inside
     * {@code Level.setBlock}, which vanilla only reaches on the success path (right after
     * the chunk write, before the update dispatch). Records the actual world state for
     * every judged change.
     */
    public static void recordJudgedWrite(Level level, BlockPos pos) {
        WriteMark mark = WRITE.get();
        if (!mark.judged) {
            return;
        }
        mark.judged = false;
        BlockState actual = level.getBlockState(pos);
        SuperkoJudge.afterSetBlock(mark.pos, Block.getId(actual), mark.flags, mark.ctx);
    }
}
