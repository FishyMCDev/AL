package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.items.SubelementItem;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class SubelementItemListener implements Listener {

    private final AvatarLegacy plugin;

    public SubelementItemListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().name().contains("RIGHT")) return;
        if (!plugin.getConfig().getBoolean("subelement-access-items.enabled", true)) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        String entryId = SubelementItem.getEntryId(plugin, item);
        if (entryId == null) return;

        event.setCancelled(true);

        String subelement = SubelementItem.getSubelement(plugin, entryId);
        if (subelement == null || subelement.isBlank()) {
            player.sendMessage(MessageUtil.error("This scroll isn't configured correctly."));
            return;
        }

        boolean granted = plugin.getTraitManager().grantSubelementDirect(player, subelement);
        if (!granted) {
            player.sendMessage(MessageUtil.error(
                    "You need the matching parent element active to learn " + subelement + "."));
            return;
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        player.sendMessage(MessageUtil.success("You have learned the " + subelement + " subelement!"));
    }
}