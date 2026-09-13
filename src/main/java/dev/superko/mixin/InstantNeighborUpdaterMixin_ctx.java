package dev.superko.mixin;

import dev.superko.core.SuperkoJudge;
import dev.superko.core.UpdateContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.InstantNeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Update-context tagging for the vanilla instant (recursive) updater — used by client
 * worlds, and on the server when Carpet-TIS-Addition's instantBlockUpdaterReintroduced
 * swaps it in (§6.1). Both entry points funnel into the tagged methods.
 */
@Mixin(InstantNeighborUpdater.class)
public abstract class InstantNeighborUpdaterMixin_ctx {
    @Inject(method = "shapeUpdate", at = @At("HEAD"))
    private void superko$pushShapeContext(Direction direction, BlockState state, BlockPos pos,
                                          BlockPos neighborPos, int flags, int recursionLevel, CallbackInfo ci) {
        SuperkoJudge.pushContext(UpdateContext.SHAPE_BASE + direction.ordinal());
    }

    @Inject(method = "shapeUpdate", at = @At("RETURN"))
    private void superko$popShapeContext(Direction direction, BlockState state, BlockPos pos,
                                         BlockPos neighborPos, int flags, int recursionLevel, CallbackInfo ci) {
        SuperkoJudge.popContext();
    }

    @Inject(method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("HEAD"))
    private void superko$pushNeighborContext(BlockState state, BlockPos pos, Block neighborBlock,
                                             BlockPos neighborPos, boolean movedByPiston, CallbackInfo ci) {
        SuperkoJudge.pushContext(UpdateContext.NEIGHBOR);
    }

    @Inject(method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V",
            at = @At("RETURN"))
    private void superko$popNeighborContext(BlockState state, BlockPos pos, Block neighborBlock,
                                            BlockPos neighborPos, boolean movedByPiston, CallbackInfo ci) {
        SuperkoJudge.popContext();
    }
}
