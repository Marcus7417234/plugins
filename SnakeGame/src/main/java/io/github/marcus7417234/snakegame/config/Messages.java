package io.github.marcus7417234.snakegame.config;

import io.github.marcus7417234.snakegame.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Text from messages.yml, falling back to the bundled defaults for missing keys. */
public final class Messages {

    private final YamlConfiguration config;
    private final String prefix;

    public Messages(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource("messages.yml");
        if (defaults != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8)));
        }
        prefix = Text.color(text("prefix"));
    }

    /** A coloured single-line message with placeholders filled in. */
    public String get(String path, Object... placeholders) {
        return Text.color(Text.format(text(path), placeholders));
    }

    /** A message as raw text (colour codes untouched), for item names that are coloured later. */
    public String raw(String path, Object... placeholders) {
        return Text.format(text(path), placeholders);
    }

    /** The text at a path, from messages.yml or else the bundled defaults (shows the path if missing). */
    private String text(String path) {
        // getString(path, default) would skip the bundled defaults, so check for null instead.
        String text = config.getString(path);
        return text != null ? text : path;
    }

    /** A list of lines with placeholders filled in, colour codes untouched. */
    public List<String> rawList(String path, Object... placeholders) {
        List<String> lines = config.isList(path) ? config.getStringList(path) : Collections.<String>emptyList();
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(Text.format(line, placeholders));
        }
        return result;
    }

    public void send(CommandSender sender, String path, Object... placeholders) {
        sender.sendMessage(prefix + get(path, placeholders));
    }

    public void sendList(CommandSender sender, String path, Object... placeholders) {
        for (String line : rawList(path, placeholders)) {
            sender.sendMessage(Text.color(line));
        }
    }

    public String prefix() {
        return prefix;
    }

    /** "apple" or "apples" depending on the count. */
    public String apples(int count) {
        return get(count == 1 ? "chat.apple-singular" : "chat.apple-plural");
    }
}
