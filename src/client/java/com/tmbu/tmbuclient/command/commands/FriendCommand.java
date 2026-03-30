package com.tmbu.tmbuclient.command.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.tmbu.tmbuclient.command.Command;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.HashSet;
import java.util.Set;

public class FriendCommand extends Command {

    /** Global friend list — modules can check this to avoid targeting friends. */
    public static final Set<String> FRIENDS = new HashSet<>();

    public FriendCommand() {
        super("friend", "Manage your friend list.", "f");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder
            .then(literal("add")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        FRIENDS.add(name.toLowerCase());
                        info("Added %s to friends.", name);
                        return SUCCESS;
                    })))
            .then(literal("remove")
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        if (FRIENDS.remove(name.toLowerCase())) {
                            info("Removed %s from friends.", name);
                        } else {
                            error("%s is not a friend.", name);
                        }
                        return SUCCESS;
                    })))
            .then(literal("list")
                .executes(ctx -> {
                    if (FRIENDS.isEmpty()) {
                        info("No friends added.");
                    } else {
                        info("Friends: %s", String.join(", ", FRIENDS));
                    }
                    return SUCCESS;
                }))
            .then(literal("clear")
                .executes(ctx -> {
                    FRIENDS.clear();
                    info("Cleared friend list.");
                    return SUCCESS;
                }));
    }
}
