package es.boffmedia.teras.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;

public class SmartRotomCommand {
    public SmartRotomCommand(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> literalBuilder = Commands.literal("smartrotom")
                .then(unlockApp());
        dispatcher.register(literalBuilder);

    }

    private ArgumentBuilder<CommandSource, ?> unlockApp() {
        return Commands.literal("unlockApp")
                .executes((command) -> {
                    return 1;
                });
    }
}