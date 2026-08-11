package fishy.avatarlegacy.items;

import fishy.avatarlegacy.AvatarLegacy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ElementChangerItem {

    public static final String KEY_ID = "element_changer";

    public static ItemStack create(AvatarLegacy plugin) {
        ItemStack item = null;

        String nexoId = plugin.getConfig().getString("element-changer.nexo-id", "");
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
                // Nexo not installed, or id not found — fall back to the vanilla item below.
            }
        }
        if (item == null) item = new ItemStack(Material.CRYING_OBSIDIAN);

        ItemMeta meta = item.getItemMeta();

        String rawName = plugin.getConfig().getString("element-changer.item-name", "&5&lElement Changer");
        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(rawName)
                .decoration(TextDecoration.ITALIC, false));

        List<String> rawLore = plugin.getConfig().getStringList("element-changer.item-lore");
        List<Component> lore = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(LegacyComponentSerializer.legacyAmpersand().deserialize(line)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        NamespacedKey key = new NamespacedKey(plugin, KEY_ID);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /** Percentage (0-100) chance this item is added to a structure chest. Kept low by default. */
    public static double getChanceInStructure(AvatarLegacy plugin) {
        return plugin.getConfig().getDouble("element-changer.chance-in-structure", 0.0D);
    }

    public static boolean isElementChanger(AvatarLegacy plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = new NamespacedKey(plugin, KEY_ID);
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}