package dev.superko.config;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.superko.core.SuperkoJudge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * {@code /superko} — registered into the vanilla dispatcher by {@code CommandsMixin}, so
 * no Fabric API is required. Permission level 2 (ops).
 */
public final class SuperkoCommands {
    private static final String PREFIX = "[Superko] ";

    private SuperkoCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("superko")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("on").executes(ctx -> {
                    SuperkoConfig.setEnabled(true);
                    ctx.getSource().sendSuccess(Component.literal(PREFIX + "enabled"), true);
                    return 1;
                }))
                .then(Commands.literal("off").executes(ctx -> {
                    SuperkoConfig.setEnabled(false);
                    ctx.getSource().sendSuccess(Component.literal(PREFIX + "disabled (chains run vanilla behavior)"), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    SuperkoConfig c = SuperkoConfig.get();
                    ctx.getSource().sendSuccess(Component.literal(PREFIX
                            + "enabled=" + c.enabled
                            + ", logLevel=" + c.logLevel
                            + ", exemptBlocks=" + SuperkoConfig.exemptList()), false);
                    ctx.getSource().sendSuccess(Component.literal(PREFIX
                            + "chains started=" + SuperkoJudge.chainsStarted
                            + ", judged setBlocks=" + SuperkoJudge.judgedSetBlocks
                            + ", recorded setBlocks=" + SuperkoJudge.recordedSetBlocks
                            + ", rejected setBlocks=" + SuperkoJudge.rejectedSetBlocks), false);
                    ctx.getSource().sendSuccess(Component.literal(PREFIX
                            + "last ended chain: touched=" + SuperkoJudge.lastChainTouched
                            + ", snapshots=" + SuperkoJudge.lastChainHistory
                            + " (these grow while a chain records changes)"), false);
                    return 1;
                }))
                .then(Commands.literal("log")
                        .then(Commands.argument("level", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        List.of("none", "console", "broadcast", "debug"), b))
                                .executes(ctx -> {
                                    String level = StringArgumentType.getString(ctx, "level").toLowerCase(java.util.Locale.ROOT);
                                    if (!level.equals("none") && !level.equals("console")
                                            && !level.equals("broadcast") && !level.equals("debug")) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                PREFIX + "unknown log level '" + level + "' (none|console|broadcast|debug)"));
                                        return 0;
                                    }
                                    SuperkoConfig.setLogLevel(level);
                                    ctx.getSource().sendSuccess(Component.literal(PREFIX + "log level set to " + level), true);
                                    return 1;
                                })))
                .then(Commands.literal("exempt")
                        .then(Commands.literal("add")
                                .then(Commands.argument("block", StringArgumentType.string())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                BuiltInRegistries.BLOCK.keySet(), b))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "block");
                                            if (SuperkoConfig.addExempt(id)) {
                                                ctx.getSource().sendSuccess(Component.literal(
                                                        PREFIX + "exempted " + SuperkoConfig.parseBlockId(id)), true);
                                                return 1;
                                            }
                                            ctx.getSource().sendFailure(Component.literal(
                                                    PREFIX + "cannot exempt '" + id + "' (unknown block or already listed)"));
                                            return 0;
                                        })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("block", StringArgumentType.string())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                SuperkoConfig.exemptList(), b))
                                        .executes(ctx -> {
                                            String id = StringArgumentType.getString(ctx, "block");
                                            if (SuperkoConfig.removeExempt(id)) {
                                                ctx.getSource().sendSuccess(Component.literal(
                                                        PREFIX + "no longer exempting " + id), true);
                                                return 1;
                                            }
                                            ctx.getSource().sendFailure(Component.literal(
                                                    PREFIX + "'" + id + "' is not on the exempt list"));
                                            return 0;
                                        })))
                        .then(Commands.literal("list").executes(ctx -> {
                            List<String> list = SuperkoConfig.exemptList();
                            ctx.getSource().sendSuccess(Component.literal(
                                    PREFIX + "exempt blocks: " + (list.isEmpty() ? "(none)" : list)), false);
                            return 1;
                        })));
    }
}
