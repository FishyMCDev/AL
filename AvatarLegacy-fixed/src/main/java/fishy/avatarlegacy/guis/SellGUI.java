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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SellGUI implements Listener {

    private final AvatarLegacy plugin;
    private static final String GUI_TITLE = "§6§lQuick Sell";

    
    private final Map<UUID, Map<Integer, Material>> openGuis = new HashMap<>();

    public SellGUI(AvatarLegacy plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    
    
    

    public void openSellGUI(Player player) {
        
        if (plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) == null) {
            player.sendMessage(MessageUtil.error("You need a loaded player profile to use the shop!"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(GUI_TITLE));
        Map<Integer, Material> slotMaterials = new HashMap<>();

        
        Map<Material, Integer> totals = getPlayerSellableTotals(player);

        if (totals.isEmpty()) {
            
            fillBorderGlass(inv);
            setInfoItem(inv, 22, Material.PAPER,
                    "§eNo Sellable Items",
                    List.of("§7You don't have any items",
                            "§7that can be sold in this shop."));
        } else {
            
            int slot = 0;
            String sym = plugin.getConfig().getString("currency.symbol", "¥");

            for (Map.Entry<Material, Integer> entry : totals.entrySet()) {
                if (slot >= 36) break;
                Material mat = entry.getKey();
                int qty = entry.getValue();
                double priceEach = getSellPrice(mat);
                double totalValue = priceEach * qty;

                ItemStack display = new ItemStack(mat, Math.min(qty, 64));
                ItemMeta meta = display.getItemMeta();
                meta.displayName(Component.text("§f" + formatMaterialName(mat)));
                meta.lore(List.of(
                        Component.text(""),
                        Component.text("§7You have: §e" + qty + "x"),
                        Component.text("§7Sell price: §a" + sym + String.format("%.1f", priceEach) + " §7each"),
                        Component.text("§7Total value: §a" + sym + String.format("%.1f", totalValue)),
                        Component.text(""),
                        Component.text("§eLeft-click  §7→ Sell ALL (" + qty + "x)"),
                        Component.text("§eRight-click §7→ Sell one stack"),
                        Component.text("")
                ));
                display.setItemMeta(meta);
                inv.setItem(slot, display);
                slotMaterials.put(slot, mat);
                slot++;
            }

            
            ItemStack filler = makeFiller();
            for (int i = slot; i < 36; i++) inv.setItem(i, filler);
        }

        
        ItemStack divider = makeFiller();
        for (int i = 36; i < 45; i++) inv.setItem(i, divider);

        
        double grandTotal = calcGrandTotal(player);
        String sym = plugin.getConfig().getString("currency.symbol", "¥");
        ItemStack sellAll = new ItemStack(Material.EMERALD);
        ItemMeta sam = sellAll.getItemMeta();
        sam.displayName(Component.text("§a§lSELL ALL"));
        sam.lore(List.of(
                Component.text(""),
                Component.text("§7Sells every sellable item"),
                Component.text("§7in your inventory at once."),
                Component.text(""),
                Component.text("§7Total payout: §a" + sym + String.format("%.1f", grandTotal)),
                Component.text(""),
                Component.text("§eClick to sell everything!")
        ));
        sellAll.setItemMeta(sam);
        inv.setItem(45, sellAll);

        
        for (int i = 46; i < 49; i++) inv.setItem(i, divider);

        
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(Component.text("§cClose"));
        close.setItemMeta(cm);
        inv.setItem(49, close);

        
        for (int i = 50; i < 53; i++) inv.setItem(i, divider);

        
        setInfoItem(inv, 53, Material.BOOK,
                "§bSell GUI",
                List.of("§7Items shown are sellable",
                        "§7based on the server shop.",
                        "",
                        "§7Prices match §e/shop§7.",
                        "",
                        "§eUse §a/sellgui §eto reopen."));

        openGuis.put(player.getUniqueId(), slotMaterials);
        player.openInventory(inv);
    }

    
    
    

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = event.getRawSlot();

        
        if (slot >= 54) return;

        Map<Integer, Material> slotMap = openGuis.get(player.getUniqueId());
        if (slotMap == null) return;

        
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        
        if (slot == 45 && clicked.getType() == Material.EMERALD) {
            sellAll(player);
            return;
        }

        
        Material mat = slotMap.get(slot);
        if (mat == null) return;

        double priceEach = getSellPrice(mat);
        if (priceEach <= 0) {
            player.sendMessage(MessageUtil.error("This item is not sellable."));
            return;
        }

        if (event.isLeftClick() || event.isShiftClick()) {
            
            sellMaterial(player, mat, -1, priceEach);
        } else if (event.isRightClick()) {
            
            sellMaterial(player, mat, 64, priceEach);
        }

        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().title().equals(Component.text(GUI_TITLE))) {
                player.closeInventory();
                openSellGUI(player);
            }
        });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openGuis.remove(player.getUniqueId());
    }

    
    
    

    
    private void sellMaterial(Player player, Material mat, int maxQty, double priceEach) {
        int available = countMaterial(player, mat);
        if (available == 0) {
            player.sendMessage(MessageUtil.error("You don't have any " + formatMaterialName(mat) + " to sell!"));
            return;
        }

        int toSell = (maxQty < 0) ? available : Math.min(maxQty, available);
        int removed = removeMaterial(player, mat, toSell);
        double payout = removed * priceEach;

        plugin.getEconomyManager().deposit(player.getUniqueId(), payout);

        String sym = plugin.getConfig().getString("currency.symbol", "¥");
        player.sendMessage(MessageUtil.success(
                "Sold §e" + removed + "x " + formatMaterialName(mat)
                + " §afor §e" + sym + String.format("%.1f", payout) + "§a!"));
    }

    
    private void sellAll(Player player) {
        Map<Material, Integer> totals = getPlayerSellableTotals(player);
        if (totals.isEmpty()) {
            player.sendMessage(MessageUtil.error("You have no sellable items!"));
            return;
        }

        double totalPayout = 0;
        int totalItems = 0;
        String sym = plugin.getConfig().getString("currency.symbol", "¥");
        StringBuilder breakdown = new StringBuilder();

        for (Map.Entry<Material, Integer> entry : totals.entrySet()) {
            Material mat = entry.getKey();
            int qty = entry.getValue();
            double price = getSellPrice(mat);
            int removed = removeMaterial(player, mat, qty);
            double payout = removed * price;
            totalPayout += payout;
            totalItems += removed;
            breakdown.append("  §7").append(formatMaterialName(mat)).append(" x").append(removed)
                     .append(" §8→ §a").append(sym).append(String.format("%.1f", payout)).append("\n");
        }

        plugin.getEconomyManager().deposit(player.getUniqueId(), totalPayout);

        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("§a§lSold §e" + totalItems + " items §afor §e" + sym + String.format("%.1f", totalPayout) + "§a!");
        player.sendMessage(breakdown.toString().trim());
        player.sendMessage("§6§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        
        player.closeInventory();
    }

    
    
    

    
    private Map<Material, Integer> getPlayerSellableTotals(Player player) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.items");
        if (items == null) return totals;

        
        Set<Material> sellable = new LinkedHashSet<>();
        for (String key : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(key);
            if (sec == null) continue;
            if (!sec.getBoolean("sellable", false)) continue;
            String matName = sec.getString("material");
            if (matName == null) continue;
            try {
                sellable.add(Material.valueOf(matName));
            } catch (IllegalArgumentException ignored) {}
        }

        
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) continue;
            if (!sellable.contains(stack.getType())) continue;
            totals.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        return totals;
    }

    
    private double getSellPrice(Material mat) {
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("shop.items");
        if (items == null) return 0;
        for (String key : items.getKeys(false)) {
            ConfigurationSection sec = items.getConfigurationSection(key);
            if (sec == null) continue;
            if (!sec.getBoolean("sellable", false)) continue;
            if (mat.name().equals(sec.getString("material"))) {
                return sec.getDouble("sell-price", 0);
            }
        }
        return 0;
    }

    
    private int countMaterial(Player player, Material mat) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) count += stack.getAmount();
        }
        return count;
    }

    
    private int removeMaterial(Player player, Material mat, int qty) {
        int remaining = qty;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
        player.updateInventory();
        return qty - remaining;
    }

    
    private double calcGrandTotal(Player player) {
        double total = 0;
        for (Map.Entry<Material, Integer> entry : getPlayerSellableTotals(player).entrySet()) {
            total += getSellPrice(entry.getKey()) * entry.getValue();
        }
        return total;
    }

    private String formatMaterialName(Material mat) {
        String[] words = mat.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private ItemStack makeFiller() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.text(" "));
        pane.setItemMeta(m);
        return pane;
    }

    private void fillBorderGlass(Inventory inv) {
        ItemStack filler = makeFiller();
        for (int i = 0; i < 36; i++) inv.setItem(i, filler);
    }

    private void setInfoItem(Inventory inv, int slot, Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        m.displayName(Component.text(name));
        List<Component> components = new ArrayList<>();
        for (String line : lore) components.add(Component.text(line));
        m.lore(components);
        item.setItemMeta(m);
        inv.setItem(slot, item);
    }
}
