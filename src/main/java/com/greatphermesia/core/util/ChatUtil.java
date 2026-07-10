package com.greatphermesia.core.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class ChatUtil {

    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();

    private ChatUtil() {
    }

    public static String color(String input) {
        if (input == null) {
            return "";
        }
        return LEGACY_SECTION.serialize(LEGACY_AMPERSAND.deserialize(input));
    }

    public static Component component(String input) {
        if (input == null || input.isBlank()) {
            return Component.empty();
        }
        return LEGACY_AMPERSAND.deserialize(input);
    }

    public static List<Component> components(List<String> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        return input.stream().map(ChatUtil::component).toList();
    }

    public static String plainText(Component component) {
        if (component == null) {
            return "";
        }
        return PLAIN_TEXT.serialize(component);
    }

    public static String plainPlayerName(String input) {
        if (input == null || input.isBlank()) {
            return "Unknown";
        }
        return plainText(legacyComponent(input));
    }

    private static Component legacyComponent(String input) {
        if (input == null || input.isBlank()) {
            return Component.empty();
        }
        if (input.indexOf('§') >= 0) {
            return LEGACY_SECTION.deserialize(input);
        }
        return LEGACY_AMPERSAND.deserialize(input);
    }
}
