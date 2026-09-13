package dev.superko.mixin;

import dev.superko.hooks.SuperkoHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The core hook: every block state change funnels into
 * {@code Level.setBlock(BlockPos, BlockState, int, int)}.
 *
 * <p>Judging at HEAD (before the chunk write) means a rejected change never emits any of
 * its updates — required by the design ("绝不允许此次动作的更新放出"). Recording happens at
 * TAIL, only when the change actually landed.
 */
@Mixin(Level.class)
public abstract class LevelMixin_setBlock {
    private static final String SET_BLOCK =
            "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z";

    @Inject(method = SET_BLOCK, at = @At("HEAD"), cancellable = true)
    private void superko$judge(BlockPos pos, BlockState newState, int flags, int recursionLeft,
                               CallbackInfoReturnable<Boolean> cir) {
        if (SuperkoHooks.judgeSetBlock((Level) (Object) this, pos, newState, flags)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = SET_BLOCK, at = @At("TAIL"))
    private void superko$record(BlockPos pos, BlockState newState, int flags, int recursionLeft,
                                CallbackInfoReturnable<Boolean> cir) {
        SuperkoHooks.recordSetBlock((Level) (Object) this, pos, newState, flags);
    }
}
