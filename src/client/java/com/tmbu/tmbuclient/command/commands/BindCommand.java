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
    private static final Map<String, Integer> MOUSE_MAP = new HashMap<>();

    static {
        // Populate common key names
        for (Field f : GLFW.class.getFields()) {
            if (f.getName().startsWith("GLFW_KEY_")) {
                try {
                    KEY_MAP.put(f.getName().substring(9).toLowerCase(), f.getInt(null));
                } catch (IllegalAccessException ignored) {}
            }
        }
        // Mouse buttons
        MOUSE_MAP.put("mouse1", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        MOUSE_MAP.put("mouse2", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        MOUSE_MAP.put("mouse3", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
        MOUSE_MAP.put("mouse4", GLFW.GLFW_MOUSE_BUTTON_4);
        MOUSE_MAP.put("mouse5", GLFW.GLFW_MOUSE_BUTTON_5);
        MOUSE_MAP.put("mouseleft", GLFW.GLFW_MOUSE_BUTTON_LEFT);
        MOUSE_MAP.put("mouseright", GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        MOUSE_MAP.put("mousemiddle", GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
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
                        m.getKeybindSetting().clear();
                        info("Unbound %s.", m.getName());
                        return SUCCESS;
                    }

                    // Check mouse buttons first
                    Integer mouseBtn = MOUSE_MAP.get(keyName);
                    if (mouseBtn != null) {
                        m.getKeybindSetting().setMouseButton(mouseBtn);
                        info("Bound %s to %s.", m.getName(), m.getKeybindSetting().getDisplayName());
                        return SUCCESS;
                    }

                    Integer key = KEY_MAP.get(keyName);
                    if (key == null) { error("Unknown key '%s'. Use mouse1-5 for mouse buttons.", keyName); return 0; }

                    m.getKeybindSetting().setKey(key);
                    info("Bound %s to %s.", m.getName(), keyName.toUpperCase());
                    return SUCCESS;
                })));
    }
}
