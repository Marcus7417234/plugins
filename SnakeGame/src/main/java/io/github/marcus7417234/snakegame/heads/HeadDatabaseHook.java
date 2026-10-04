package io.github.marcus7417234.snakegame.heads;

import io.github.marcus7417234.snakegame.util.Text;
import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import me.arcaniax.hdb.enums.CategoryEnum;
import me.arcaniax.hdb.object.head.Head;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks heads up in HeadDatabase by name or ID.
 * <p>
 * Only loaded when the HeadDatabase plugin is installed, so its classes are never
 * touched otherwise.
 */
public final class HeadDatabaseHook implements Listener {

    private final Plugin plugin;
    private final Runnable onDatabaseLoad;
    private final HeadDatabaseAPI api = new HeadDatabaseAPI();
    private Map<String, List<Head>> headsByName;

    public HeadDatabaseHook(Plugin plugin, Runnable onDatabaseLoad) {
        this.plugin = plugin;
        this.onDatabaseLoad = onDatabaseLoad;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDatabaseLoad(DatabaseLoadEvent event) {
        headsByName = null;
        // HeadDatabase may fire this off the main thread.
        Bukkit.getScheduler().runTask(plugin, onDatabaseLoad);
    }

    /** Whether HeadDatabase has finished loading its heads. */
    public boolean isLoaded() {
        return !index().isEmpty();
    }

    /**
     * Returns the first head from the candidates that exists. Candidates are head IDs
     * (such as "8352") or head names (such as "Snake Head").
     */
    public FoundHead find(List<String> candidates) {
        for (String raw : candidates) {
            String candidate = raw == null ? "" : raw.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (!candidate.contains(" ")) {
                ItemStack item = itemById(candidate);
                if (item != null) {
                    return new FoundHead(item, candidate, nameOf(candidate));
                }
            }
            List<Head> matches = index().get(Text.normalize(candidate));
            if (matches == null) {
                continue;
            }
            for (Head head : ranked(matches, candidate, candidates)) {
                ItemStack item = itemById(head.id);
                if (item == null) {
                    item = safeHeadItem(head);
                }
                if (item != null) {
                    return new FoundHead(item, head.id, ChatColor.stripColor(head.name));
                }
            }
        }
        return null;
    }

    /** Best match first: IDs also listed in the config, then exact names, then the oldest head. */
    private static List<Head> ranked(List<Head> matches, String candidate, List<String> candidates) {
        List<Head> sorted = new ArrayList<>(matches);
        sorted.sort((a, b) -> {
            int byListedId = Boolean.compare(candidates.contains(b.id), candidates.contains(a.id));
            if (byListedId != 0) {
                return byListedId;
            }
            int byExactName = Boolean.compare(isExactName(b, candidate), isExactName(a, candidate));
            if (byExactName != 0) {
                return byExactName;
            }
            return Long.compare(numericId(a), numericId(b));
        });
        return sorted;
    }

    private static boolean isExactName(Head head, String candidate) {
        return ChatColor.stripColor(head.name).trim().equalsIgnoreCase(candidate);
    }

    private static long numericId(Head head) {
        try {
            return Long.parseLong(head.id);
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
    }

    private String nameOf(String id) {
        for (List<Head> heads : index().values()) {
            for (Head head : heads) {
                if (id.equals(head.id)) {
                    return ChatColor.stripColor(head.name);
                }
            }
        }
        return null;
    }

    private ItemStack itemById(String id) {
        try {
            return api.isHead(id) ? api.getItemHead(id) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static ItemStack safeHeadItem(Head head) {
        try {
            return head.getHead();
        } catch (Throwable t) {
            return null;
        }
    }

    private Map<String, List<Head>> index() {
        if (headsByName != null) {
            return headsByName;
        }
        Map<String, List<Head>> index = new HashMap<>();
        for (CategoryEnum category : CategoryEnum.values()) {
            if (category == CategoryEnum.DISABLED || category == CategoryEnum.ONLINE_PLAYERS) {
                continue;
            }
            List<Head> heads;
            try {
                heads = api.getHeads(category);
            } catch (Throwable t) {
                continue;
            }
            if (heads == null) {
                continue;
            }
            for (Head head : heads) {
                if (head == null || head.name == null || head.id == null) {
                    continue;
                }
                String key = Text.normalize(head.name);
                List<Head> list = index.get(key);
                if (list == null) {
                    list = new ArrayList<>(1);
                    index.put(key, list);
                }
                list.add(head);
            }
        }
        // Keep trying until HeadDatabase has actually loaded something.
        if (!index.isEmpty()) {
            headsByName = index;
        }
        return index;
    }

    /** A head that was found, plus where it came from for logging. */
    public static final class FoundHead {
        public final ItemStack item;
        public final String id;
        public final String name;

        FoundHead(ItemStack item, String id, String name) {
            this.item = item;
            this.id = id;
            this.name = name;
        }
    }
}
