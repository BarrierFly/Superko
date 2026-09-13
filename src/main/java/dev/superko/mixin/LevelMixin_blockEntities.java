package dev.superko.mixin;

import dev.superko.core.ChainType;
import dev.superko.core.SuperkoJudge;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chain boundary for block entity ticks (§5.1 row 4): one chain per block entity per
 * tick. {@code Level.tickBlockEntities} has exactly one call site of
 * {@code TickingBlockEntity.tick()}, so bracketing that INVOKE gives per-block-entity
 * boundaries. This deliberately avoids mixin-ing the inner-class implementations: their
 * synthetic outer-field reference has no entry in the mappings and cannot be shadowed
 * (which crashed the mixin apply at chunk-tick time in production).
 */
@Mixin(Level.class)
public abstract class LevelMixin_blockEntities {
    private static final String TICK_INVOKE =
            "Lnet/minecraft/world/level/block/entity/TickingBlockEntity;tick()V";

    @Inject(method = "tickBlockEntities", at = @At(value = "INVOKE", target = TICK_INVOKE))
    private void superko$beginBlockEntityTick(CallbackInfo ci) {
        if (!((Level) (Object) this).isClientSide()) {
            SuperkoJudge.beginChain(ChainType.BLOCK_ENTITY_TICK,
                    ((Level) (Object) this).dimension().location().toString());
        }
    }

    @Inject(method = "tickBlockEntities", at = @At(value = "INVOKE", target = TICK_INVOKE, shift = At.Shift.AFTER))
    private void superko$endBlockEntityTick(CallbackInfo ci) {
        if (!((Level) (Object) this).isClientSide()) {
            SuperkoJudge.endChain();
        }
    }
}
