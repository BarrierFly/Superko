package dev.superko.mixin;

import com.mojang.brigadier.CommandDispatcher;
import dev.superko.config.SuperkoCommands;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Registers {@code /superko} directly into the vanilla dispatcher so no Fabric API is
 * required. The Commands object is constructed once per server launch.
 */
@Mixin(Commands.class)
public abstract class CommandsMixin {
    @Shadow
    @Final
    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>(Lnet/minecraft/commands/Commands$CommandSelection;Lnet/minecraft/commands/CommandBuildContext;)V",
            at = @At("TAIL"))
    private void superko$register(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo ci) {
        this.dispatcher.register(SuperkoCommands.build());
    }
}
