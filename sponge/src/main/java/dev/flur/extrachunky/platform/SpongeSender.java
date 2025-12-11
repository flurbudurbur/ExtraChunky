package dev.flur.extrachunky.platform;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.service.permission.Subject;

public class SpongeSender implements ExtraChunkySender {
    private final Object source;

    public SpongeSender(Object source) {
        this.source = source;
    }

    @Override
    public void sendMessage(String message) {
        if (source instanceof Audience audience) {
            audience.sendMessage(Identity.nil(), parseColorCodes(message));
        }
    }

    /**
     * Parses Minecraft color codes (&) and converts them to Adventure Component styling.
     */
    private static Component parseColorCodes(String message) {
        TextComponent.Builder result = Component.text();
        Style.Builder currentStyle = Style.style();
        StringBuilder currentText = new StringBuilder();

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '&' && i + 1 < message.length()) {
                char code = message.charAt(i + 1);
                if (isValidCode(code)) {
                    if (currentText.length() > 0) {
                        result.append(Component.text(currentText.toString(), currentStyle.build()));
                        currentText = new StringBuilder();
                    }
                    if (code == 'r') {
                        currentStyle = Style.style();
                    } else {
                        TextColor color = getColor(code);
                        if (color != null) {
                            currentStyle = Style.style().color(color);
                        } else {
                            TextDecoration decoration = getDecoration(code);
                            if (decoration != null) {
                                currentStyle.decoration(decoration, true);
                            }
                        }
                    }
                    i++;
                    continue;
                }
            }
            currentText.append(c);
        }

        if (currentText.length() > 0) {
            result.append(Component.text(currentText.toString(), currentStyle.build()));
        }

        return result.build();
    }

    private static boolean isValidCode(char code) {
        return (code >= '0' && code <= '9') ||
               (code >= 'a' && code <= 'f') ||
               (code >= 'k' && code <= 'o') ||
               code == 'r';
    }

    private static TextColor getColor(char code) {
        return switch (code) {
            case '0' -> NamedTextColor.BLACK;
            case '1' -> NamedTextColor.DARK_BLUE;
            case '2' -> NamedTextColor.DARK_GREEN;
            case '3' -> NamedTextColor.DARK_AQUA;
            case '4' -> NamedTextColor.DARK_RED;
            case '5' -> NamedTextColor.DARK_PURPLE;
            case '6' -> NamedTextColor.GOLD;
            case '7' -> NamedTextColor.GRAY;
            case '8' -> NamedTextColor.DARK_GRAY;
            case '9' -> NamedTextColor.BLUE;
            case 'a' -> NamedTextColor.GREEN;
            case 'b' -> NamedTextColor.AQUA;
            case 'c' -> NamedTextColor.RED;
            case 'd' -> NamedTextColor.LIGHT_PURPLE;
            case 'e' -> NamedTextColor.YELLOW;
            case 'f' -> NamedTextColor.WHITE;
            default -> null;
        };
    }

    private static TextDecoration getDecoration(char code) {
        return switch (code) {
            case 'k' -> TextDecoration.OBFUSCATED;
            case 'l' -> TextDecoration.BOLD;
            case 'm' -> TextDecoration.STRIKETHROUGH;
            case 'n' -> TextDecoration.UNDERLINED;
            case 'o' -> TextDecoration.ITALIC;
            default -> null;
        };
    }

    @Override
    public String getName() {
        if (source instanceof Subject subject) {
            return subject.friendlyIdentifier().orElse("Unknown");
        }
        return "Console";
    }

    @Override
    public boolean hasPermission(String permission) {
        if (source instanceof Subject subject) {
            return subject.hasPermission(permission);
        }
        return false;
    }

    @Override
    public boolean isPlayer() {
        return source instanceof ServerPlayer;
    }

    public Object getSource() {
        return source;
    }
}
