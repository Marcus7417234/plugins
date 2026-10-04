package io.github.marcus7417234.snakegame.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the menu's items. Every item gets an invisible marker at the end of its name,
 * so items that ever leak out of the menu (for example on a crash) can be recognised and removed.
 */
public final class ItemBuilder {

    /** Invisible formatting codes appended to every menu item's name. */
    private static final String MARKER = "\u00a75\u00a7n\u00a74\u00a7k\u00a7e";

    private final ItemStack item;
    private String name = " ";
    private final List<String> lore = new ArrayList<>();
    private boolean glow;
    private String skullOwner;

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    public static ItemBuilder of(Material material, int data) {
        return new ItemBuilder(new ItemStack(material, 1, (short) data));
    }

    /** Starts from a copy of an existing item, keeping things such as a custom head texture. */
    public static ItemBuilder from(ItemStack base) {
        ItemStack copy = base.clone();
        copy.setAmount(1);
        return new ItemBuilder(copy);
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    /** Sets the display name. {@code &} colour codes are translated. */
    public ItemBuilder name(String name) {
        this.name = name == null || name.isEmpty() ? " " : name;
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public ItemBuilder lore(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemBuilder skullOwner(String owner) {
        this.skullOwner = owner;
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(Text.color(name) + MARKER);
        meta.setLore(lore.isEmpty() ? null : Text.color(lore));
        if (glow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
        } else if (meta.hasEnchants()) {
            for (Enchantment enchantment : new ArrayList<>(meta.getEnchants().keySet())) {
                meta.removeEnchant(enchantment);
            }
        }
        meta.addItemFlags(ItemFlag.values());
        if (skullOwner != null && meta instanceof SkullMeta) {
            ((SkullMeta) meta).setOwner(skullOwner);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Whether the item was made by this plugin's menu. */
    public static boolean isMenuItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().endsWith(MARKER);
    }
}
