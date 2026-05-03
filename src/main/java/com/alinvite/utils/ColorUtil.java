package com.alinvite.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.HashMap;
import java.util.Map;

public class ColorUtil {
    private static final Map<Character, String> COLORS = new HashMap<>();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    static {
        COLORS.put('0', "\u001b[30m");
        COLORS.put('1', "\u001b[34m");
        COLORS.put('2', "\u001b[32m");
        COLORS.put('3', "\u001b[36m");
        COLORS.put('4', "\u001b[31m");
        COLORS.put('5', "\u001b[35m");
        COLORS.put('6', "\u001b[33m");
        COLORS.put('7', "\u001b[37m");
        COLORS.put('8', "\u001b[90m");
        COLORS.put('9', "\u001b[94m");
        COLORS.put('a', "\u001b[92m");
        COLORS.put('b', "\u001b[96m");
        COLORS.put('c', "\u001b[91m");
        COLORS.put('d', "\u001b[95m");
        COLORS.put('e', "\u001b[93m");
        COLORS.put('f', "\u001b[97m");
        COLORS.put('r', "\u001b[0m");
        COLORS.put('l', "\u001b[1m");
        COLORS.put('o', "\u001b[3m");
        COLORS.put('n', "\u001b[4m");
        COLORS.put('m', "\u001b[9m");
        COLORS.put('k', "\u001b[5m");
    }

    public static String translate(String message) {
        if (message == null) return null;
        return translateLegacy(message);
    }

    public static String translateLegacy(String message) {
        if (message == null) return null;
        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < message.length()) {
            char c = message.charAt(i);

            if (c == '&' && i + 1 < message.length()) {
                char next = message.charAt(i + 1);
                if (next == 'x' && i + 7 < message.length()) {
                    result.append(translateRGB(message.substring(i, i + 8)));
                    i += 8;
                } else {
                    String color = COLORS.get(next);
                    if (color != null) {
                        result.append(color);
                    }
                    i += 2;
                }
            } else {
                result.append(c);
                i++;
            }
        }

        return result.toString();
    }

    private static String translateRGB(String rgbCode) {
        try {
            int r = Integer.parseInt(String.valueOf(rgbCode.charAt(2) + rgbCode.charAt(3)), 16);
            int g = Integer.parseInt(String.valueOf(rgbCode.charAt(4) + rgbCode.charAt(5)), 16);
            int b = Integer.parseInt(String.valueOf(rgbCode.charAt(6) + rgbCode.charAt(7)), 16);
            return "\u001b[38;2;" + r + ";" + g + ";" + b + "m";
        } catch (NumberFormatException e) {
            return rgbCode;
        }
    }

    public static Component parseMiniMessage(String message) {
        if (message == null) return null;
        return MINI_MESSAGE.deserialize(message);
    }

    public static String serializeMiniMessage(Component component) {
        if (component == null) return null;
        return MINI_MESSAGE.serialize(component);
    }

    public static String toLegacy(String message) {
        if (message == null) return null;
        Component component = parseMiniMessage(message);
        return LEGACY_SERIALIZER.serialize(component);
    }

    public static void log(java.util.logging.Logger logger, String message) {
        logger.info(translate(message));
    }
}