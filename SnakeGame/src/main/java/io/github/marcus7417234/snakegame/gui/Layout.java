package io.github.marcus7417234.snakegame.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the board and the buttons go.
 * <p>
 * The full layout gives the board the whole 9x6 chest and puts the controls in the
 * player's own inventory (shown underneath the chest). The compact layout keeps
 * everything inside the chest: a 9x5 board plus one row of controls.
 */
public final class Layout {

    public static final int WIDTH = 9;
    public static final int CHEST_SIZE = 54;

    private final int boardHeight;
    private final boolean usesPlayerInventory;
    private final Map<Integer, Button> chestButtons;
    private final Map<Integer, Button> playerButtons;
    private final Map<Integer, Button> hotkeys;

    private Layout(int boardHeight, boolean usesPlayerInventory, Map<Integer, Button> chestButtons,
                   Map<Integer, Button> playerButtons) {
        this.boardHeight = boardHeight;
        this.usesPlayerInventory = usesPlayerInventory;
        this.chestButtons = Collections.unmodifiableMap(chestButtons);
        this.playerButtons = Collections.unmodifiableMap(playerButtons);

        // Number keys work while hovering any slot. 1-4 steer, 6 starts/pauses, 8 sound, 9 quit.
        Map<Integer, Button> keys = new HashMap<>();
        keys.put(0, Button.LEFT);
        keys.put(1, Button.UP);
        keys.put(2, Button.DOWN);
        keys.put(3, Button.RIGHT);
        keys.put(5, Button.PRIMARY);
        keys.put(7, Button.SOUND);
        keys.put(8, Button.QUIT);
        this.hotkeys = Collections.unmodifiableMap(keys);
    }

    /** Board fills the chest; controls live in the player's inventory. */
    public static Layout full() {
        Map<Integer, Button> player = new LinkedHashMap<>();
        // Player inventory slots 9-35 are the three rows above the hotbar.
        player.put(9, Button.SCORE);
        player.put(13, Button.UP);
        player.put(17, Button.BEST);
        player.put(18, Button.SPEED);
        player.put(21, Button.LEFT);
        player.put(22, Button.PRIMARY);
        player.put(23, Button.RIGHT);
        player.put(26, Button.STATS);
        player.put(27, Button.THEME);
        player.put(31, Button.DOWN);
        player.put(35, Button.HELP);
        // Hotbar (slots 0-8): the same buttons the number keys trigger.
        player.put(0, Button.LEFT);
        player.put(1, Button.UP);
        player.put(2, Button.DOWN);
        player.put(3, Button.RIGHT);
        player.put(5, Button.PRIMARY);
        player.put(7, Button.SOUND);
        player.put(8, Button.QUIT);
        return new Layout(6, true, new LinkedHashMap<Integer, Button>(), player);
    }

    /** 9x5 board with a row of controls at the bottom; the player's inventory is left alone. */
    public static Layout compact() {
        Map<Integer, Button> chest = new LinkedHashMap<>();
        chest.put(45, Button.SCORE);
        chest.put(46, Button.LEFT);
        chest.put(47, Button.UP);
        chest.put(48, Button.DOWN);
        chest.put(49, Button.RIGHT);
        chest.put(50, Button.PRIMARY);
        chest.put(51, Button.SPEED);
        chest.put(52, Button.SOUND);
        chest.put(53, Button.QUIT);
        return new Layout(5, false, chest, new LinkedHashMap<Integer, Button>());
    }

    public int boardWidth() {
        return WIDTH;
    }

    public int boardHeight() {
        return boardHeight;
    }

    public int boardCells() {
        return WIDTH * boardHeight;
    }

    public boolean usesPlayerInventory() {
        return usesPlayerInventory;
    }

    /** Chest slot to button (compact layout's control row). */
    public Map<Integer, Button> chestButtons() {
        return chestButtons;
    }

    /** Player inventory slot (0-35) to button. */
    public Map<Integer, Button> playerButtons() {
        return playerButtons;
    }

    /** Hotbar number key (0 = key "1") to button. */
    public Map<Integer, Button> hotkeys() {
        return hotkeys;
    }

    /** The number key label for a button, such as "2", or null if it has none. */
    public String keyFor(Button button) {
        for (Map.Entry<Integer, Button> entry : hotkeys.entrySet()) {
            if (entry.getValue() == button) {
                return String.valueOf(entry.getKey() + 1);
            }
        }
        return null;
    }
}
