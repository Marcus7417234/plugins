package io.github.marcus7417234.snakegame.data;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Stores every player's statistics and preferences in data.yml. */
public final class DataStore {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private final Object writeLock = new Object();
    private boolean dirty;
    private long snapshotVersion;
    private long writtenVersion;

    public DataStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        players.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("players");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException e) {
                continue;
            }
            ConfigurationSection entry = section.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            PlayerData data = new PlayerData(uuid, entry.getString("name", "?"));
            data.sound = entry.getBoolean("sound", true);
            data.speed = entry.getString("speed");
            data.theme = entry.getString("theme");
            data.games = entry.getInt("games");
            data.apples = entry.getLong("apples");
            data.secondsPlayed = entry.getLong("seconds-played");
            ConfigurationSection best = entry.getConfigurationSection("best");
            if (best != null) {
                for (String speed : best.getKeys(false)) {
                    data.best.put(speed, best.getInt(speed));
                }
            }
            players.put(uuid, data);
        }
    }

    /** The player's data, created on first use. */
    public PlayerData get(UUID uuid, String name) {
        PlayerData data = players.get(uuid);
        if (data == null) {
            data = new PlayerData(uuid, name);
            players.put(uuid, data);
        } else if (name != null && !name.equals(data.name)) {
            data.name = name;
            dirty = true;
        }
        return data;
    }

    /** Finds a player who has played before by name, ignoring case. */
    public PlayerData find(String name) {
        for (PlayerData data : players.values()) {
            if (data.name != null && data.name.equalsIgnoreCase(name)) {
                return data;
            }
        }
        return null;
    }

    public void setSound(PlayerData data, boolean sound) {
        data.sound = sound;
        dirty = true;
    }

    public void setSpeed(PlayerData data, String speed) {
        data.speed = speed;
        dirty = true;
    }

    public void setTheme(PlayerData data, String theme) {
        data.theme = theme;
        dirty = true;
    }

    /** Records a finished round and reports whether it set a personal or server record. */
    public GameResult record(PlayerData data, String speed, int score, long seconds) {
        int serverBestBefore = 0;
        for (PlayerData other : players.values()) {
            if (other != data) {
                serverBestBefore = Math.max(serverBestBefore, other.best(speed));
            }
        }
        int previousBest = data.best(speed);
        data.games++;
        data.apples += score;
        data.secondsPlayed += seconds;
        boolean newBest = score > previousBest;
        if (newBest) {
            data.best.put(speed, score);
        }
        dirty = true;
        return new GameResult(previousBest, newBest, newBest && score > serverBestBefore);
    }

    /** The best players for a speed, highest score first. */
    public List<PlayerData> top(String speed, int limit) {
        List<PlayerData> ranked = new ArrayList<>();
        for (PlayerData data : players.values()) {
            if (data.best(speed) > 0) {
                ranked.add(data);
            }
        }
        ranked.sort((a, b) -> Integer.compare(b.best(speed), a.best(speed)));
        return ranked.size() > limit ? new ArrayList<>(ranked.subList(0, limit)) : ranked;
    }

    /** Saves if anything changed, writing the file off the main thread. */
    public void saveAsync() {
        if (!dirty) {
            return;
        }
        final String content = serialize();
        final long version = snapshotVersion;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(content, version));
    }

    /** Saves right away on the calling thread (used when the plugin shuts down). */
    public void saveNow() {
        if (dirty) {
            write(serialize(), snapshotVersion);
        }
    }

    private String serialize() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<PlayerData> sorted = new ArrayList<>(players.values());
        Collections.sort(sorted, (a, b) -> a.uuid().compareTo(b.uuid()));
        for (PlayerData data : sorted) {
            String path = "players." + data.uuid() + ".";
            yaml.set(path + "name", data.name);
            yaml.set(path + "sound", data.sound);
            yaml.set(path + "speed", data.speed);
            yaml.set(path + "theme", data.theme);
            yaml.set(path + "games", data.games);
            yaml.set(path + "apples", data.apples);
            yaml.set(path + "seconds-played", data.secondsPlayed);
            for (Map.Entry<String, Integer> best : data.best.entrySet()) {
                yaml.set(path + "best." + best.getKey(), best.getValue());
            }
        }
        dirty = false;
        snapshotVersion++;
        return yaml.saveToString();
    }

    private void write(String content, long version) {
        synchronized (writeLock) {
            if (version <= writtenVersion) {
                return; // a newer snapshot is already on disk
            }
            try {
                File folder = file.getParentFile();
                if (!folder.exists() && !folder.mkdirs()) {
                    throw new IOException("Could not create " + folder);
                }
                File temp = new File(folder, "data.yml.tmp");
                try (Writer writer = new OutputStreamWriter(Files.newOutputStream(temp.toPath()), StandardCharsets.UTF_8)) {
                    writer.write(content);
                }
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                writtenVersion = version;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save " + file.getName(), e);
            }
        }
    }

    /** What a finished round meant for the player's records. */
    public static final class GameResult {
        public final int previousBest;
        public final boolean newBest;
        public final boolean serverRecord;

        GameResult(int previousBest, boolean newBest, boolean serverRecord) {
            this.previousBest = previousBest;
            this.newBest = newBest;
            this.serverRecord = serverRecord;
        }
    }
}
