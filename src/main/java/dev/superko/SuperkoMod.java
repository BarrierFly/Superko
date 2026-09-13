package dev.superko;

import dev.superko.compat.TisInstantUpdaterCompat;
import dev.superko.config.SuperkoConfig;
import dev.superko.core.SuperkoLog;
import dev.superko.core.UpdateContext;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SuperkoMod implements ModInitializer {
    public static final String MOD_ID = "superko";
    public static final Logger LOGGER = LoggerFactory.getLogger("Superko");

    private static volatile MinecraftServer server;

    public static MinecraftServer server() {
        return server;
    }

    /** Called by MinecraftServerMixin; the broadcast sink needs the player list. */
    public static void bindServer(MinecraftServer srv) {
        server = srv;
    }

    /**
     * Console output goes to SLF4J; the "broadcast" level additionally sends the message
     * to online operators. The server reference is resolved lazily so no extra mixin is
     * needed for it.
     */
    public static final SuperkoLog.Sink LOG_SINK = new SuperkoLog.Sink() {
        @Override
        public void console(String message) {
            LOGGER.info("{}", message);
        }

        @Override
        public void broadcast(String message) {
            MinecraftServer srv = server;
            if (srv != null) {
                for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
                    if (player.hasPermissions(2)) {
                        player.sendSystemMessage(Component.literal(message));
                    }
                }
            }
        }
    };

    @Override
    public void onInitialize() {
        SuperkoLog.setContextNamer(ctx -> {
            if (ctx == UpdateContext.NEIGHBOR) {
                return "neighbor";
            }
            if (ctx >= UpdateContext.SHAPE_BASE && ctx - UpdateContext.SHAPE_BASE < Direction.values().length) {
                return "shape:" + Direction.values()[ctx - UpdateContext.SHAPE_BASE].getName();
            }
            return "self";
        });
        SuperkoConfig.load();
        TisInstantUpdaterCompat.logAdvice();
        LOGGER.info("[Superko] initialized (enabled={}, logLevel={})",
                SuperkoConfig.get().enabled, SuperkoConfig.get().logLevel);
    }
}
