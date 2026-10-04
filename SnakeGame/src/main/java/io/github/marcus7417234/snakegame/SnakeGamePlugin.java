package io.github.marcus7417234.snakegame;

import io.github.marcus7417234.snakegame.command.GameCommand;
import io.github.marcus7417234.snakegame.config.Messages;
import io.github.marcus7417234.snakegame.config.Settings;
import io.github.marcus7417234.snakegame.data.DataStore;
import io.github.marcus7417234.snakegame.gui.InventoryVault;
import io.github.marcus7417234.snakegame.gui.SessionManager;
import io.github.marcus7417234.snakegame.heads.HeadDatabaseHook;
import io.github.marcus7417234.snakegame.heads.Icons;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class SnakeGamePlugin extends JavaPlugin {

    private static final String COMMAND = "game";
    private static final long AUTOSAVE_TICKS = 20L * 60 * 5;

    private Settings settings;
    private Messages messages;
    private final Icons icons = new Icons();
    private DataStore data;
    private InventoryVault vault;
    private SessionManager sessions;
    private HeadDatabaseHook headDatabase;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();

        data = new DataStore(this);
        data.load();
        vault = new InventoryVault(this);
        sessions = new SessionManager(this);

        hookHeadDatabase();
        loadIcons();

        getServer().getPluginManager().registerEvents(sessions, this);
        sessions.start();

        GameCommand command = new GameCommand(this);
        PluginCommand pluginCommand = getCommand(COMMAND);
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        Bukkit.getScheduler().runTaskTimer(this, () -> data.saveAsync(), AUTOSAVE_TICKS, AUTOSAVE_TICKS);

        // After a /reload, hand back anything left over from games that were interrupted.
        for (Player player : Bukkit.getOnlinePlayers()) {
            vault.recover(player);
        }
    }

    @Override
    public void onDisable() {
        if (sessions != null) {
            sessions.shutdown();
        }
        if (data != null) {
            data.saveNow();
        }
    }

    private void loadConfiguration() {
        reloadConfig();
        settings = new Settings(getConfig(), getLogger());
        messages = new Messages(this);
    }

    private void hookHeadDatabase() {
        if (!Bukkit.getPluginManager().isPluginEnabled("HeadDatabase")) {
            getLogger().info("HeadDatabase not found - using the fallback items from config.yml.");
            return;
        }
        try {
            headDatabase = new HeadDatabaseHook(this, this::onHeadDatabaseLoaded);
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Could not hook into HeadDatabase - using the fallback items.", t);
            headDatabase = null;
            return;
        }
        if (!headDatabase.isLoaded()) {
            getLogger().info("Waiting for HeadDatabase to finish loading its heads...");
        }
    }

    private void loadIcons() {
        icons.load(settings, headDatabase, getLogger());
    }

    private void onHeadDatabaseLoaded() {
        loadIcons();
        sessions.refreshVisuals();
    }

    /** Reloads config.yml and messages.yml and redraws every open game. */
    public void reload() {
        loadConfiguration();
        loadIcons();
        sessions.refreshVisuals();
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public Icons icons() {
        return icons;
    }

    public DataStore data() {
        return data;
    }

    public InventoryVault vault() {
        return vault;
    }

    public SessionManager sessions() {
        return sessions;
    }

    /** The command players type to play, for use in messages. */
    public String commandLabel() {
        return COMMAND;
    }
}
