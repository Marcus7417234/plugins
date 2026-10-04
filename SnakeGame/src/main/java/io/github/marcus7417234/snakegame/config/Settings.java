package io.github.marcus7417234.snakegame.config;

import io.github.marcus7417234.snakegame.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Everything read from config.yml, validated once per (re)load. */
public final class Settings {

    public final int startLength;
    public final int growthPerApple;
    public final boolean walls;
    public final String defaultSpeed;
    public final String defaultTheme;
    public final double speedUpPerApple;
    public final double maxMovesPerSecond;
    public final boolean usePlayerInventory;
    public final boolean pauseOnClose;
    public final long pausedTimeoutMillis;
    public final boolean broadcastServerRecord;
    public final int leaderboardSize;
    public final int eatPitchSteps;

    private final Map<String, Speed> speeds;
    private final Map<String, Theme> themes;
    private final Map<String, ItemSpec> items;
    private final Map<String, SoundEffect> sounds;

    public Settings(FileConfiguration config, Logger logger) {
        startLength = clamp(config.getInt("game.start-length", 3), 1, 6);
        growthPerApple = clamp(config.getInt("game.growth-per-apple", 1), 1, 10);
        walls = config.getBoolean("game.walls", true);
        speedUpPerApple = Math.max(0, config.getDouble("speed-up.per-apple", 0.05));
        maxMovesPerSecond = clamp(config.getDouble("speed-up.max-moves-per-second", 8.0), 0.5, 20.0);
        usePlayerInventory = config.getBoolean("menu.use-player-inventory", true);
        pauseOnClose = config.getBoolean("menu.pause-on-close", true);
        pausedTimeoutMillis = Math.max(1, config.getLong("menu.paused-game-timeout-minutes", 10)) * 60_000L;
        broadcastServerRecord = config.getBoolean("leaderboard.broadcast-server-record", true);
        leaderboardSize = clamp(config.getInt("leaderboard.size", 10), 1, 50);
        eatPitchSteps = clamp(config.getInt("sounds.eat-pitch-steps", 20), 0, 100);

        speeds = Collections.unmodifiableMap(loadSpeeds(config.getConfigurationSection("speeds"), logger));
        themes = Collections.unmodifiableMap(loadThemes(config.getConfigurationSection("themes"), logger));
        items = Collections.unmodifiableMap(loadItems(config.getConfigurationSection("items"), logger));

        Map<String, SoundEffect> soundMap = new LinkedHashMap<>();
        ConfigurationSection soundSection = config.getConfigurationSection("sounds");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                if (soundSection.isString(key)) {
                    soundMap.put(key, SoundEffect.parse(soundSection.getString(key), "sounds." + key, logger));
                }
            }
        }
        sounds = Collections.unmodifiableMap(soundMap);

        String speed = config.getString("game.default-speed", "normal").toLowerCase(Locale.ROOT);
        if (!speeds.containsKey(speed)) {
            logger.warning("game.default-speed '" + speed + "' is not a configured speed.");
            speed = speeds.keySet().iterator().next();
        }
        defaultSpeed = speed;
        String theme = config.getString("game.default-theme", "grass").toLowerCase(Locale.ROOT);
        if (!themes.containsKey(theme)) {
            logger.warning("game.default-theme '" + theme + "' is not a configured theme.");
            theme = themes.keySet().iterator().next();
        }
        defaultTheme = theme;
    }

    private static Map<String, Speed> loadSpeeds(ConfigurationSection section, Logger logger) {
        Map<String, Speed> result = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String id = key.toLowerCase(Locale.ROOT);
                double moves = section.getDouble(key + ".moves-per-second", 3.5);
                if (moves <= 0) {
                    logger.warning("speeds." + key + ".moves-per-second must be above 0.");
                    continue;
                }
                result.put(id, new Speed(id, result.size(), Text.color(section.getString(key + ".name", key)), moves));
            }
        }
        if (result.isEmpty()) {
            result.put("normal", new Speed("normal", 0, Text.color("&eNormal"), 3.5));
        }
        return result;
    }

    private static Map<String, Theme> loadThemes(ConfigurationSection section, Logger logger) {
        Map<String, Theme> result = new LinkedHashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String id = key.toLowerCase(Locale.ROOT);
                Material icon = material(section.getString(key + ".icon"), Material.GRASS, "themes." + key + ".icon", logger);
                result.put(id, new Theme(id, Text.color(section.getString(key + ".name", key)), icon,
                        clamp(section.getInt(key + ".light", 5), 0, 15),
                        clamp(section.getInt(key + ".dark", 13), 0, 15)));
            }
        }
        if (result.isEmpty()) {
            result.put("grass", new Theme("grass", Text.color("&aGrass"), Material.GRASS, 5, 13));
        }
        return result;
    }

    private static Map<String, ItemSpec> loadItems(ConfigurationSection section, Logger logger) {
        Map<String, ItemSpec> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            List<String> hdb = new ArrayList<>();
            if (section.isList(key + ".hdb")) {
                for (Object entry : section.getList(key + ".hdb")) {
                    if (entry != null) {
                        hdb.add(String.valueOf(entry));
                    }
                }
            } else if (section.isSet(key + ".hdb")) {
                hdb.add(section.getString(key + ".hdb"));
            }
            Material material = material(section.getString(key + ".material"), Material.STONE, "items." + key + ".material", logger);
            result.put(key, new ItemSpec(hdb, material, (short) section.getInt(key + ".data", 0)));
        }
        return result;
    }

    private static Material material(String name, Material fallback, String path, Logger logger) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        if (material == null) {
            logger.warning("Unknown material '" + name + "' at " + path + ", using " + fallback + ".");
            return fallback;
        }
        return material;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public Map<String, Speed> speeds() {
        return speeds;
    }

    /** The speed with this id, or the default speed. */
    public Speed speed(String id) {
        Speed speed = id == null ? null : speeds.get(id);
        return speed != null ? speed : speeds.get(defaultSpeed);
    }

    public Speed nextSpeed(Speed current) {
        return next(new ArrayList<>(speeds.values()), current);
    }

    public Map<String, Theme> themes() {
        return themes;
    }

    /** The theme with this id, or the default theme. */
    public Theme theme(String id) {
        Theme theme = id == null ? null : themes.get(id);
        return theme != null ? theme : themes.get(defaultTheme);
    }

    public Theme nextTheme(Theme current) {
        return next(new ArrayList<>(themes.values()), current);
    }

    private static <T> T next(List<T> values, T current) {
        int index = values.indexOf(current);
        return values.get((index + 1) % values.size());
    }

    /** The item spec for a key under "items", or a plain stone item if it is missing. */
    public ItemSpec item(String key) {
        ItemSpec spec = items.get(key);
        return spec != null ? spec : new ItemSpec(Collections.<String>emptyList(), Material.STONE, (short) 0);
    }

    public SoundEffect sound(String key) {
        SoundEffect effect = sounds.get(key);
        return effect != null ? effect : SoundEffect.NONE;
    }

    /** A selectable game speed. */
    public static final class Speed {
        public final String id;
        public final int index;
        public final String name;
        public final double movesPerSecond;

        Speed(String id, int index, String name, double movesPerSecond) {
            this.id = id;
            this.index = index;
            this.name = name;
            this.movesPerSecond = movesPerSecond;
        }
    }

    /** A board colour theme. */
    public static final class Theme {
        public final String id;
        public final String name;
        public final Material icon;
        public final int lightPane;
        public final int darkPane;

        Theme(String id, String name, Material icon, int lightPane, int darkPane) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.lightPane = lightPane;
            this.darkPane = darkPane;
        }
    }

    /** Where an item's look comes from: HeadDatabase heads first, then a plain material. */
    public static final class ItemSpec {
        public final List<String> hdb;
        public final Material material;
        public final short data;

        ItemSpec(List<String> hdb, Material material, short data) {
            this.hdb = Collections.unmodifiableList(hdb);
            this.material = material;
            this.data = data;
        }
    }
}
