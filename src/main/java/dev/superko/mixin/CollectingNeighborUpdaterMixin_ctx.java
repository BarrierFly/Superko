package dev.superko.mixin;

import dev.superko.core.SuperkoJudge;
import dev.superko.core.UpdateContext;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Update-context tagging for the vanilla 1.19+ manual update stack: while the run loop is
 * executing queued entries, every setBlock below it is neighbor-update-driven. This also
 * covers {@code MultiNeighborUpdate}, whose entry dispatches without going through the
 * static {@code NeighborUpdater.executeUpdate} helper.
 */
@Mixin(CollectingNeighborUpdater.class)
public abstract class CollectingNeighborUpdaterMixin_ctx {
    @Inject(method = "runUpdates", at = @At("HEAD"))
    private void superko$pushNeighborContext(CallbackInfo ci) {
        SuperkoJudge.pushContext(UpdateContext.NEIGHBOR);
    }

    @Inject(method = "runUpdates", at = @At("RETURN"))
    private void superko$popNeighborContext(CallbackInfo ci) {
        SuperkoJudge.popContext();
    }
}
