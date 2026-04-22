package com.alinvite.utils;

import java.util.HashMap;
import java.util.Map;

public class ColorUtil {
    private static final Map<Character, String> COLORS = new HashMap<>();

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
        StringBuilder result = new StringBuilder();
        boolean skipNext = false;

        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);

            if (skipNext) {
                if (c == '&') {
                    result.append('&');
                } else {
                    String color = COLORS.get(c);
                    if (color != null) {
                        result.append(color);
                    }
                }
                skipNext = false;
            } else if (c == '&') {
                skipNext = true;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    public static void log(java.util.logging.Logger logger, String message) {
        logger.info(translate(message));
    }
}