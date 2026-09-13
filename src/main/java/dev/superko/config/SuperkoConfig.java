package dev.superko.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.superko.SuperkoMod;
import dev.superko.core.SuperkoJudge;
import dev.superko.core.SuperkoLog;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Standalone config stored at {@code config/superko.json}. Defaults: enabled, console-only
 * logging, no exempt blocks (Q5).
 */
public final class SuperkoConfig {
    public boolean enabled = true;
    public String logLevel = "console";
    public List<String> exemptBlocks = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SuperkoConfig instance = new SuperkoConfig();
    private static volatile Set<String> exempt = Set.of();

    private SuperkoConfig() {
    }

    public static SuperkoConfig get() {
        return instance;
    }

    public static synchronized void load() {
        Path path = configPath();
        try {
            if (Files.exists(path)) {
                SuperkoConfig read = GSON.fromJson(Files.readString(path), SuperkoConfig.class);
                instance = read != null ? read : new SuperkoConfig();
            } else {
                instance = new SuperkoConfig();
                save();
            }
        } catch (Exception e) {
            SuperkoMod.LOGGER.error("[Superko] Failed to read superko.json, using defaults", e);
            instance = new SuperkoConfig();
        }
        apply();
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(configPath().getParent());
            Files.writeString(configPath(), GSON.toJson(instance));
        } catch (IOException e) {
            SuperkoMod.LOGGER.error("[Superko] Failed to write superko.json", e);
        }
    }

    public static void apply() {
        SuperkoJudge.enabled = instance.enabled;
        SuperkoLog.configure(parseLogLevel(instance.logLevel), SuperkoMod.LOG_SINK);
        exempt = Set.copyOf(instance.exemptBlocks);
    }

    private static int parseLogLevel(String s) {
        return switch (s == null ? "" : s.toLowerCase(Locale.ROOT)) {
            case "none" -> SuperkoLog.LEVEL_NONE;
            case "broadcast" -> SuperkoLog.LEVEL_BROADCAST;
            case "debug" -> SuperkoLog.LEVEL_DEBUG;
            default -> SuperkoLog.LEVEL_CONSOLE;
        };
    }

    public static void setEnabled(boolean on) {
        instance.enabled = on;
        apply();
        save();
    }

    public static void setLogLevel(String level) {
        instance.logLevel = level.toLowerCase(Locale.ROOT);
        apply();
        save();
    }

    public static boolean isExempt(Block block) {
        return exempt.contains(BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    /** Adds a block id ("minecraft:piston" or "piston"); false if unknown or already present. */
    public static boolean addExempt(String id) {
        ResourceLocation rl = parseBlockId(id);
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            return false;
        }
        String key = rl.toString();
        if (instance.exemptBlocks.contains(key)) {
            return false;
        }
        instance.exemptBlocks.add(key);
        apply();
        save();
        return true;
    }

    /** Removes a block id; false if it was not on the list. */
    public static boolean removeExempt(String id) {
        ResourceLocation rl = parseBlockId(id);
        if (rl == null) {
            return false;
        }
        String key = rl.toString();
        if (!instance.exemptBlocks.remove(key)) {
            return false;
        }
        apply();
        save();
        return true;
    }

    public static List<String> exemptList() {
        return List.copyOf(instance.exemptBlocks);
    }

    public static ResourceLocation parseBlockId(String id) {
        String withNs = id.indexOf(':') >= 0 ? id : "minecraft:" + id;
        return ResourceLocation.tryParse(withNs);
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("superko.json");
    }
}
