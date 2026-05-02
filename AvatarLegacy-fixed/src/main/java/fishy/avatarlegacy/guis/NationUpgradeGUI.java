package fishy.avatarlegacy.guis;

import fishy.avatarlegacy.AvatarLegacy;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NationUpgradeGUI implements Listener {
    private final AvatarLegacy plugin;
    private final String guiTitle = "Nation Upgrades";
    
    private final Set<UUID> processing = new HashSet<>();

    private static final int MAX_LEVEL = 3;

    public NationUpgradeGUI(AvatarLegacy plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openGUI(Player player, int nationId) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(guiTitle));

        double treasury = plugin.getNationManager().getTreasury(nationId);
        int resourceLevel  = getUpgradeLevel(nationId, "resource_drop_rate_level");
        int strengthLevel  = getUpgradeLevel(nationId, "citizen_strength_level");
        int coreLevel      = getUpgradeLevel(nationId, "core_shield_level");

        inv.setItem(11, buildUpgradeItem(Material.DIAMOND_PICKAXE,
                "§aResource Drop Rate", "§7Increases resource drops for citizens",
                resourceLevel, "upgrades.resource-drop-rate"));

        inv.setItem(13, buildUpgradeItem(Material.DIAMOND_SWORD,
                "§aCitizen Strength", "§7+5% damage & health per level",
                strengthLevel, "upgrades.citizen-strength"));

        inv.setItem(15, buildUpgradeItem(Material.BEACON,
                "§aCore Shield", "§7Activate /nation shield to protect core for " + plugin.getConfig().getLong("core.shield-duration-minutes", 60) + " min",
                coreLevel, "upgrades.core-shield"));

        ItemStack treasuryItem = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta tm = treasuryItem.getItemMeta();
        tm.displayName(Component.text("§6Nation Treasury"));
        tm.lore(List.of(Component.text("§7Balance: §6" + MessageUtil.formatYen(treasury))));
        treasuryItem.setItemMeta(tm);
        inv.setItem(4, treasuryItem);

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.displayName(Component.text("§cClose"));
        close.setItemMeta(cm);
        inv.setItem(22, close);

        player.openInventory(inv);
    }

    private ItemStack buildUpgradeItem(Material mat, String name, String desc,
                                        int currentLevel, String configPath) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));

        int nextLevel = currentLevel + 1;
        String status = currentLevel >= MAX_LEVEL ? "§aMAX LEVEL" : "§7Level: §e" + currentLevel + "/" + MAX_LEVEL;
        String costLine;

        if (currentLevel >= MAX_LEVEL) {
            costLine = "§aFully upgraded!";
        } else {
            double cost = plugin.getConfig().getDouble(configPath + ".level-" + nextLevel + "-cost", 0);
            costLine = "§7Upgrade cost: §6" + MessageUtil.formatYen(cost);
        }

        meta.lore(List.of(
                Component.text(desc),
                Component.text(""),
                Component.text(status),
                Component.text(costLine),
                Component.text(""),
                currentLevel < MAX_LEVEL
                        ? Component.text("§eClick to upgrade!")
                        : Component.text("§8No further upgrades available.")
        ));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        String viewTitle = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.getView().title());
        if (!viewTitle.equals(guiTitle)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.BARRIER) { player.closeInventory(); return; }
        if (clicked.getType() == Material.GOLD_BLOCK) return;
        if (!processing.add(player.getUniqueId())) return;

        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) return;
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can purchase upgrades!"));
            return;
        }

        String upgradeType;
        String dbColumn;
        String configPath;

        switch (clicked.getType()) {
            case DIAMOND_PICKAXE -> {
                upgradeType = "Resource Drop Rate";
                dbColumn    = "resource_drop_rate_level";
                configPath  = "upgrades.resource-drop-rate";
            }
            case DIAMOND_SWORD -> {
                upgradeType = "Citizen Strength";
                dbColumn    = "citizen_strength_level";
                configPath  = "upgrades.citizen-strength";
            }
            case BEACON -> {
                upgradeType = "Core Shield";
                dbColumn    = "core_shield_level";
                configPath  = "upgrades.core-shield";
            }
            default -> { return; }
        }

        int currentLevel = getUpgradeLevel(nationId, dbColumn);

        if (currentLevel >= MAX_LEVEL) {
            player.sendMessage(MessageUtil.error(upgradeType + " is already at max level!"));
            return;
        }

        int nextLevel = currentLevel + 1;
        double cost = plugin.getConfig().getDouble(configPath + ".level-" + nextLevel + "-cost", 0);

        if (cost <= 0) {
            player.sendMessage(MessageUtil.error("Upgrade cost not configured! Contact an admin."));
            return;
        }

        if (!plugin.getNationManager().withdrawTreasury(nationId, cost)) {
            player.sendMessage(MessageUtil.error("Insufficient treasury funds! Need " + MessageUtil.formatYen(cost)));
            return;
        }

        setUpgradeLevel(nationId, dbColumn, nextLevel);
        player.sendMessage(MessageUtil.success(upgradeType + " upgraded to level " + nextLevel + "!"));
        plugin.getNotificationManager().queueNotificationForNation(nationId,
                upgradeType + " upgraded to level " + nextLevel + " by " + player.getName() + "!");

        if (dbColumn.equals("citizen_strength_level")) {
            for (java.util.UUID citizenUuid : plugin.getNationManager().getNationCitizens(nationId)) {
                org.bukkit.entity.Player cp = org.bukkit.Bukkit.getPlayer(citizenUuid);
                if (cp != null) plugin.getNationManager().applyCitizenStrengthBonus(cp);
            }
        }

        player.closeInventory();
        openGUI(player, nationId);
        processing.remove(player.getUniqueId());
    }

    private int getUpgradeLevel(int nationId, String column) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT " + column + " FROM nation_upgrades WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(column);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get upgrade level (" + column + "): " + e.getMessage());
        }
        return 0;
    }

    private void setUpgradeLevel(int nationId, String column, int level) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try {
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT OR IGNORE INTO nation_upgrades (nation_id) VALUES (?)")) {
                ins.setInt(1, nationId);
                ins.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE nation_upgrades SET " + column + " = ? WHERE nation_id = ?")) {
                stmt.setInt(1, level);
                stmt.setInt(2, nationId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set upgrade level (" + column + "): " + e.getMessage());
        }
    }
}
