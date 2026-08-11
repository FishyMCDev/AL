package fishy.avatarlegacy.guis;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bar-chart style GUI of the top Avatar candidates for the current cycle element.
 * Auto-refreshes every 30 seconds for as long as at least one viewer has it open.
 */
public class AvatarScoresGUI implements InventoryHolder {

    private static final int MAX_CANDIDATES = 9;
    private static final int CHART_ROWS = 4; // rows 0-3 are the bar chart, row 4 is player heads/info

    private static final Set<AvatarScoresGUI> OPEN_INSTANCES = ConcurrentHashMap.newKeySet();
    private static BukkitTask refreshTask;

    private final AvatarLegacy plugin;
    private final Inventory inventory;

    public AvatarScoresGUI(AvatarLegacy plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, "§6§lAvatar Candidate Scores");
        render();
    }

    @Override public Inventory getInventory() { return inventory; }

    public void open(Player player) {
        player.openInventory(inventory);
        OPEN_INSTANCES.add(this);
        ensureRefreshTaskRunning(plugin);
    }

    public static void onClose(AvatarScoresGUI gui) {
        if (gui.inventory.getViewers().size() <= 1) { // the closing viewer is still counted at close time
            OPEN_INSTANCES.remove(gui);
        }
        if (OPEN_INSTANCES.isEmpty() && refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    private static void ensureRefreshTaskRunning(AvatarLegacy plugin) {
        if (refreshTask != null) return;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (AvatarScoresGUI gui : OPEN_INSTANCES) {
                if (gui.inventory.getViewers().isEmpty()) continue;
                gui.render();
            }
        }, 600L, 600L); // every 30 seconds (20 ticks/sec * 30)
    }

    private void render() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        String cycleElement = plugin.getAvatarManager().getCurrentCycleElement();

        List<Candidate> candidates = fetchCandidates(cycleElement);

        double maxScore = candidates.stream().mapToDouble(c -> c.averageScore).max().orElse(1.0);
        if (maxScore <= 0) maxScore = 1.0;

        int col = 0;
        for (Candidate c : candidates) {
            if (col >= MAX_CANDIDATES) break;

            int barHeight = (int) Math.round((c.averageScore / maxScore) * CHART_ROWS);
            barHeight = Math.max(1, Math.min(CHART_ROWS, barHeight));

            for (int row = 0; row < CHART_ROWS; row++) {
                int slotRow = CHART_ROWS - 1 - row; // fill from bottom up
                boolean filled = row < barHeight;
                Material mat = filled
                        ? (c.eligible ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE)
                        : Material.GRAY_STAINED_GLASS_PANE;
                inventory.setItem(slotRow * 9 + col, barSegment(mat, c));
            }

            inventory.setItem(4 * 9 + col, headItem(c));
            col++;
        }

        // Info item describing the current cycle and refresh cadence.
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lCycle: §f" + cycleElement.toUpperCase());
            meta.setLore(List.of(
                    "§7Scored by: damage dealt bending,",
                    "§7XP, and playtime.",
                    "",
                    "§8Refreshes every 30 seconds."
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            info.setItemMeta(meta);
        }
        inventory.setItem(53, info);
    }

    private ItemStack barSegment(Material mat, Candidate c) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§f" + c.username);
            meta.setLore(List.of(
                    "§7Score: §a" + String.format("%.0f", c.averageScore),
                    "§7Damage: §c" + c.damageScore,
                    "§7XP: §b" + c.experienceScore,
                    "§7Playtime: §e" + c.playtimeScore + "h"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack headItem(Candidate c) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta metaRaw = head.getItemMeta();
        if (metaRaw instanceof SkullMeta meta) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(c.uuid);
            meta.setOwningPlayer(op);
            meta.setDisplayName((c.eligible ? "§a" : "§c") + c.username);
            meta.setLore(List.of("§7Average Score: §f" + String.format("%.0f", c.averageScore)));
            head.setItemMeta(meta);
        }
        return head;
    }

    private List<Candidate> fetchCandidates(String cycleElement) {
        List<Candidate> list = new ArrayList<>();
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "SELECT ac.uuid, ac.interconnection_score, ac.experience_score, ac.playtime_score, " +
                "ac.damage_score, ac.average_score, ac.eligible, p.username " +
                "FROM avatar_candidates ac " +
                "JOIN players p ON ac.uuid = p.uuid " +
                "WHERE p.element = ? AND p.element_permanent = 1 " +
                "ORDER BY ac.average_score DESC LIMIT ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cycleElement.toLowerCase());
            stmt.setInt(2, MAX_CANDIDATES);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Candidate c = new Candidate();
                c.uuid = java.util.UUID.fromString(rs.getString("uuid"));
                c.username = rs.getString("username");
                c.damageScore = rs.getInt("damage_score");
                c.experienceScore = rs.getInt("experience_score");
                c.playtimeScore = rs.getInt("playtime_score");
                c.averageScore = rs.getDouble("average_score");
                c.eligible = rs.getBoolean("eligible");
                list.add(c);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to fetch avatar candidates: " + e.getMessage());
        }
        return list;
    }

    private static class Candidate {
        java.util.UUID uuid;
        String username;
        int damageScore;
        int experienceScore;
        int playtimeScore;
        double averageScore;
        boolean eligible;
    }
}
