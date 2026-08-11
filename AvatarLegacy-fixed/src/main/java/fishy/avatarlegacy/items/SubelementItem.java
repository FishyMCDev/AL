package fishy.avatarlegacy.items;

import fishy.avatarlegacy.AvatarLegacy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds "subelement access" items from config, e.g. traits.subelement-access-items.<id>.
 * Grants the configured subelement trait directly to the player who consumes it.
 */
public class SubelementItem {

    public static final String KEY_SUBELEMENT = "subelement_access_item";

    /** Config keys under subelement-access-items.<id> */
    public static String getSubelement(AvatarLegacy plugin, String entryId) {
        return plugin.getConfig().getString("subelement-access-items." + entryId + ".subelement", "");
    }

    /** Percentage (0-100) chance this item is added to a structure chest. Kept low by default. */
    public static double getChanceInStructure(AvatarLegacy plugin, String entryId) {
        return plugin.getConfig().getDouble("subelement-access-items." + entryId + ".chance-in-structure", 0.0D);
    }

    /** All configured entry ids under subelement-access-items (excluding the top-level "enabled" flag). */
    public static List<String> getEntryIds(AvatarLegacy plugin) {
        List<String> ids = new ArrayList<>();
        ConfigurationSection root = plugin.getConfig().getConfigurationSection("subelement-access-items");
        if (root == null) return ids;
        for (String key : root.getKeys(false)) {
            if (key.equalsIgnoreCase("enabled")) continue;
            if (root.isConfigurationSection(key)) ids.add(key);
        }
        return ids;
    }

    public static ItemStack create(AvatarLegacy plugin, String entryId) {
        String base = "subelement-access-items." + entryId + ".";
        ItemStack item = null;

        String nexoId = plugin.getConfig().getString(base + "nexo-id", "");
        if (nexoId != null && !nexoId.isBlank()) {
            try {
                Object builder = Class.forName("com.nexomc.nexo.api.NexoItems")
                        .getMethod("itemFromId", String.class)
                        .invoke(null, nexoId);
                if (builder != null) {
                    Object built = builder.getClass().getMethod("build").invoke(builder);
                    if (built instanceof ItemStack stack) item = stack;
                }
            } catch (Throwable ignored) {
                // Nexo not installed, or id not found — fall back to the configured material below.
            }
        }
        if (item == null) {
            Material mat = Material.matchMaterial(plugin.getConfig().getString(base + "material", "PAPER"));
            item = new ItemStack(mat != null ? mat : Material.PAPER);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String rawName = plugin.getConfig().getString(base + "name", "&bSubelement Scroll");
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(rawName)
                .decoration(TextDecoration.ITALIC, false));

        List<String> rawLore = plugin.getConfig().getStringList(base + "lore");
        List<Component> lore = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        NamespacedKey key = new NamespacedKey(plugin, KEY_SUBELEMENT);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, entryId);

        item.setItemMeta(meta);
        return item;
    }

    /** Returns the subelement-access-items entry id stored on this item, or null if it isn't one. */
    public static String getEntryId(AvatarLegacy plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        NamespacedKey key = new NamespacedKey(plugin, KEY_SUBELEMENT);
        return meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
