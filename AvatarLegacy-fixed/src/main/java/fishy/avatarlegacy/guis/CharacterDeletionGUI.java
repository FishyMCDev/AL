package fishy.avatarlegacy.guis;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class CharacterDeletionGUI implements Listener {
    private final AvatarLegacy plugin;
    private final String guiTitle = "Delete Character";

    public CharacterDeletionGUI(AvatarLegacy plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(guiTitle));
        boolean canDelete = plugin.getCharacterManager().canDeleteCharacter(player.getUniqueId());

        ItemStack warning = new ItemStack(Material.BARRIER);
        ItemMeta wm = warning.getItemMeta();
        wm.displayName(Component.text("§c§l⚠ WARNING ⚠"));
        wm.lore(List.of(
                Component.text(""),
                Component.text("§7Deleting your character will:"),
                Component.text(""),
                Component.text("§c✘ Delete ALL your items"),
                Component.text("§c✘ Delete ALL your progress"),
                Component.text("§c✘ Delete ALL your stats"),
                Component.text("§c✘ Remove your nation citizenship"),
                Component.text("§c✘ Delete your economy balance"),
                Component.text(""),
                Component.text("§c§lTHIS CANNOT BE UNDONE!")
        ));
        warning.setItemMeta(wm);

        if (canDelete) {
            ItemStack confirm = new ItemStack(Material.TNT);
            ItemMeta cm = confirm.getItemMeta();
            cm.displayName(Component.text("§c§lDELETE CHARACTER"));
            cm.lore(List.of(
                    Component.text(""),
                    Component.text("§eClick to permanently delete your character"),
                    Component.text(""),
                    Component.text("§7You will be suspended in the sky to create"),
                    Component.text("§7a new character.")
            ));
            confirm.setItemMeta(cm);
            inv.setItem(11, confirm);
        } else {
            ItemStack locked = new ItemStack(Material.REDSTONE_BLOCK);
            ItemMeta lm = locked.getItemMeta();
            lm.displayName(Component.text("§c§lCANNOT DELETE"));
            List<Component> lockedLore = new ArrayList<>();
            lockedLore.add(Component.text(""));
            lockedLore.add(Component.text("§7You cannot delete your character yet!"));
            lockedLore.add(Component.text(""));
            lockedLore.add(Component.text("§7Reasons:"));

            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            if (data != null && !data.isElementPermanent() && data.getElement() != null) {
                lockedLore.add(Component.text("§c✘ Element selection still in progress"));
            }

            int cooldownDays = plugin.getConfig().getInt("character.deletion-cooldown-days", 7);
            lockedLore.add(Component.text("§c✘ " + cooldownDays + "-day deletion cooldown active"));
            lockedLore.add(Component.text(""));
            lockedLore.add(Component.text("§eTry again later"));
            lm.lore(lockedLore);
            locked.setItemMeta(lm);
            inv.setItem(11, locked);
        }

        ItemStack cancel = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.displayName(Component.text("§a§lKEEP CHARACTER"));
        cancelMeta.lore(List.of(Component.text(""), Component.text("§eClick to cancel deletion")));
        cancel.setItemMeta(cancelMeta);

        inv.setItem(13, warning);
        inv.setItem(15, cancel);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text(guiTitle))) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.TNT) {
            if (plugin.getCharacterManager().canDeleteCharacter(player.getUniqueId())) {
                player.closeInventory();
                
                plugin.getCharacterManager().deleteCharacter(player.getUniqueId());
            } else {
                player.sendMessage(MessageUtil.error("You can no longer delete your character!"));
                player.closeInventory();
            }
        } else if (clicked.getType() == Material.EMERALD_BLOCK) {
            player.closeInventory();
            player.sendMessage(MessageUtil.success("Character deletion cancelled."));
        }
    }
}
