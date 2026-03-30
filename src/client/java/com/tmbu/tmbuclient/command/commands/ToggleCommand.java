package com.tmbu.tmbuclient.command.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tmbu.tmbuclient.TmbuClient;
import com.tmbu.tmbuclient.command.Command;
import com.tmbu.tmbuclient.module.Module;
import net.minecraft.commands.SharedSuggestionProvider;

public class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", "Toggles a module on or off.", "t");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        SuggestionProvider<SharedSuggestionProvider> moduleSuggestions = (ctx, sb) -> {
            String remaining = sb.getRemaining().toLowerCase();
            for (Module m : TmbuClient.INSTANCE.getModuleManager().getModules()) {
                if (m.getName().toLowerCase().startsWith(remaining)) {
                    sb.suggest(m.getName());
                }
            }
            return sb.buildFuture();
        };

        builder.then(argument("module", StringArgumentType.greedyString())
            .suggests(moduleSuggestions)
            .executes(ctx -> {
                String name = StringArgumentType.getString(ctx, "module");
                Module m = TmbuClient.INSTANCE.getModuleManager().getModuleByName(name);
                if (m == null) {
                    error("Module '%s' not found.", name);
                    return 0;
                }
                m.toggle();
                info("%s %s.", m.getName(), m.isEnabled() ? "enabled" : "disabled");
                return SUCCESS;
            }));
    }
}
