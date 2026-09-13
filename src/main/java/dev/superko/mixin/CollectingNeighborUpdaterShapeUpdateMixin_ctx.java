package dev.superko.mixin;

import dev.superko.core.SuperkoJudge;
import dev.superko.core.UpdateContext;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shape updates carry the face being updated; the direction is part of the action key
 * (§5.3, Q9). Tagged at the queued entry so both the collecting and the instant updater's
 * shape paths are distinguishable from plain neighbor updates.
 */
@Mixin(targets = "net.minecraft.world.level.redstone.CollectingNeighborUpdater$ShapeUpdate")
public abstract class CollectingNeighborUpdaterShapeUpdateMixin_ctx {
    @Shadow
    @Final
    private Direction direction;

    @Inject(method = "runNext", at = @At("HEAD"))
    private void superko$pushShapeContext(Level level, CallbackInfoReturnable<Boolean> cir) {
        SuperkoJudge.pushContext(UpdateContext.SHAPE_BASE + this.direction.ordinal());
    }

    @Inject(method = "runNext", at = @At("RETURN"))
    private void superko$popShapeContext(Level level, CallbackInfoReturnable<Boolean> cir) {
        SuperkoJudge.popContext();
    }
}
