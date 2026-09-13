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
        if (level.isClientSide || !SuperkoJudge.enabled) {
            return false;
        }
        BlockState oldState = level.getBlockState(pos);
        if (SuperkoConfig.isExempt(oldState.getBlock()) || SuperkoConfig.isExempt(newState.getBlock())) {
            return false;
        }
        return SuperkoJudge.beforeSetBlock(pos.asLong(), Block.getId(oldState), Block.getId(newState), flags);
    }

    /**
     * Confirmation for the TAIL of the same method: records the change when it actually
     * landed in the world. Everything needed (pos, state, flags) comes from this call
     * itself, so there is no cross-call state that can go stale.
     */
    public static void recordSetBlock(Level level, BlockPos pos, BlockState newState, int flags) {
        if (level.isClientSide || !SuperkoJudge.enabled) {
            return;
        }
        if (SuperkoConfig.isExempt(newState.getBlock())) {
            return;
        }
        if (level.getBlockState(pos) != newState) {
            return; // the change did not land (failed call / early return)
        }
        SuperkoJudge.afterSetBlock(pos.asLong(), Block.getId(newState), flags);
    }
}
