package com.tmbu.tmbuclient.mixin.client;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.tmbu.tmbuclient.command.CommandManager;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.brigadier.context.StringRange;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.ArrayList;

@Mixin(CommandSuggestions.class)
public class ChatScreenMixin {

    @Shadow private ParseResults<SharedSuggestionProvider> currentParse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean keepSuggestions;
    @Shadow private EditBox input;

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void onUpdateCommandInfo(CallbackInfo ci) {
        String text = input.getValue();
        String prefix = CommandManager.INSTANCE.getPrefix();

        if (!text.startsWith(prefix)) return;

        var source = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (source == null) return;

        String withoutPrefix = text.substring(prefix.length());
        var dispatcher = CommandManager.INSTANCE.getDispatcher();
        int prefixLen = prefix.length();

        currentParse = dispatcher.parse(withoutPrefix, source.getSuggestionsProvider());

        if (!keepSuggestions) {
            int cursor = Math.max(0, input.getCursorPosition() - prefixLen);
            // Get suggestions from our dispatcher, then offset the ranges by prefix length
            // so they align with the actual EditBox text
            pendingSuggestions = dispatcher.getCompletionSuggestions(currentParse, cursor)
                .thenApply(suggestions -> {
                    StringRange range = suggestions.getRange();
                    // Offset the range to account for the prefix in the EditBox
                    StringRange adjusted = new StringRange(range.getStart() + prefixLen, range.getEnd() + prefixLen);
                    List<Suggestion> adjusted_list = new ArrayList<>();
                    for (Suggestion s : suggestions.getList()) {
                        adjusted_list.add(new Suggestion(
                            new StringRange(s.getRange().getStart() + prefixLen, s.getRange().getEnd() + prefixLen),
                            s.getText(), s.getTooltip()));
                    }
                    return new Suggestions(adjusted, adjusted_list);
                });
        }

        ci.cancel();
    }
}
