package com.tmbu.tmbuclient.command.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmbu.tmbuclient.command.Command;
import com.tmbu.tmbuclient.command.CommandManager;
import net.minecraft.commands.SharedSuggestionProvider;

public class PrefixCommand extends Command {
    public PrefixCommand() {
        super("prefix", "Changes the command prefix.");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder
            .executes(ctx -> {
                info("Current prefix: %s", CommandManager.INSTANCE.getPrefix());
                return SUCCESS;
            })
            .then(argument("prefix", StringArgumentType.string())
                .executes(ctx -> {
                    String newPrefix = StringArgumentType.getString(ctx, "prefix");
                    CommandManager.INSTANCE.setPrefix(newPrefix);
                    info("Prefix set to: %s", newPrefix);
                    return SUCCESS;
                }));
    }
}
