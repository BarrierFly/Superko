package dev.superko.mixin;

import dev.superko.core.ChainType;
import dev.superko.core.SuperkoJudge;
import dev.superko.hooks.SuperkoHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chain boundaries for scheduled ticks and block events, one chain each (§5.1).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin_chain {
    @Inject(method = "tickBlock", at = @At("HEAD"))
    private void superko$beginTickBlock(BlockPos pos, Block block, CallbackInfo ci) {
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, SuperkoHooks.originOf((ServerLevel) (Object) this));
    }

    @Inject(method = "tickBlock", at = @At("RETURN"))
    private void superko$endTickBlock(BlockPos pos, Block block, CallbackInfo ci) {
        SuperkoJudge.endChain();
    }

    @Inject(method = "tickFluid", at = @At("HEAD"))
    private void superko$beginTickFluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        SuperkoJudge.beginChain(ChainType.SCHEDULED_TICK, SuperkoHooks.originOf((ServerLevel) (Object) this));
    }

    @Inject(method = "tickFluid", at = @At("RETURN"))
    private void superko$endTickFluid(BlockPos pos, Fluid fluid, CallbackInfo ci) {
        SuperkoJudge.endChain();
    }

    @Inject(method = "doBlockEvent", at = @At("HEAD"))
    private void superko$beginBlockEvent(BlockEventData event, CallbackInfoReturnable<Boolean> cir) {
        SuperkoJudge.beginChain(ChainType.BLOCK_EVENT, SuperkoHooks.originOf((ServerLevel) (Object) this));
    }

    @Inject(method = "doBlockEvent", at = @At("RETURN"))
    private void superko$endBlockEvent(BlockEventData event, CallbackInfoReturnable<Boolean> cir) {
        SuperkoJudge.endChain();
    }
}
