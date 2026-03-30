package com.tmbu.tmbuclient.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmbu.tmbuclient.command.Command;
import com.tmbu.tmbuclient.command.CommandManager;
import net.minecraft.commands.SharedSuggestionProvider;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Lists all available commands.", "?");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.executes(ctx -> {
            info("Commands (prefix: %s):", CommandManager.INSTANCE.getPrefix());
            for (Command cmd : CommandManager.INSTANCE.getCommands()) {
                String aliases = cmd.getAliases().isEmpty() ? "" : " (" + String.join(", ", cmd.getAliases()) + ")";
                info("  %s%s - %s", cmd.getName(), aliases, cmd.getDescription());
            }
            return SUCCESS;
        });
    }
}
