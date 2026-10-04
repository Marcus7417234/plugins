package io.github.marcus7417234.snakegame.command;

import io.github.marcus7417234.snakegame.SnakeGamePlugin;
import io.github.marcus7417234.snakegame.config.Messages;
import io.github.marcus7417234.snakegame.config.Settings;
import io.github.marcus7417234.snakegame.data.PlayerData;
import io.github.marcus7417234.snakegame.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** /game, /game top [speed], /game stats [player], /game reload, /game help */
public final class GameCommand implements TabExecutor {

    private static final String PLAY_PERMISSION = "snakegame.play";
    private static final String ADMIN_PERMISSION = "snakegame.admin";

    private final SnakeGamePlugin plugin;

    public GameCommand(SnakeGamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "":
            case "play":
                play(sender);
                return true;
            case "top":
                if (requirePermission(sender, PLAY_PERMISSION)) {
                    top(sender, label, args.length > 1 ? args[1] : null);
                }
                return true;
            case "stats":
                if (requirePermission(sender, PLAY_PERMISSION)) {
                    stats(sender, args.length > 1 ? args[1] : null);
                }
                return true;
            case "reload":
                if (requirePermission(sender, ADMIN_PERMISSION)) {
                    plugin.reload();
                    messages.send(sender, "chat.reloaded");
                }
                return true;
            case "help":
                messages.sendList(sender, "chat.help", "label", label);
                if (sender.hasPermission(ADMIN_PERMISSION)) {
                    messages.sendList(sender, "chat.help-admin", "label", label);
                }
                return true;
            default:
                messages.send(sender, "chat.unknown-command", "label", label);
                return true;
        }
    }

    private void play(CommandSender sender) {
        if (!(sender instanceof Player)) {
            plugin.messages().send(sender, "chat.players-only");
            return;
        }
        Player player = (Player) sender;
        if (!requirePermission(player, PLAY_PERMISSION)) {
            return;
        }
        if (player.isDead()) {
            plugin.messages().send(player, "chat.cannot-play-now");
            return;
        }
        plugin.sessions().open(player);
    }

    private void top(CommandSender sender, String label, String speedName) {
        Messages messages = plugin.messages();
        Settings settings = plugin.settings();
        Settings.Speed speed;
        if (speedName == null) {
            speed = sender instanceof Player
                    ? settings.speed(plugin.data().get(((Player) sender).getUniqueId(), sender.getName()).speed())
                    : settings.speed(null);
        } else {
            speed = settings.speeds().get(speedName.toLowerCase(Locale.ROOT));
            if (speed == null) {
                messages.send(sender, "chat.unknown-speed", "speeds", String.join(", ", settings.speeds().keySet()));
                return;
            }
        }
        int size = settings.leaderboardSize;
        List<PlayerData> top = plugin.data().top(speed.id, size);
        sender.sendMessage(messages.get("chat.top-header", "size", size, "speed", speed.name));
        if (top.isEmpty()) {
            sender.sendMessage(messages.get("chat.top-empty", "speed", speed.name, "label", label));
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            int score = top.get(i).best(speed.id);
            sender.sendMessage(messages.get("chat.top-line", "rank", i + 1, "player", top.get(i).name(),
                    "score", score, "apples", messages.apples(score)));
        }
    }

    private void stats(CommandSender sender, String playerName) {
        Messages messages = plugin.messages();
        PlayerData data;
        if (playerName != null) {
            data = plugin.data().find(playerName);
        } else if (sender instanceof Player) {
            data = plugin.data().get(((Player) sender).getUniqueId(), sender.getName());
        } else {
            messages.send(sender, "chat.players-only");
            return;
        }
        if (data == null) {
            messages.send(sender, "chat.unknown-player", "player", playerName);
            return;
        }
        sender.sendMessage(messages.get("chat.stats-header", "player", data.name()));
        messages.sendList(sender, "chat.stats-lines", "games", data.games(), "apples", data.apples(),
                "time", Text.duration(data.secondsPlayed()));
        for (Settings.Speed speed : plugin.settings().speeds().values()) {
            sender.sendMessage(messages.get("chat.stats-best", "speed", speed.name, "score", data.best(speed.id)));
        }
    }

    private boolean requirePermission(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(sender, "chat.no-permission");
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options;
        if (args.length == 1) {
            options = new ArrayList<>(Arrays.asList("top", "stats", "help"));
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                options.add("reload");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("top")) {
            options = new ArrayList<>(plugin.settings().speeds().keySet());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            return null; // online player names
        } else {
            return Collections.emptyList();
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(prefix)) {
                matches.add(option);
            }
        }
        return matches;
    }
}
