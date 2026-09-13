package dev.superko.mixin;

import dev.superko.core.ChainType;
import dev.superko.core.SuperkoJudge;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * One chain per handled packet (§5.1 row 1), covering the handlers that can change blocks:
 * dig/place/use/interact and chat commands (/setblock, /fill, ...).
 *
 * <p>These handlers first hop from the network thread onto the server thread (via
 * {@code PacketUtils.ensureRunningOnSameThread}, which exits by exception). On the network
 * thread invocation {@code isSameThread()} is false, so no chain is opened there; the
 * server-thread continuation gets HEAD/RETURN boundaries as usual.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerMixin_chain {
    @Shadow
    @Final
    public ServerPlayer player;

    private void superko$begin() {
        if (this.player.getLevel().getServer().isSameThread()) {
            SuperkoJudge.beginChain(ChainType.PACKET,
                    this.player.getLevel().dimension().location().toString());
        }
    }

    private void superko$end() {
        if (this.player.getLevel().getServer().isSameThread()) {
            SuperkoJudge.endChain();
        }
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"))
    private void superko$beginPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        superko$begin();
    }

    @Inject(method = "handlePlayerAction", at = @At("RETURN"))
    private void superko$endPlayerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        superko$end();
    }

    @Inject(method = "handleUseItemOn", at = @At("HEAD"))
    private void superko$beginUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        superko$begin();
    }

    @Inject(method = "handleUseItemOn", at = @At("RETURN"))
    private void superko$endUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci) {
        superko$end();
    }

    @Inject(method = "handleUseItem", at = @At("HEAD"))
    private void superko$beginUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        superko$begin();
    }

    @Inject(method = "handleUseItem", at = @At("RETURN"))
    private void superko$endUseItem(ServerboundUseItemPacket packet, CallbackInfo ci) {
        superko$end();
    }

    @Inject(method = "handleInteract", at = @At("HEAD"))
    private void superko$beginInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        superko$begin();
    }

    @Inject(method = "handleInteract", at = @At("RETURN"))
    private void superko$endInteract(ServerboundInteractPacket packet, CallbackInfo ci) {
        superko$end();
    }

    @Inject(method = "handleChatCommand", at = @At("HEAD"))
    private void superko$beginChatCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        superko$begin();
    }

    @Inject(method = "handleChatCommand", at = @At("RETURN"))
    private void superko$endChatCommand(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        superko$end();
    }
}
