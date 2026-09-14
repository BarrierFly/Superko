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
 * its updates — required by the design ("绝不允许此次动作的更新放出").
 *
 * <p>Recording is NOT done at TAIL: in production the TAIL point never fired for this
 * method, silently disabling all recording. Instead the hook injects at the
 * {@code getBlockState(pos)} call that vanilla performs right after the chunk write on
 * the success path (before any update dispatch), which is a plain, composable INVOKE
 * point. The judge leaves a per-call marker that this hook consumes.
 */
@Mixin(Level.class)
public abstract class LevelMixin_setBlock {
    private static final String SET_BLOCK =
            "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z";
    private static final String GET_STATE_INVOKE =
            "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;";

    @Inject(method = SET_BLOCK, at = @At("HEAD"), cancellable = true)
    private void superko$judge(BlockPos pos, BlockState newState, int flags, int recursionLeft,
                               CallbackInfoReturnable<Boolean> cir) {
        if (SuperkoHooks.judgeSetBlock((Level) (Object) this, pos, newState, flags)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = SET_BLOCK, at = @At(value = "INVOKE", target = GET_STATE_INVOKE))
    private void superko$record(BlockPos pos, BlockState newState, int flags, int recursionLeft,
                                CallbackInfoReturnable<Boolean> cir) {
        SuperkoHooks.recordJudgedWrite((Level) (Object) this, pos);
    }
}
