package com.tmbu.tmbuclient.command.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.tmbu.tmbuclient.TmbuClient;
import com.tmbu.tmbuclient.command.Command;
import com.tmbu.tmbuclient.module.Module;
import net.minecraft.commands.SharedSuggestionProvider;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class BindCommand extends Command {

    private static final Map<String, Integer> KEY_MAP = new HashMap<>();

    static {
        // Populate common key names
        for (Field f : GLFW.class.getFields()) {
            if (f.getName().startsWith("GLFW_KEY_")) {
                try {
                    KEY_MAP.put(f.getName().substring(9).toLowerCase(), f.getInt(null));
                } catch (IllegalAccessException ignored) {}
            }
        }
    }

    public BindCommand() {
        super("bind", "Bind a module to a key.", "b");
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

        builder.then(argument("module", StringArgumentType.word())
            .suggests(moduleSuggestions)
            .then(argument("key", StringArgumentType.word())
                .executes(ctx -> {
                    String moduleName = StringArgumentType.getString(ctx, "module");
                    String keyName = StringArgumentType.getString(ctx, "key").toLowerCase();

                    Module m = TmbuClient.INSTANCE.getModuleManager().getModuleByName(moduleName);
                    if (m == null) { error("Module '%s' not found.", moduleName); return 0; }

                    if (keyName.equals("none") || keyName.equals("unbind")) {
                        m.getKeybindSetting().setValue(GLFW.GLFW_KEY_UNKNOWN);
                        info("Unbound %s.", m.getName());
                        return SUCCESS;
                    }

                    Integer key = KEY_MAP.get(keyName);
                    if (key == null) { error("Unknown key '%s'.", keyName); return 0; }

                    m.getKeybindSetting().setValue(key);
                    info("Bound %s to %s.", m.getName(), keyName.toUpperCase());
                    return SUCCESS;
                })));
    }
}
