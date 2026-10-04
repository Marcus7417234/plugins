package io.github.marcus7417234.snakegame.gui;

import io.github.marcus7417234.snakegame.SnakeGamePlugin;
import io.github.marcus7417234.snakegame.config.Messages;
import io.github.marcus7417234.snakegame.config.Settings;
import io.github.marcus7417234.snakegame.data.DataStore;
import io.github.marcus7417234.snakegame.data.PlayerData;
import io.github.marcus7417234.snakegame.engine.Direction;
import io.github.marcus7417234.snakegame.engine.SnakeEngine;
import io.github.marcus7417234.snakegame.heads.Icons;
import io.github.marcus7417234.snakegame.util.ItemBuilder;
import io.github.marcus7417234.snakegame.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * One player's game: the menu, the snake and everything drawn on it.
 * <p>
 * A session lives while the menu is open, and (if enabled) a while longer when the
 * player closes the menu mid-game, so they can continue with /game.
 */
public final class GameSession implements InventoryHolder {

    enum State {
        /** Snake waiting for the first move. */
        READY,
        RUNNING,
        PAUSED,
        /** The snake just crashed and is flashing. */
        DYING,
        /** Results screen. */
        OVER
    }

    private static final int FLASH_TICKS = 18;
    private static final int RESULTS_AFTER_TICKS = 28;
    private static final int BLINK_TICKS = 10;
    private static final int TOP_PLAYERS_SHOWN = 5;

    private final SnakeGamePlugin plugin;
    private final UUID playerId;
    private final PlayerData data;
    private final Layout layout;
    private final Inventory inventory;
    private final Random random = new Random();

    private SnakeEngine engine;
    private State state = State.READY;
    private Settings.Speed speed;
    private Settings.Theme theme;
    private double moveProgress;
    private int playTicks;
    private int stateTicks;
    private boolean started;
    private boolean recorded;
    private DataStore.GameResult result;

    private Player viewer;
    private boolean resendNeeded;
    private ItemStack[] storedItems;
    private long pausedSince;

    private final ItemStack[] shownChest = new ItemStack[Layout.CHEST_SIZE];
    private final ItemStack[] shownPlayer = new ItemStack[InventoryVault.SIZE];
    private final Map<Integer, Button> resultButtons = new HashMap<>();

    private ItemStack lightTile;
    private ItemStack darkTile;
    private ItemStack appleTile;
    private ItemStack bodyTile;
    private ItemStack crashTile;
    private ItemStack fillerTile;
    private ItemStack headTile;
    private int headTileLength = -1;

    public GameSession(SnakeGamePlugin plugin, Player player) {
        this.plugin = plugin;
        this.playerId = player.getUniqueId();
        this.data = plugin.data().get(player.getUniqueId(), player.getName());
        this.layout = plugin.settings().usePlayerInventory ? Layout.full() : Layout.compact();
        String title = messages().get("gui.title");
        this.inventory = Bukkit.createInventory(this, Layout.CHEST_SIZE, title.length() > 32 ? title.substring(0, 32) : title);
        this.speed = settings().speed(data.speed());
        this.theme = settings().theme(data.theme());
        rebuildTiles();
        newRound();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID playerId() {
        return playerId;
    }

    public boolean isOpen() {
        return viewer != null;
    }

    /** A round has started and not been recorded yet. */
    public boolean isInProgress() {
        return started && !recorded && (state == State.RUNNING || state == State.PAUSED);
    }

    public long pausedSince() {
        return pausedSince;
    }

    public int score() {
        return engine.score();
    }

    // ------------------------------------------------------------------
    // Opening and closing
    // ------------------------------------------------------------------

    /** Opens the menu for the player. Returns false if it could not be opened. */
    public boolean open(Player player) {
        if (viewer != null) {
            return true;
        }
        if (layout.usesPlayerInventory()) {
            storedItems = plugin.vault().store(player);
            if (storedItems == null) {
                messages().send(player, "chat.cannot-play-now");
                return false;
            }
        }
        viewer = player;
        Arrays.fill(shownChest, null);
        Arrays.fill(shownPlayer, null);
        renderAll();
        InventoryView view = player.openInventory(inventory);
        resendNeeded = false; // opening sends everything anyway
        if (view == null) {
            viewer = null;
            giveItemsBack(player);
            return false;
        }
        return true;
    }

    /**
     * Called when the menu closes, for any reason. Gives the player's items back and
     * pauses a running game.
     *
     * @return whether the menu was open
     */
    public boolean onMenuClosed(Player player) {
        if (viewer == null) {
            return false;
        }
        viewer = null;
        giveItemsBack(player);
        if (state == State.RUNNING) {
            state = State.PAUSED;
        }
        pausedSince = System.currentTimeMillis();
        return true;
    }

    /** Closes the menu right away (used on shutdown). */
    public void forceClose() {
        Player player = viewer;
        if (player == null) {
            return;
        }
        onMenuClosed(player);
        player.closeInventory();
        player.updateInventory();
    }

    /** Ends the session for good, recording an unfinished round. */
    public void end() {
        if (isInProgress()) {
            finishRound(false);
        }
    }

    private void giveItemsBack(final Player player) {
        if (storedItems != null) {
            plugin.vault().restore(player, storedItems);
            storedItems = null;
        }
        if (!layout.usesPlayerInventory()) {
            return;
        }
        // The client keeps showing the menu's items until the inventory is re-sent,
        // which only works once the menu has fully closed.
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.updateInventory();
                }
            });
        } else {
            player.updateInventory();
        }
    }

    /**
     * Makes dying with the menu open look like a normal death: the player's real items
     * go back into their inventory and replace the menu's items in the drops, so other
     * plugins (graves, keep-inventory) see exactly what they would without this game.
     */
    public void handleDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(ItemBuilder::isMenuItem);
        if (storedItems == null) {
            return;
        }
        plugin.vault().restore(event.getEntity(), storedItems);
        if (!event.getKeepInventory()) {
            for (ItemStack item : storedItems) {
                if (item != null && item.getType() != Material.AIR) {
                    event.getDrops().add(item);
                }
            }
        }
        storedItems = null;
    }

    // ------------------------------------------------------------------
    // Game flow
    // ------------------------------------------------------------------

    private void newRound() {
        Settings settings = settings();
        engine = new SnakeEngine(layout.boardWidth(), layout.boardHeight(), settings.startLength,
                settings.growthPerApple, !settings.walls, random);
        state = State.READY;
        started = false;
        recorded = false;
        result = null;
        moveProgress = 0;
        playTicks = 0;
        stateTicks = 0;
        headTileLength = -1;
    }

    /** Called every server tick by the session manager. */
    public void tick() {
        if (viewer == null) {
            return;
        }
        stateTicks++;
        switch (state) {
            case READY:
                if (stateTicks % BLINK_TICKS == 0) {
                    renderButtons(Button.PRIMARY);
                }
                break;
            case RUNNING:
                playTicks++;
                moveProgress += movesPerSecond() / 20.0;
                while (moveProgress >= 1 && state == State.RUNNING) {
                    moveProgress -= 1;
                    move();
                }
                break;
            case DYING:
                if (stateTicks <= FLASH_TICKS && stateTicks % 3 == 0) {
                    renderBoard();
                }
                if (stateTicks >= RESULTS_AFTER_TICKS) {
                    showResults();
                }
                break;
            default:
                break;
        }
        // Also picks up changes made by clicks since the last tick.
        flushResend();
    }

    private void move() {
        switch (engine.step()) {
            case MOVED:
                renderBoard();
                break;
            case ATE:
                renderBoard();
                int steps = settings().eatPitchSteps;
                float rise = steps <= 0 ? 0f : 0.5f * Math.min(engine.score(), steps) / steps;
                if (soundOn()) {
                    settings().sound("eat").play(viewer, rise);
                }
                renderButtons(Button.SCORE);
                break;
            case WON:
                renderBoard();
                finishRound(true);
                showResults();
                break;
            case DIED:
                setState(State.DYING);
                play("die");
                finishRound(true);
                renderBoard();
                renderButtons(Button.PRIMARY, Button.QUIT, Button.SCORE);
                break;
            default:
                break;
        }
    }

    private double movesPerSecond() {
        double base = speed.movesPerSecond;
        double boosted = base + settings().speedUpPerApple * engine.score();
        return Math.max(base, Math.min(settings().maxMovesPerSecond, boosted));
    }

    private void start(Direction direction) {
        if (direction != null) {
            engine.turn(direction);
        }
        started = true;
        moveProgress = 0;
        setState(State.RUNNING);
        play("start");
        renderButtons(Button.PRIMARY, Button.SPEED, Button.QUIT);
    }

    private void pause() {
        setState(State.PAUSED);
        play("pause");
        renderButtons(Button.PRIMARY);
    }

    private void resume() {
        moveProgress = 0;
        setState(State.RUNNING);
        play("resume");
        renderButtons(Button.PRIMARY);
    }

    private void steer(Direction direction) {
        switch (state) {
            case READY:
                start(direction);
                break;
            case PAUSED:
                resume();
                turn(direction);
                break;
            case RUNNING:
                turn(direction);
                break;
            default:
                break;
        }
    }

    private void turn(Direction direction) {
        if (engine.turn(direction)) {
            play("turn");
        }
    }

    private void primary() {
        switch (state) {
            case READY:
                start(null);
                break;
            case RUNNING:
                pause();
                break;
            case PAUSED:
                resume();
                break;
            case OVER:
                playAgain();
                break;
            default:
                break;
        }
    }

    private void playAgain() {
        newRound();
        play("click");
        renderAll();
    }

    private void setState(State newState) {
        state = newState;
        stateTicks = 0;
    }

    /**
     * Saves the round's score. Only rounds that were actually started count.
     *
     * @param announce whether to tell the player how it went (not when an abandoned game is cleaned up)
     */
    private void finishRound(boolean announce) {
        if (recorded || !started) {
            return;
        }
        recorded = true;
        int score = engine.score();
        long seconds = playTicks / 20;
        DataStore store = plugin.data();
        result = store.record(data, speed.id, score, seconds);
        store.saveAsync();

        Messages messages = messages();
        Player player = Bukkit.getPlayer(playerId);
        if (announce && player != null) {
            if (engine.isWon()) {
                messages.send(player, "chat.win");
            } else {
                messages.send(player, "chat.game-over", "score", score, "apples", messages.apples(score),
                        "time", Text.clock(seconds), "best", data.best(speed.id));
            }
            if (result.newBest && score > 0) {
                messages.send(player, "chat.new-best", "score", score, "apples", messages.apples(score), "speed", speed.name);
            }
        }
        if (result.serverRecord && settings().broadcastServerRecord) {
            Bukkit.broadcastMessage(messages.prefix() + messages.get("chat.server-record", "player", data.name(),
                    "score", score, "apples", messages.apples(score), "speed", speed.name));
        }
    }

    private void showResults() {
        setState(State.OVER);
        renderBoard();
        renderButtons();
        if (engine.isWon()) {
            play("win");
        }
        if (result != null && result.newBest && engine.score() > 0) {
            play("new-best");
        } else if (!engine.isWon()) {
            play("game-over");
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** Handles a click anywhere in the open menu. The event itself is already cancelled. */
    public void handleClick(int rawSlot, ClickType click, int hotbarKey) {
        if (viewer == null) {
            return;
        }
        if (click == ClickType.NUMBER_KEY) {
            Button button = layout.hotkeys().get(hotbarKey);
            if (button != null) {
                press(button);
            }
            return;
        }
        if (click != ClickType.LEFT && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) {
            return;
        }
        if (rawSlot < 0) {
            return;
        }
        if (rawSlot < Layout.CHEST_SIZE) {
            if (rawSlot < layout.boardCells()) {
                clickBoard(rawSlot);
            } else {
                Button button = layout.chestButtons().get(rawSlot);
                if (button != null) {
                    press(button);
                }
            }
            return;
        }
        // Below the chest: three inventory rows (slots 9-35), then the hotbar (slots 0-8).
        int index = rawSlot - Layout.CHEST_SIZE;
        int playerSlot = index < 27 ? index + 9 : index - 27;
        Button button = layout.playerButtons().get(playerSlot);
        if (button != null) {
            press(button);
        }
    }

    /** Clicking the board turns the snake towards the clicked square. */
    private void clickBoard(int cell) {
        if (state == State.OVER) {
            Button button = resultButtons.get(cell);
            if (button != null) {
                press(button);
            }
            return;
        }
        if (state == State.DYING) {
            return;
        }
        int width = engine.width();
        int dx = cell % width - engine.head() % width;
        int dy = cell / width - engine.head() / width;
        if (dx == 0 && dy == 0) {
            if (state != State.RUNNING) {
                primary();
            }
            return;
        }
        Direction wanted;
        if (state == State.READY) {
            wanted = Math.abs(dx) >= Math.abs(dy) ? horizontal(dx) : vertical(dy);
        } else if (engine.plannedDirection().isHorizontal()) {
            wanted = dy != 0 ? vertical(dy) : null;
        } else {
            wanted = dx != 0 ? horizontal(dx) : null;
        }
        if (wanted != null) {
            steer(wanted);
        }
    }

    private static Direction horizontal(int dx) {
        return dx > 0 ? Direction.RIGHT : Direction.LEFT;
    }

    private static Direction vertical(int dy) {
        return dy > 0 ? Direction.DOWN : Direction.UP;
    }

    private void press(Button button) {
        if (button.direction() != null) {
            steer(button.direction());
            return;
        }
        switch (button) {
            case PRIMARY:
                primary();
                break;
            case PLAY_AGAIN:
                if (state == State.OVER) {
                    playAgain();
                }
                break;
            case SPEED:
                if (canChangeSpeed()) {
                    speed = settings().nextSpeed(speed);
                    plugin.data().setSpeed(data, speed.id);
                    play("click");
                    renderButtons(Button.SPEED, Button.SCORE, Button.BEST);
                } else {
                    play("deny");
                }
                break;
            case THEME:
                theme = settings().nextTheme(theme);
                plugin.data().setTheme(data, theme.id);
                play("click");
                rebuildTiles();
                renderBoard();
                renderButtons(Button.THEME);
                break;
            case SOUND:
                plugin.data().setSound(data, !data.soundEnabled());
                play("click");
                renderButtons(Button.SOUND);
                break;
            case QUIT:
                final Player player = viewer;
                // Closing inside a click event confuses the client; do it on the next tick.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player == viewer) {
                        player.closeInventory();
                    }
                });
                break;
            default:
                break; // information only
        }
    }

    private boolean canChangeSpeed() {
        return state == State.READY || state == State.OVER;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /** Re-reads settings and icons, e.g. after /game reload or once HeadDatabase has loaded. */
    public void refreshVisuals() {
        speed = settings().speed(speed.id);
        theme = settings().theme(theme.id);
        rebuildTiles();
        headTileLength = -1;
        if (viewer != null) {
            renderAll();
        }
    }

    private void rebuildTiles() {
        Messages messages = messages();
        Icons icons = plugin.icons();
        lightTile = pane(theme.lightPane);
        darkTile = pane(theme.darkPane);
        fillerTile = pane(7);
        crashTile = ItemBuilder.of(Material.STAINED_GLASS_PANE, 14).name(messages.raw("gui.snake-body")).build();
        appleTile = ItemBuilder.from(icons.get(Icons.APPLE)).name(messages.raw("gui.apple"))
                .lore(messages.rawList("gui.apple-lore")).build();
        bodyTile = ItemBuilder.from(icons.get(Icons.SNAKE_BODY)).name(messages.raw("gui.snake-body")).build();
    }

    private static ItemStack pane(int color) {
        return ItemBuilder.of(Material.STAINED_GLASS_PANE, color).name(" ").build();
    }

    private ItemStack headTile() {
        int length = engine.length();
        if (headTile == null || headTileLength != length) {
            headTile = ItemBuilder.from(plugin.icons().get(Icons.SNAKE_HEAD)).name(messages().raw("gui.snake-head"))
                    .lore(messages().rawList("gui.snake-head-lore", "length", length)).build();
            headTileLength = length;
        }
        return headTile;
    }

    private void renderAll() {
        if (layout.usesPlayerInventory()) {
            for (int slot = 0; slot < InventoryVault.SIZE; slot++) {
                if (!layout.playerButtons().containsKey(slot)) {
                    setPlayerSlot(slot, fillerTile);
                }
            }
        }
        for (int slot = layout.boardCells(); slot < Layout.CHEST_SIZE; slot++) {
            if (!layout.chestButtons().containsKey(slot)) {
                setChestSlot(slot, fillerTile);
            }
        }
        renderBoard();
        renderButtons();
    }

    private void renderBoard() {
        ItemStack[] frame = new ItemStack[layout.boardCells()];
        resultButtons.clear();
        if (state == State.OVER) {
            drawResults(frame);
        } else {
            drawGame(frame);
        }
        for (int cell = 0; cell < frame.length; cell++) {
            setChestSlot(cell, frame[cell]);
        }
    }

    private void drawGame(ItemStack[] frame) {
        int width = layout.boardWidth();
        for (int cell = 0; cell < frame.length; cell++) {
            frame[cell] = (cell % width + cell / width) % 2 == 0 ? lightTile : darkTile;
        }
        if (engine.apple() >= 0) {
            frame[engine.apple()] = appleTile;
        }
        // While dying the snake flashes red, ending on red.
        boolean red = state == State.DYING && (stateTicks > FLASH_TICKS || (stateTicks / 3) % 2 == 0);
        List<Integer> body = engine.body();
        for (int i = body.size() - 1; i >= 0; i--) {
            frame[body.get(i)] = red ? crashTile : i == 0 ? headTile() : bodyTile;
        }
    }

    private void drawResults(ItemStack[] frame) {
        Messages messages = messages();
        boolean won = engine.isWon();
        ItemStack light = pane(won ? 4 : 7);
        ItemStack dark = pane(won ? 1 : 15);
        int width = layout.boardWidth();
        for (int cell = 0; cell < frame.length; cell++) {
            frame[cell] = (cell % width + cell / width) % 2 == 0 ? light : dark;
        }
        int score = engine.score();
        long seconds = playTicks / 20;
        Object[] placeholders = {"score", score, "apples", messages.apples(score), "length", engine.length(),
                "time", Text.clock(seconds), "speed", speed.name, "best", data.best(speed.id),
                "previous", result != null ? result.previousBest : 0};

        ItemStack title = (won ? ItemBuilder.of(Material.NETHER_STAR).glow(true) : ItemBuilder.of(Material.SKULL_ITEM, 0))
                .name(messages.raw(won ? "gui.win" : "gui.game-over"))
                .lore(messages.rawList("gui.result-lore", placeholders)).build();
        ItemStack apples = ItemBuilder.from(plugin.icons().get(Icons.APPLE)).amount(score)
                .name(messages.raw("gui.result-score", placeholders)).build();
        ItemStack time = ItemBuilder.of(Material.WATCH).name(messages.raw("gui.result-time", placeholders)).build();
        boolean newBest = result != null && result.newBest && score > 0;
        ItemStack best = newBest
                ? ItemBuilder.of(Material.NETHER_STAR).glow(true).name(messages.raw("gui.result-new-best", placeholders))
                .lore(messages.rawList("gui.result-new-best-lore", placeholders)).build()
                : ItemBuilder.of(Material.GOLD_INGOT).name(messages.raw("gui.result-best", placeholders)).build();
        ItemStack again = ItemBuilder.of(Material.INK_SACK, 10).glow(true).name(messages.raw("gui.play-again"))
                .lore(keyLore(Button.PRIMARY)).build();

        int buttonsRow = layout.boardHeight() - 2;
        put(frame, 4, 1, title);
        put(frame, 2, 2, apples);
        put(frame, 4, 2, time);
        put(frame, 6, 2, best);
        resultButtons.put(put(frame, 3, buttonsRow, again), Button.PLAY_AGAIN);
        resultButtons.put(put(frame, 5, buttonsRow, buttonItem(Button.QUIT)), Button.QUIT);
    }

    private int put(ItemStack[] frame, int x, int y, ItemStack item) {
        int cell = y * layout.boardWidth() + x;
        frame[cell] = item;
        return cell;
    }

    /** Redraws the given buttons, or all of them when none are given. */
    private void renderButtons(Button... which) {
        List<Button> wanted = which.length == 0 ? null : Arrays.asList(which);
        Map<Button, ItemStack> built = new EnumMap<>(Button.class);
        for (Map.Entry<Integer, Button> entry : layout.chestButtons().entrySet()) {
            if (wanted == null || wanted.contains(entry.getValue())) {
                setChestSlot(entry.getKey(), buttonItem(entry.getValue(), built));
            }
        }
        for (Map.Entry<Integer, Button> entry : layout.playerButtons().entrySet()) {
            if (wanted == null || wanted.contains(entry.getValue())) {
                setPlayerSlot(entry.getKey(), buttonItem(entry.getValue(), built));
            }
        }
    }

    private ItemStack buttonItem(Button button, Map<Button, ItemStack> built) {
        ItemStack item = built.get(button);
        if (item == null) {
            item = buttonItem(button);
            built.put(button, item);
        }
        return item;
    }

    private ItemStack buttonItem(Button button) {
        Messages messages = messages();
        Icons icons = plugin.icons();
        switch (button) {
            case UP:
                return arrow(icons.get(Icons.ARROW_UP), "gui.up", button);
            case DOWN:
                return arrow(icons.get(Icons.ARROW_DOWN), "gui.down", button);
            case LEFT:
                return arrow(icons.get(Icons.ARROW_LEFT), "gui.left", button);
            case RIGHT:
                return arrow(icons.get(Icons.ARROW_RIGHT), "gui.right", button);
            case PRIMARY:
                return primaryItem();
            case SCORE: {
                int score = engine.score();
                List<String> lore = new ArrayList<>(messages.rawList("gui.score-lore", "length", engine.length(),
                        "speed", speed.name, "moves", String.format(java.util.Locale.ROOT, "%.1f", movesPerSecond())));
                if (!layout.playerButtons().containsValue(Button.BEST) && !layout.chestButtons().containsValue(Button.BEST)) {
                    lore.add("");
                    lore.add(messages.raw("gui.best", "best", data.best(speed.id)));
                }
                return ItemBuilder.from(icons.get(Icons.APPLE)).amount(score)
                        .name(messages.raw("gui.score", "score", score)).lore(lore).build();
            }
            case BEST: {
                List<String> lore = new ArrayList<>(messages.rawList("gui.best-lore", "speed", speed.name));
                List<PlayerData> top = plugin.data().top(speed.id, TOP_PLAYERS_SHOWN);
                if (top.isEmpty()) {
                    lore.add(messages.raw("gui.best-empty"));
                }
                for (int i = 0; i < top.size(); i++) {
                    lore.add(messages.raw("gui.best-line", "rank", i + 1, "player", top.get(i).name(),
                            "score", top.get(i).best(speed.id)));
                }
                return ItemBuilder.of(Material.GOLD_INGOT).name(messages.raw("gui.best", "best", data.best(speed.id)))
                        .lore(lore).build();
            }
            case SPEED: {
                List<String> lore = new ArrayList<>();
                for (Settings.Speed option : settings().speeds().values()) {
                    lore.add(messages.raw(option == speed ? "gui.speed-line-selected" : "gui.speed-line", "speed", option.name));
                }
                lore.add("");
                lore.add(messages.raw(canChangeSpeed() ? "gui.speed-change" : "gui.speed-locked"));
                return ItemBuilder.of(Material.SUGAR).amount(speed.index + 1)
                        .name(messages.raw("gui.speed", "speed", speed.name)).lore(lore).build();
            }
            case THEME: {
                List<String> lore = new ArrayList<>();
                for (Settings.Theme option : settings().themes().values()) {
                    lore.add(messages.raw(option == theme ? "gui.theme-line-selected" : "gui.theme-line", "theme", option.name));
                }
                lore.add("");
                lore.add(messages.raw("gui.theme-change"));
                return ItemBuilder.of(theme.icon).name(messages.raw("gui.theme", "theme", theme.name)).lore(lore).build();
            }
            case STATS: {
                List<String> lore = new ArrayList<>(messages.rawList("gui.stats-lore", "games", data.games(),
                        "apples", data.apples(), "time", Text.duration(data.secondsPlayed())));
                for (Settings.Speed option : settings().speeds().values()) {
                    lore.add(messages.raw("gui.stats-best-line", "speed", option.name, "score", data.best(option.id)));
                }
                return ItemBuilder.of(Material.SKULL_ITEM, 3).skullOwner(data.name())
                        .name(messages.raw("gui.stats", "player", data.name())).lore(lore).build();
            }
            case HELP:
                return ItemBuilder.of(Material.BOOK).name(messages.raw("gui.help"))
                        .lore(messages.rawList("gui.help-lore")).build();
            case SOUND: {
                boolean on = data.soundEnabled();
                List<String> lore = new ArrayList<>(messages.rawList("gui.sound-lore"));
                lore.addAll(keyLore(button));
                return (on ? ItemBuilder.of(Material.NOTE_BLOCK) : ItemBuilder.of(Material.INK_SACK, 8))
                        .name(messages.raw(on ? "gui.sound-on" : "gui.sound-off")).lore(lore).build();
            }
            case QUIT: {
                boolean pauses = settings().pauseOnClose && started && !recorded;
                List<String> lore = new ArrayList<>(messages.rawList(pauses ? "gui.quit-lore-paused" : "gui.quit-lore",
                        "label", plugin.commandLabel()));
                lore.addAll(keyLore(button));
                return ItemBuilder.of(Material.BARRIER).name(messages.raw("gui.quit")).lore(lore).build();
            }
            default:
                return fillerTile;
        }
    }

    private ItemStack primaryItem() {
        Messages messages = messages();
        ItemBuilder builder;
        List<String> lore = new ArrayList<>();
        switch (state) {
            case READY: {
                // Pulses between lime and green to show where to start. (A colour change syncs to
                // the client right away; toggling only the glint would need a full resend.)
                boolean bright = (stateTicks / BLINK_TICKS) % 2 == 0;
                builder = ItemBuilder.of(Material.INK_SACK, bright ? 10 : 2).glow(bright)
                        .name(messages.raw("gui.start"));
                lore.addAll(messages.rawList("gui.start-lore"));
                break;
            }
            case RUNNING:
                builder = ItemBuilder.of(Material.INK_SACK, 11).name(messages.raw("gui.pause"));
                break;
            case PAUSED:
                builder = ItemBuilder.of(Material.INK_SACK, 10).glow(true).name(messages.raw("gui.resume"));
                break;
            case OVER:
                builder = ItemBuilder.of(Material.INK_SACK, 10).glow(true).name(messages.raw("gui.play-again"));
                break;
            default:
                builder = ItemBuilder.of(Material.INK_SACK, 8).name(messages.raw("gui.wait"));
                break;
        }
        lore.addAll(keyLore(Button.PRIMARY));
        return builder.lore(lore).build();
    }

    private ItemStack arrow(ItemStack icon, String nameKey, Button button) {
        String key = layout.keyFor(button);
        return ItemBuilder.from(icon).name(messages().raw(nameKey))
                .lore(messages().rawList("gui.arrow-lore", "key", key != null ? key : "?")).build();
    }

    private List<String> keyLore(Button button) {
        String key = layout.keyFor(button);
        List<String> lore = new ArrayList<>();
        if (key != null) {
            lore.add(messages().raw("gui.key-lore", "key", key));
        }
        return lore;
    }

    private void setChestSlot(int slot, ItemStack item) {
        ItemStack old = shownChest[slot];
        if (old != item) {
            shownChest[slot] = item;
            if (!unchanged(old, item)) {
                inventory.setItem(slot, item);
            }
        }
    }

    private void setPlayerSlot(int slot, ItemStack item) {
        ItemStack old = shownPlayer[slot];
        if (viewer != null && old != item) {
            shownPlayer[slot] = item;
            if (!unchanged(old, item)) {
                viewer.getInventory().setItem(slot, item);
            }
        }
    }

    /**
     * Whether a slot can keep its current item. Also notes changes the server would be slow
     * to send: Spigot 1.8 only compares type, amount and damage every tick, and the rest
     * (name, lore, head texture) once a second - so moving from one head to another
     * (snake head to body) needs a full resend.
     */
    private boolean unchanged(ItemStack old, ItemStack item) {
        if (old == null || item == null || old.getType() != item.getType()
                || old.getDurability() != item.getDurability() || old.getAmount() != item.getAmount()) {
            return false;
        }
        if (old.isSimilar(item)) {
            return true;
        }
        resendNeeded = true;
        return false;
    }

    /** Sends the whole menu again if a change would otherwise show up late. */
    private void flushResend() {
        if (resendNeeded && viewer != null) {
            viewer.updateInventory();
        }
        resendNeeded = false;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean soundOn() {
        return viewer != null && data.soundEnabled();
    }

    private void play(String sound) {
        if (soundOn()) {
            settings().sound(sound).play(viewer);
        }
    }

    private Settings settings() {
        return plugin.settings();
    }

    private Messages messages() {
        return plugin.messages();
    }
}
