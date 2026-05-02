package fishy.avatarlegacy.guis;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ShopGUI implements Listener {
    private final AvatarLegacy plugin;
    private final String guiTitle;

    public ShopGUI(AvatarLegacy plugin) {
        this.plugin = plugin;
        this.guiTitle = plugin.getConfig().getString("shop.gui-title", "Server Shop");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(guiTitle));

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.items");
        if (items == null) return;

        Set<String> itemKeys = items.getKeys(false);
        int slot = 0;

        for (String key : itemKeys) {
            if (slot >= 45) break;

            ConfigurationSection itemSection = items.getConfigurationSection(key);
            if (itemSection == null) continue;

            String materialName = itemSection.getString("material");
            double buyPrice = itemSection.getDouble("buy-price");
            double sellPrice = itemSection.getDouble("sell-price");
            boolean sellable = itemSection.getBoolean("sellable");

            try {
                Material material = Material.valueOf(materialName);
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();

                List<Component> lore = new ArrayList<>();
                String sym = plugin.getConfig().getString("currency.symbol", "¥");
                lore.add(Component.text("§aBuy: " + sym + buyPrice));
                if (sellable) {
                    lore.add(Component.text("§cSell: " + sym + sellPrice));
                }
                lore.add(Component.text(""));
                lore.add(Component.text("§eLeft-click to buy"));
                if (sellable) {
                    lore.add(Component.text("§eRight-click to sell"));
                }

                meta.lore(lore);
                item.setItemMeta(meta);

                inv.setItem(slot, item);
                slot++;
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in shop config: " + materialName);
            }
        }

        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.displayName(Component.text("§cClose Shop"));
        closeButton.setItemMeta(closeMeta);
        inv.setItem(49, closeButton);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text(guiTitle))) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            ConfigurationSection itemSection = items.getConfigurationSection(key);
            if (itemSection == null) continue;

            String materialName = itemSection.getString("material");
            if (!clicked.getType().name().equals(materialName)) continue;

            double buyPrice = itemSection.getDouble("buy-price");
            double sellPrice = itemSection.getDouble("sell-price");
            boolean sellable = itemSection.getBoolean("sellable");

            if (event.isLeftClick()) {
                if (plugin.getEconomyManager().withdraw(player.getUniqueId(), buyPrice)) {
                    ItemStack item = new ItemStack(clicked.getType(), 1);
                    player.getInventory().addItem(item);
                    String sym = plugin.getConfig().getString("currency.symbol", "¥");
                    player.sendMessage(MessageUtil.success("Purchased 1x " + clicked.getType().name() + " for " + sym + buyPrice));
                } else {
                    player.sendMessage(MessageUtil.error("Insufficient funds!"));
                }
            } else if (event.isRightClick() && sellable) {
                ItemStack toSell = new ItemStack(clicked.getType(), 1);
                if (player.getInventory().containsAtLeast(toSell, 1)) {
                    player.getInventory().removeItem(toSell);
                    plugin.getEconomyManager().deposit(player.getUniqueId(), sellPrice);
                    String sym = plugin.getConfig().getString("currency.symbol", "¥");
                    player.sendMessage(MessageUtil.success("Sold 1x " + clicked.getType().name() + " for " + sym + sellPrice));
                } else {
                    player.sendMessage(MessageUtil.error("You don't have that item!"));
                }
            }

            break;
        }
    }
}