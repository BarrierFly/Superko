package dev.superko.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the package-private {@code level} field of LevelChunk so the block-entity tick
 * hook can tell client and server worlds apart.
 */
@Mixin(LevelChunk.class)
public interface LevelChunkLevelAccessor {
    @Accessor("level")
    Level superko$getLevel();
}
