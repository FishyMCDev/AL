package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.items.ElementChangerItem;
import fishy.avatarlegacy.items.SubelementItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rolls, per chest-loot generation (every vanilla structure chest, and any datapack
 * that reuses a vanilla "chests/..." loot table), a low independent chance to add
 * the Element Changer and each configured subelement-access item. Additive only —
 * never removes or replaces the vanilla loot that was already rolled.
 */
public class StructureLootManager implements Listener {

    private final AvatarLegacy plugin;

    public StructureLootManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        LootTable table = event.getLootTable();
        if (table == null) return;

        // Vanilla structure chests (and datapacks that reuse them) all live under "chests/".
        String key = table.getKey().getKey();
        if (!key.startsWith("chests/")) return;

        double roll = ThreadLocalRandom.current().nextDouble(100.0D);
        double elementChangerChance = ElementChangerItem.getChanceInStructure(plugin);
        if (elementChangerChance > 0 && roll < elementChangerChance) {
            event.getLoot().add(ElementChangerItem.create(plugin));
        }

        if (plugin.getConfig().getBoolean("subelement-access-items.enabled", true)) {
            List<String> entryIds = SubelementItem.getEntryIds(plugin);
            for (String entryId : entryIds) {
                double chance = SubelementItem.getChanceInStructure(plugin, entryId);
                if (chance <= 0) continue;
                if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                    ItemStack item = SubelementItem.create(plugin, entryId);
                    event.getLoot().add(item);
                }
            }
        }
    }
}