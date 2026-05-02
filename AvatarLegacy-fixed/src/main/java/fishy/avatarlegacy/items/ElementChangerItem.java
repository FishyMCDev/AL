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
        ItemStack item = new ItemStack(Material.CRYING_OBSIDIAN);
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

        meta.setEnchantmentGlintOverride(true);

        NamespacedKey key = new NamespacedKey(plugin, KEY_ID);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    public static boolean isElementChanger(AvatarLegacy plugin, ItemStack item) {
        if (item == null || item.getType() != Material.CRYING_OBSIDIAN) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        NamespacedKey key = new NamespacedKey(plugin, KEY_ID);
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
