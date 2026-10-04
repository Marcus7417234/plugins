package io.github.marcus7417234.snakegame.gui;

import io.github.marcus7417234.snakegame.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Keeps a player's inventory safe while the game's controls are shown in it.
 * <p>
 * The items are also written to {@code inventories/<uuid>.yml} until they are handed back,
 * so nothing is lost if the server stops unexpectedly mid-game.
 */
public final class InventoryVault {

    /** Main inventory and hotbar; armour is never touched. */
    public static final int SIZE = 36;

    private final Plugin plugin;
    private final File folder;

    public InventoryVault(Plugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "inventories");
    }

    /**
     * Takes the player's items out of their inventory and stores them.
     *
     * @return the stored items, or null if they could not be saved (the inventory is then left alone)
     */
    public ItemStack[] store(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] items = new ItemStack[SIZE];
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack item = inventory.getItem(slot);
            items[slot] = item == null ? null : item.clone();
        }
        try {
            save(player, items);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not back up the inventory of " + player.getName()
                    + ", so it was left untouched.", e);
            return null;
        }
        for (int slot = 0; slot < SIZE; slot++) {
            inventory.setItem(slot, null);
        }
        return items;
    }

    /**
     * Puts stored items back. Menu items are removed, and anything else the player
     * received while playing is kept (or dropped at their feet if there is no room).
     */
    public void restore(Player player, ItemStack[] items) {
        PlayerInventory inventory = player.getInventory();
        List<ItemStack> received = new ArrayList<>();
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current != null && current.getType() != Material.AIR && !ItemBuilder.isMenuItem(current)) {
                received.add(current);
            }
            inventory.setItem(slot, items[slot]);
        }
        giveOrDrop(player, received);
        delete(player);
    }

    /**
     * Hands back items left over from a game that never finished properly (a crash).
     * <p>
     * If the server last saved the player while they were playing, their saved inventory
     * holds the menu's items, which are swapped for the stored ones. If it last saved them
     * before the game started, their real items are already there, and the backup is
     * dropped so nothing gets duplicated.
     */
    public boolean recover(Player player) {
        File file = file(player);
        if (!file.exists()) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        boolean savedMidGame = false;
        for (int slot = 0; slot < SIZE; slot++) {
            if (ItemBuilder.isMenuItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
                savedMidGame = true;
            }
        }
        if (!savedMidGame) {
            delete(player);
            plugin.getLogger().info(player.getName() + " already has their items from before an unfinished Snake game;"
                    + " removed the outdated backup.");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the stored Snake inventory of " + player.getName()
                    + " (" + file + "). Please give the items back by hand.", e);
            return false;
        }
        List<ItemStack> overflow = new ArrayList<>();
        for (int slot = 0; slot < SIZE; slot++) {
            ItemStack item = yaml.getItemStack("items." + slot);
            if (item == null) {
                continue;
            }
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, item);
            } else {
                overflow.add(item);
            }
        }
        giveOrDrop(player, overflow);
        delete(player);
        plugin.getLogger().info("Gave " + player.getName() + " back the items stored during an unfinished Snake game.");
        return true;
    }

    /** Forgets the stored items, e.g. when they were dropped on death instead. */
    public void delete(Player player) {
        File file = file(player);
        if (file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete " + file);
        }
    }

    private void giveOrDrop(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(items.toArray(new ItemStack[0]));
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void save(Player player, ItemStack[] items) throws IOException {
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Could not create " + folder);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("player", player.getName());
        for (int slot = 0; slot < SIZE; slot++) {
            if (items[slot] != null) {
                yaml.set("items." + slot, items[slot]);
            }
        }
        Files.write(file(player).toPath(), yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }

    private File file(Player player) {
        return new File(folder, player.getUniqueId() + ".yml");
    }
}
