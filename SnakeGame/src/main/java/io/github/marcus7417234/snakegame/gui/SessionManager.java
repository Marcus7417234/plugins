package io.github.marcus7417234.snakegame.gui;

import io.github.marcus7417234.snakegame.SnakeGamePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps track of everyone's games, runs the game loop and guards the menu. */
public final class SessionManager implements Listener {

    private final SnakeGamePlugin plugin;
    private final Map<UUID, GameSession> sessions = new HashMap<>();
    private BukkitTask task;
    private long ticks;

    public SessionManager(SnakeGamePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Closes every menu, giving items back and saving unfinished rounds. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (GameSession session : new ArrayList<>(sessions.values())) {
            session.forceClose();
            session.end();
        }
        sessions.clear();
    }

    /** Opens the game for a player, continuing their paused game if they have one. */
    public void open(Player player) {
        GameSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            session = new GameSession(plugin, player);
            sessions.put(player.getUniqueId(), session);
        }
        if (!session.open(player) && !session.isInProgress()) {
            sessions.remove(player.getUniqueId());
        }
    }

    /** Redraws every game with fresh settings and icons. */
    public void refreshVisuals() {
        for (GameSession session : sessions.values()) {
            session.refreshVisuals();
        }
    }

    private void tick() {
        ticks++;
        for (GameSession session : new ArrayList<>(sessions.values())) {
            session.tick();
        }
        if (ticks % 20 == 0) {
            expirePausedGames();
        }
    }

    private void expirePausedGames() {
        long now = System.currentTimeMillis();
        long timeout = plugin.settings().pausedTimeoutMillis;
        for (GameSession session : new ArrayList<>(sessions.values())) {
            if (!session.isOpen() && now - session.pausedSince() > timeout) {
                sessions.remove(session.playerId());
                session.end();
                Player player = Bukkit.getPlayer(session.playerId());
                if (player != null) {
                    plugin.messages().send(player, "chat.game-expired", "score", session.score());
                }
            }
        }
    }

    private static GameSession sessionOf(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof GameSession
                ? (GameSession) inventory.getHolder() : null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        GameSession session = sessionOf(event.getView().getTopInventory());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player && event.getWhoClicked().getUniqueId().equals(session.playerId())) {
            session.handleClick(event.getRawSlot(), event.getClick(), event.getHotbarButton());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (sessionOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        GameSession session = sessionOf(event.getInventory());
        HumanEntity human = event.getPlayer();
        if (session == null || !(human instanceof Player) || !human.getUniqueId().equals(session.playerId())) {
            return;
        }
        Player player = (Player) human;
        if (!session.onMenuClosed(player)) {
            return;
        }
        if (session.isInProgress() && plugin.settings().pauseOnClose) {
            plugin.messages().send(player, "chat.game-paused", "label", plugin.commandLabel());
            return;
        }
        sessions.remove(session.playerId());
        session.end();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        GameSession session = sessions.get(event.getEntity().getUniqueId());
        if (session != null && session.isOpen()) {
            session.handleDeath(event);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        // Picked up items would land between the menu's items while playing.
        GameSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.isOpen()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        GameSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.onMenuClosed(player);
            session.end();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        plugin.vault().recover(event.getPlayer());
    }
}
