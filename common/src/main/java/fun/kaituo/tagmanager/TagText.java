package fun.kaituo.tagmanager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared legacy-format migration and MiniMessage parsing for every platform. */
public final class TagText {
    private static final Pattern LEGACY_CODE = Pattern.compile("\u00a7[xX](?:\u00a7[0-9a-fA-F]){6}|\u00a7#[0-9a-fA-F]{6}|\u00a7[0-9a-fA-Fk-oK-OrR]");
    private static final String[] COLORS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    };
    private static final MiniMessage PARSER = MiniMessage.miniMessage();
    private static final MiniMessage SERIALIZER = MiniMessage.builder().strict(true).build();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .preProcessor(TagText::convertLegacyCodes)
            .build();

    private TagText() { }

    public static MiniMessage miniMessage() {
        return MINI_MESSAGE;
    }

    /** Serializes an assigned prefix with an explicit, enclosing hover tag. */
    public static String serializePrefix(Tag tag) {
        Component prefix = MINI_MESSAGE.deserialize(tag.prefix);
        if (tag.description == null) return MINI_MESSAGE.serialize(prefix);
        Component hovered = Component.empty()
                .hoverEvent(HoverEvent.showText(MINI_MESSAGE.deserialize(tag.description)))
                .append(prefix.hoverEvent(null));
        return SERIALIZER.serialize(hovered);
    }

    /** Converts section-sign codes to MiniMessage with named, balanced tags. */
    public static String convertLegacyCodes(String text) {
        Matcher legacy = LEGACY_CODE.matcher(text);
        if (!legacy.find()) return text;
        String converted = legacy.replaceAll(match -> {
            String code = match.group();
            char value = Character.toLowerCase(code.charAt(1));
            String replacement;
            if (value == 'x') {
                StringBuilder hex = new StringBuilder("<reset><#");
                for (int index = 3; index < code.length(); index += 2) hex.append(code.charAt(index));
                replacement = hex.append('>').toString();
            } else if (value == '#') {
                replacement = "<reset><" + code.substring(1) + ">";
            } else {
                int color = Character.digit(value, 16);
                replacement = color >= 0 ? "<reset><" + COLORS[color] + ">" : switch (value) {
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> throw new IllegalStateException("Unexpected legacy format code: " + code);
                };
            }
            return Matcher.quoteReplacement(replacement);
        });
        // Serializing the parsed styles produces named, balanced tags instead of
        // leaving legacy resets or open formatting scopes in stored tag data.
        return SERIALIZER.serialize(PARSER.deserialize(converted));
    }
}
