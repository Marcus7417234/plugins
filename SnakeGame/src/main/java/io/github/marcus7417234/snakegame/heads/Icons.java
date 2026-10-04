package io.github.marcus7417234.snakegame.heads;

import io.github.marcus7417234.snakegame.config.Settings;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/** The base items (mostly heads) the game is drawn with, resolved from config.yml. */
public final class Icons {

    public static final String SNAKE_HEAD = "snake-head";
    public static final String SNAKE_BODY = "snake-body";
    public static final String APPLE = "apple";
    public static final String ARROW_UP = "arrow-up";
    public static final String ARROW_DOWN = "arrow-down";
    public static final String ARROW_LEFT = "arrow-left";
    public static final String ARROW_RIGHT = "arrow-right";

    private static final List<String> KEYS = Arrays.asList(
            SNAKE_HEAD, SNAKE_BODY, APPLE, ARROW_UP, ARROW_DOWN, ARROW_LEFT, ARROW_RIGHT);

    private final Map<String, ItemStack> items = new HashMap<>();

    /**
     * Resolves every icon. HeadDatabase heads are used when the hook is given and
     * HeadDatabase has loaded; otherwise the configured materials are used.
     */
    public void load(Settings settings, HeadDatabaseHook headDatabase, Logger logger) {
        items.clear();
        boolean useHeads = headDatabase != null && headDatabase.isLoaded();
        for (String key : KEYS) {
            Settings.ItemSpec spec = settings.item(key);
            ItemStack item = null;
            if (useHeads && !spec.hdb.isEmpty()) {
                HeadDatabaseHook.FoundHead found = headDatabase.find(spec.hdb);
                if (found != null) {
                    item = found.item;
                    logger.info("Using HeadDatabase head '" + (found.name != null ? found.name : "?")
                            + "' (#" + found.id + ") for " + key + ".");
                } else {
                    logger.warning("None of the HeadDatabase heads " + spec.hdb + " exist, so " + key
                            + " uses " + spec.material + " instead. Check items." + key + ".hdb in config.yml.");
                }
            }
            if (item == null) {
                item = new ItemStack(spec.material, 1, spec.data);
            }
            items.put(key, item);
        }
    }

    public ItemStack get(String key) {
        ItemStack item = items.get(key);
        return item != null ? item : new ItemStack(org.bukkit.Material.STONE);
    }
}
