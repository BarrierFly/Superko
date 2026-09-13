package dev.superko.mixin;

import com.mojang.datafixers.DataFixer;
import dev.superko.SuperkoMod;
import java.net.Proxy;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a server reference for the broadcast log sink (no Fabric API lifecycle events
 * needed).
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    private static final String CONSTRUCTOR =
            "<init>(Ljava/lang/Thread;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;"
                    + "Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/WorldStem;"
                    + "Ljava/net/Proxy;Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/server/Services;"
                    + "Lnet/minecraft/server/level/progress/ChunkProgressListenerFactory;)V";

    @Inject(method = CONSTRUCTOR, at = @At("TAIL"))
    private void superko$bindServer(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource,
                                    PackRepository packRepository, WorldStem worldStem, Proxy proxy,
                                    DataFixer fixerUpper, Services services,
                                    ChunkProgressListenerFactory progressListenerFactory, CallbackInfo ci) {
        SuperkoMod.bindServer((MinecraftServer) (Object) this);
    }
}
