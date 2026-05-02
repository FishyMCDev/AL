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

import java.util.List;

public class StatsRestorationGUI implements Listener {
    private final java.util.Set<java.util.UUID> processing = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private final AvatarLegacy plugin;
    private final String guiTitle = "Stats Restoration";
    private final String moveTitle = "Restore a Move";

    public StatsRestorationGUI(AvatarLegacy plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(guiTitle));

        int currentStrength = plugin.getStatsManager().getBendingStrength(player.getUniqueId());
        int movesRemoved    = plugin.getStatsManager().getMovesRemoved(player.getUniqueId());
        int currentDeaths   = plugin.getStatsManager().getDeathCount(player.getUniqueId());
        int maxDeaths       = plugin.getConfig().getInt("bending-spirit.max-deaths", 30);
        int spiritCostXP    = plugin.getConfig().getInt("bending-spirit.restore-cost-xp", 5000);
        int spiritCostYen   = plugin.getConfig().getInt("bending-spirit.restore-cost-yen", 100000);
        int maxRestores     = plugin.getConfig().getInt("bending-spirit.max-restores", -1);
        int usedRestores    = plugin.getCharacterManager().getRestoreCount(player.getUniqueId());
        int strengthCostXP  = plugin.getConfig().getInt("restoration.bending-strength-restore-cost-xp", 100);
        int strengthCostYen = plugin.getConfig().getInt("restoration.bending-strength-restore-cost-yen", 500);
        int moveCostXP      = plugin.getConfig().getInt("restoration.move-restore-cost-xp", 50);
        int moveCostYen     = plugin.getConfig().getInt("restoration.move-restore-cost-yen", 250);
        boolean spiritMaxed = maxRestores >= 0 && usedRestores >= maxRestores;

        ItemStack strengthXP = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta sxm = strengthXP.getItemMeta();
        sxm.displayName(Component.text("?aRestore Bending Strength (XP)"));
        sxm.lore(List.of(
                Component.text("?7Current: ?c" + currentStrength + "%"),
                Component.text("?7Cost: ?d" + strengthCostXP + " XP"),
                Component.text("?eClick to restore to 100%")
        ));
        strengthXP.setItemMeta(sxm);

        ItemStack strengthYen = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sym = strengthYen.getItemMeta();
        sym.displayName(Component.text("?aRestore Bending Strength (Yen)"));
        sym.lore(List.of(
                Component.text("?7Current: ?c" + currentStrength + "%"),
                Component.text("?7Cost: ?6?" + strengthCostYen),
                Component.text("?eClick to restore to 100%")
        ));
        strengthYen.setItemMeta(sym);

        ItemStack movesXP = new ItemStack(Material.BOOK);
        ItemMeta mxm = movesXP.getItemMeta();
        mxm.displayName(Component.text("?aRestore Removed Moves (XP)"));
        mxm.lore(List.of(
                Component.text("?7Moves removed: ?c" + movesRemoved),
                Component.text("?7Cost: ?d" + moveCostXP + " XP per move"),
                Component.text("?eClick to restore ONE move")
        ));
        movesXP.setItemMeta(mxm);

        ItemStack movesYen = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta mym = movesYen.getItemMeta();
        mym.displayName(Component.text("?aRestore Removed Moves (Yen)"));
        mym.lore(List.of(
                Component.text("?7Moves removed: ?c" + movesRemoved),
                Component.text("?7Cost: ?6?" + moveCostYen + " per move"),
                Component.text("?eClick to restore ONE move")
        ));
        movesYen.setItemMeta(mym);

        ItemStack spiritXP = new ItemStack(Material.TOTEM_OF_UNDYING);
        ItemMeta spxm = spiritXP.getItemMeta();
        spxm.displayName(Component.text(spiritMaxed ? "?cSpirit Restore (XP) | LIMIT REACHED" : "?bRestore Bending Spirit (XP)"));
        spxm.lore(List.of(
                Component.text("?7Spirit: ?c" + currentDeaths + "?7/?a" + maxDeaths + " deaths"),
                Component.text("?7Restores used: ?e" + usedRestores + (maxRestores >= 0 ? "?7/?e" + maxRestores : "")),
                Component.text("?7Cost: ?d" + spiritCostXP + " XP"),
                Component.text(spiritMaxed ? "?cLimit reached!" : "?eReduces death count by 1")
        ));
        spiritXP.setItemMeta(spxm);

        ItemStack spiritYen = new ItemStack(Material.NETHER_STAR);
        ItemMeta spym = spiritYen.getItemMeta();
        spym.displayName(Component.text(spiritMaxed ? "?cSpirit Restore (Yen) | LIMIT REACHED" : "?bRestore Bending Spirit (Yen)"));
        spym.lore(List.of(
                Component.text("?7Spirit: ?c" + currentDeaths + "?7/?a" + maxDeaths + " deaths"),
                Component.text("?7Restores used: ?e" + usedRestores + (maxRestores >= 0 ? "?7/?e" + maxRestores : "")),
                Component.text("?7Cost: ?6?" + spiritCostYen),
                Component.text(spiritMaxed ? "?cLimit reached!" : "?eReduces death count by 1")
        ));
        spiritYen.setItemMeta(spym);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(Component.text("?cClose"));
        close.setItemMeta(cm);

        inv.setItem(3,  spiritXP);
        inv.setItem(5,  spiritYen);
        inv.setItem(10, strengthXP);
        inv.setItem(12, strengthYen);
        inv.setItem(14, movesXP);
        inv.setItem(16, movesYen);
        inv.setItem(22, close);

        player.openInventory(inv);
    }

    private void openMoveRestoreGUI(Player player, boolean useXp) {
        List<String> removed = plugin.getStatsManager().getRemovedMoves(player.getUniqueId());
        if (removed.isEmpty()) {
            player.sendMessage(MessageUtil.error("You have no removed moves to restore."));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, Component.text(moveTitle));
        int moveCostXP = plugin.getConfig().getInt("restoration.move-restore-cost-xp", 50);
        int moveCostYen = plugin.getConfig().getInt("restoration.move-restore-cost-yen", 250);

        int idx = 0;
        for (String move : removed) {
            if (move == null || move.isBlank()) continue;
            if (idx >= inv.getSize()) break;

            Material mat = useXp ? Material.EXPERIENCE_BOTTLE : Material.GOLD_INGOT;
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(move));
            meta.lore(List.of(
                    Component.text("?7Restore this move"),
                    Component.text(useXp ? ("?7Cost: ?d" + moveCostXP + " XP") : ("?7Cost: ?6?" + moveCostYen)),
                    Component.text("?aClick to restore")
            ));
            item.setItemMeta(meta);
            inv.setItem(idx++, item);
        }

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(Component.text("?cBack"));
        close.setItemMeta(cm);
        inv.setItem(53, close);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Component title = event.getView().title();
        boolean isMain = title.equals(Component.text(guiTitle));
        boolean isMove = title.equals(Component.text(moveTitle));
        if (!isMain && !isMove) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.BARRIER) {
            if (isMove) openGUI(player);
            else player.closeInventory();
            return;
        }
        if (!processing.add(player.getUniqueId())) return;
        try {

        int spiritCostXP    = plugin.getConfig().getInt("bending-spirit.restore-cost-xp", 5000);
        int spiritCostYen   = plugin.getConfig().getInt("bending-spirit.restore-cost-yen", 100000);
        int maxRestores     = plugin.getConfig().getInt("bending-spirit.max-restores", -1);
        int usedRestores    = plugin.getCharacterManager().getRestoreCount(player.getUniqueId());
        long cooldownHours  = plugin.getConfig().getLong("bending-spirit.restore-cooldown-hours", 24);
        long lastRestore    = plugin.getCharacterManager().getLastRestoreTimestamp(player.getUniqueId());
        long cooldownMs     = cooldownHours * 3600 * 1000L;
        int strengthCostXP  = plugin.getConfig().getInt("restoration.bending-strength-restore-cost-xp", 100);
        int strengthCostYen = plugin.getConfig().getInt("restoration.bending-strength-restore-cost-yen", 500);
        int moveCostXP      = plugin.getConfig().getInt("restoration.move-restore-cost-xp", 50);
        int moveCostYen     = plugin.getConfig().getInt("restoration.move-restore-cost-yen", 250);

        if (isMove) {
            String moveName = null;
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.displayName() != null) {
                moveName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(meta.displayName()).trim();
            }
            if (moveName == null || moveName.isBlank()) return;

            if (clicked.getType() == Material.EXPERIENCE_BOTTLE) {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (data == null || data.getCustomXP() < moveCostXP) {
                    player.sendMessage(MessageUtil.error("Insufficient XP! Need " + moveCostXP + " XP.")); return;
                }
                data.addCustomXP(-moveCostXP);
                plugin.getPlayerDataManager().savePlayerData(data);
                plugin.getStatsManager().restoreMove(player.getUniqueId(), moveName);
                player.sendMessage(MessageUtil.success("Restored ?e" + moveName + "?a."));
                player.closeInventory();
                return;
            }

            if (clicked.getType() == Material.GOLD_INGOT) {
                if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), moveCostYen)) {
                    player.sendMessage(MessageUtil.error("Insufficient yen! Need ?" + moveCostYen + ".")); return;
                }
                plugin.getStatsManager().restoreMove(player.getUniqueId(), moveName);
                player.sendMessage(MessageUtil.success("Restored ?e" + moveName + "?a."));
                player.closeInventory();
                return;
            }

            return;
        }

        switch (clicked.getType()) {

            case TOTEM_OF_UNDYING -> {
                if (maxRestores >= 0 && usedRestores >= maxRestores) {
                    player.sendMessage(MessageUtil.error("You have reached the restore limit (" + maxRestores + ")!")); return;
                }
                if (cooldownHours > 0 && (System.currentTimeMillis() - lastRestore) < cooldownMs) {
                    long hoursLeft = ((cooldownMs - (System.currentTimeMillis() - lastRestore)) / 3600000) + 1;
                    player.sendMessage(MessageUtil.error("On cooldown! Available in ~" + hoursLeft + "h.")); return;
                }
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (data == null || data.getCustomXP() < spiritCostXP) {
                    player.sendMessage(MessageUtil.error("Insufficient XP! Need " + spiritCostXP + " XP.")); return;
                }
                data.addCustomXP(-spiritCostXP);
                plugin.getPlayerDataManager().savePlayerData(data);
                plugin.getStatsManager().reduceDeathCount(player.getUniqueId(), 1);
                plugin.getCharacterManager().incrementRestoreCount(player.getUniqueId());
                player.sendMessage(MessageUtil.success("?bBending spirit restored! Death count reduced by 1."));
                player.closeInventory();
            }

            case NETHER_STAR -> {
                if (maxRestores >= 0 && usedRestores >= maxRestores) {
                    player.sendMessage(MessageUtil.error("You have reached the restore limit (" + maxRestores + ")!")); return;
                }
                if (cooldownHours > 0 && (System.currentTimeMillis() - lastRestore) < cooldownMs) {
                    long hoursLeft = ((cooldownMs - (System.currentTimeMillis() - lastRestore)) / 3600000) + 1;
                    player.sendMessage(MessageUtil.error("On cooldown! Available in ~" + hoursLeft + "h.")); return;
                }
                if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), spiritCostYen)) {
                    player.sendMessage(MessageUtil.error("Insufficient yen! Need ?" + spiritCostYen + ".")); return;
                }
                plugin.getStatsManager().reduceDeathCount(player.getUniqueId(), 1);
                plugin.getCharacterManager().incrementRestoreCount(player.getUniqueId());
                player.sendMessage(MessageUtil.success("?bBending spirit restored! Death count reduced by 1."));
                player.closeInventory();
            }

            case EXPERIENCE_BOTTLE -> {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
                if (data == null || data.getCustomXP() < strengthCostXP) {
                    player.sendMessage(MessageUtil.error("Insufficient XP! Need " + strengthCostXP + " XP.")); return;
                }
                data.addCustomXP(-strengthCostXP);
                plugin.getPlayerDataManager().savePlayerData(data);
                plugin.getStatsManager().restoreBendingStrength(player.getUniqueId());
                player.sendMessage(MessageUtil.success("Bending strength restored to 100%!"));
                player.closeInventory();
            }

            case GOLD_INGOT -> {
                if (!plugin.getEconomyManager().withdraw(player.getUniqueId(), strengthCostYen)) {
                    player.sendMessage(MessageUtil.error("Insufficient yen! Need ?" + strengthCostYen + ".")); return;
                }
                plugin.getStatsManager().restoreBendingStrength(player.getUniqueId());
                player.sendMessage(MessageUtil.success("Bending strength restored to 100%!"));
                player.closeInventory();
            }

            case BOOK -> {
                openMoveRestoreGUI(player, true);
            }

            case ENCHANTED_BOOK -> {
                openMoveRestoreGUI(player, false);
            }
        }
        } finally {
            processing.remove(player.getUniqueId());
        }
    }
}
