package com.tmbu.tmbuclient.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.tmbu.tmbuclient.command.commands.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Manages client-side commands with a configurable prefix.
 * Uses Brigadier for parsing and tab completion.
 */
public class CommandManager {

    public static final CommandManager INSTANCE = new CommandManager();

    private final List<Command> commands = new ArrayList<>();
    private CommandDispatcher<SharedSuggestionProvider> dispatcher = new CommandDispatcher<>();
    private String prefix = ".";

    private CommandManager() {}

    public void init() {
        add(new ToggleCommand());
        add(new HelpCommand());
        add(new PrefixCommand());
        add(new FriendCommand());
        add(new BindCommand());

        commands.sort(Comparator.comparing(Command::getName));
        rebuildDispatcher();
    }

    public void add(Command command) {
        commands.removeIf(c -> c.getName().equals(command.getName()));
        commands.add(command);
    }

    public void rebuildDispatcher() {
        dispatcher = new CommandDispatcher<>();
        for (Command cmd : commands) {
            cmd.registerTo(dispatcher);
        }
    }

    /**
     * Try to handle a chat message as a command.
     * @return true if the message was a command (consumed), false if it should be sent normally
     */
    public boolean handleMessage(String message) {
        if (!message.startsWith(prefix)) return false;

        String input = message.substring(prefix.length()).trim();
        if (input.isEmpty()) return true;

        try {
            dispatch(input);
        } catch (CommandSyntaxException e) {
            chat(Component.literal("[Mist] ").append(
                Component.literal(e.getMessage()).withStyle(s -> s.withColor(0xFF5555))));
        }
        return true;
    }

    public void dispatch(String input) throws CommandSyntaxException {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        dispatcher.execute(input, mc.getConnection().getSuggestionsProvider());
    }

    /**
     * Get tab completions for the current input (without prefix).
     */
    public CompletableFuture<Suggestions> getSuggestions(String input, int cursor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return Suggestions.empty();

        String withoutPrefix = input.startsWith(prefix) ? input.substring(prefix.length()) : input;
        int adjustedCursor = cursor - prefix.length();
        if (adjustedCursor < 0) return Suggestions.empty();

        ParseResults<SharedSuggestionProvider> parse = dispatcher.parse(withoutPrefix, mc.getConnection().getSuggestionsProvider());
        return dispatcher.getCompletionSuggestions(parse, adjustedCursor);
    }

    public boolean isCommand(String message) {
        return message.startsWith(prefix);
    }

    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public List<Command> getCommands() { return commands; }
    public CommandDispatcher<SharedSuggestionProvider> getDispatcher() { return dispatcher; }

    public static void chat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(message, false);
        }
    }
}
