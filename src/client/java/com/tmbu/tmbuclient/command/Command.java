package com.tmbu.tmbuclient.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Base class for all client-side commands.
 * Uses Brigadier for parsing, tab completion, and argument types.
 */
public abstract class Command {

    protected static final int SUCCESS = com.mojang.brigadier.Command.SINGLE_SUCCESS;
    protected static final Minecraft mc = Minecraft.getInstance();

    private final String name;
    private final String description;
    private final List<String> aliases;

    public Command(String name, String description, String... aliases) {
        this.name = name;
        this.description = description;
        this.aliases = List.of(aliases);
    }

    protected static <T> RequiredArgumentBuilder<SharedSuggestionProvider, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    protected static LiteralArgumentBuilder<SharedSuggestionProvider> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    public final void registerTo(CommandDispatcher<SharedSuggestionProvider> dispatcher) {
        register(dispatcher, name);
        for (String alias : aliases) register(dispatcher, alias);
    }

    private void register(CommandDispatcher<SharedSuggestionProvider> dispatcher, String name) {
        LiteralArgumentBuilder<SharedSuggestionProvider> builder = LiteralArgumentBuilder.literal(name);
        build(builder);
        dispatcher.register(builder);
    }

    public abstract void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder);

    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getAliases() { return aliases; }

    /** Send an info message to the player's chat. */
    protected void info(String msg, Object... args) {
        CommandManager.chat(Component.literal("[Mist] ").append(
            Component.literal(String.format(msg, args))));
    }

    /** Send an error message to the player's chat. */
    protected void error(String msg, Object... args) {
        CommandManager.chat(Component.literal("[Mist] ").append(
            Component.literal(String.format(msg, args)).withStyle(s -> s.withColor(0xFF5555))));
    }
}
