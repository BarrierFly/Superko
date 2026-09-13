package dev.superko.mixin;

import dev.superko.core.ChainType;
import dev.superko.core.SuperkoJudge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chain boundary for block entity ticks (§5.1 row 4): one chain per block entity per tick.
 * Every server-side BE tick goes through this rebindable wrapper. {@code this$0} is the
 * synthetic outer-class reference to the owning LevelChunk.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$RebindableTickingBlockEntityWrapper")
public abstract class LevelChunkMixin_chain {
    @Shadow
    @Final
    private LevelChunk this$0;

    private Level superko$level() {
        return ((LevelChunkLevelAccessor) (Object) this$0).superko$getLevel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void superko$beginBlockEntityTick(CallbackInfo ci) {
        Level level = superko$level();
        if (level.isClientSide) {
            return;
        }
        SuperkoJudge.beginChain(ChainType.BLOCK_ENTITY_TICK, level.dimension().location().toString());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void superko$endBlockEntityTick(CallbackInfo ci) {
        if (superko$level().isClientSide) {
            return;
        }
        SuperkoJudge.endChain();
    }
}
