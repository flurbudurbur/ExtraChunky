package dev.flur.extrachunky.platform;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

public class NeoForgeSender implements ExtraChunkySender {
    private final CommandSourceStack source;

    public NeoForgeSender(CommandSourceStack source) {
        this.source = source;
    }

    @Override
    public void sendMessage(String message) {
        source.sendSystemMessage(parseColorCodes(message));
    }

    /**
     * Parses Minecraft color codes (&) and converts them to Component styling.
     */
    private static Component parseColorCodes(String message) {
        MutableComponent result = Component.empty();
        Style currentStyle = Style.EMPTY;
        StringBuilder currentText = new StringBuilder();

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '&' && i + 1 < message.length()) {
                char code = message.charAt(i + 1);
                ChatFormatting formatting = getFormatting(code);
                if (formatting != null) {
                    if (currentText.length() > 0) {
                        result.append(Component.literal(currentText.toString()).withStyle(currentStyle));
                        currentText = new StringBuilder();
                    }
                    if (code == 'r') {
                        currentStyle = Style.EMPTY;
                    } else if (formatting.isColor()) {
                        currentStyle = Style.EMPTY.withColor(formatting);
                    } else {
                        currentStyle = currentStyle.applyFormat(formatting);
                    }
                    i++;
                    continue;
                }
            }
            currentText.append(c);
        }

        if (currentText.length() > 0) {
            result.append(Component.literal(currentText.toString()).withStyle(currentStyle));
        }

        return result;
    }

    private static ChatFormatting getFormatting(char code) {
        return switch (code) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            case 'k' -> ChatFormatting.OBFUSCATED;
            case 'l' -> ChatFormatting.BOLD;
            case 'm' -> ChatFormatting.STRIKETHROUGH;
            case 'n' -> ChatFormatting.UNDERLINE;
            case 'o' -> ChatFormatting.ITALIC;
            case 'r' -> ChatFormatting.RESET;
            default -> null;
        };
    }

    @Override
    public String getName() {
        return source.getTextName();
    }

    @Override
    public boolean hasPermission(String permission) {
        // In NeoForge, we check op level 2 (game master commands)
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    @Override
    public boolean isPlayer() {
        return source.getEntity() instanceof ServerPlayer;
    }

    public CommandSourceStack getSource() {
        return source;
    }
}
