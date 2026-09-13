package dev.superko.mixin;

import dev.superko.core.ChainType;
import dev.superko.core.SuperkoJudge;
import dev.superko.hooks.SuperkoHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chain boundaries for entity ticks (§5.1 row 5): falling blocks, endermen, etc. One chain
 * per entity per tick. Passenger ticks nest inside the vehicle's chain, which the core
 * handles by keeping a chain stack.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin_entityTick {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void superko$beginEntity(Entity entity, CallbackInfo ci) {
        SuperkoJudge.beginChain(ChainType.ENTITY, SuperkoHooks.originOf((ServerLevel) (Object) this));
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void superko$endEntity(Entity entity, CallbackInfo ci) {
        SuperkoJudge.endChain();
    }

    @Inject(method = "tickPassenger", at = @At("HEAD"))
    private void superko$beginPassenger(Entity ridingEntity, Entity passengerEntity, CallbackInfo ci) {
        SuperkoJudge.beginChain(ChainType.ENTITY_PASSENGER, SuperkoHooks.originOf((ServerLevel) (Object) this));
    }

    @Inject(method = "tickPassenger", at = @At("RETURN"))
    private void superko$endPassenger(Entity ridingEntity, Entity passengerEntity, CallbackInfo ci) {
        SuperkoJudge.endChain();
    }
}
