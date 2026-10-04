package io.github.marcus7417234.snakegame.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;

public final class Text {

    private Text() {
    }

    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> color(List<String> lines) {
        List<String> colored = new ArrayList<>(lines.size());
        for (String line : lines) {
            colored.add(color(line));
        }
        return colored;
    }

    /**
     * Replaces {@code {key}} placeholders. Arguments alternate between key and value:
     * {@code format("{a} and {b}", "a", 1, "b", 2)}.
     */
    public static String format(String text, Object... placeholders) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }

    /** Formats a duration as {@code m:ss}, or {@code h:mm:ss} past an hour. */
    public static String clock(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%d:%02d", minutes, seconds);
    }

    /** Formats a duration as a short human readable string such as {@code 1h 5m} or {@code 42s}. */
    public static String duration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }

    /** Lower-cases and strips everything but letters and digits, so "Snake (head)" matches "Snake Head". */
    public static String normalize(String text) {
        String stripped = ChatColor.stripColor(text == null ? "" : text).toLowerCase(java.util.Locale.ROOT);
        StringBuilder builder = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                builder.append(c);
            }
        }
        return builder.toString();
    }
}
