package fishy.avatarlegacy.guis;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.skilltree.PlayerSkillData;
import fishy.avatarlegacy.skilltree.SkillNode;
import fishy.avatarlegacy.skilltree.SkillRequirements;
import fishy.avatarlegacy.skilltree.SkillTreeManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Skill Tree GUI — uses the RPG-SkillTree resource pack for all visuals.
 *
 * ── Background positioning (IMPORTANT) ───────────────────────────────────────
 *  The parchment panel is glyph \uE000 in font "rpg:skilltree".
 *  It MUST be placed as the inventory TITLE Component so the client renders it
 *  on top of the vanilla dark vignette (full brightness, no dimming).
 *
 *  Geometry (Minecraft 1.21 6-row chest):
 *    GUI window:   176 px wide, title baseline at topPos + 6
 *    Title text:   starts at leftPos + 8 (Minecraft's default title X)
 *    Slot area:    leftPos + 7 → rightPos - 7
 *
 *  The \uE000 glyph (ascent=24, height=234) is 176 px wide (full GUI width).
 *  Without any shift it starts at x = leftPos + 8, which is 8 px too far right.
 *
 *  Fix: prepend two shift glyphs BEFORE \uE000 to move the draw cursor left 8 px:
 *    \uE012  =  −9 px
 *    \uE019  =  +1 px
 *    net     =  −8 px  → cursor now at leftPos + 0
 *
 *  After drawing the background we append \uE010 (−18 px) to pull the cursor
 *  back left, preventing any Adventure text that follows from leaking into view.
 *
 *  Full title string (all in rpg:skilltree font, white, no italic):
 *    "\uE012\uE019\uE000\uE010"
 *
 * ── CustomModelData values (must match assets/minecraft/items/paper.json) ───
 *  1001 arrow_up    1002 arrow_down   1003 arrow_left   1004 arrow_right
 *  1010 upgrade (yellow/available)    1011 reset (green/unlocked)
 *  1012 locked (red)                  1013 info
 *  1020 line_h                        1021 line_v
 *
 * ── Grid / viewport ──────────────────────────────────────────────────────────
 *  Rows 0–4 (slots 0–44) : 9×5 scrollable node area
 *  Row  5   (slots 45–53): navigation bar
 *  slot = (posY − offsetY) × 9 + (posX − offsetX)
 *
 * ── Navigation bar layout ────────────────────────────────────────────────────
 *  45=◀  46=░  47=▲  48=░  49=ℹ  50=░  51=▼  52=░  53=▶
 */
public class SkillTreeGUI implements InventoryHolder {

    // ── Nav slot indices (read by SkillTreeListener) ─────────────────────────
    public static final int NAV_LEFT  = 45;
    public static final int NAV_UP    = 47;
    public static final int NAV_INFO  = 49;
    public static final int NAV_DOWN  = 51;
    public static final int NAV_RIGHT = 53;

    // ── PersistentData keys (read by SkillTreeListener) ──────────────────────
    public static final String NODE_ID_KEY  = "skilltree_node_id";
    public static final String TREE_KEY_KEY = "skilltree_tree_key";

    // ── CustomModelData values ────────────────────────────────────────────────
    private static final int CMD_LOCKED     = 1012;
    private static final int CMD_UNLOCKABLE = 1010;
    private static final int CMD_UNLOCKED   = 1011;
    private static final int CMD_LINE_H     = 1020;
    private static final int CMD_LINE_V     = 1021;
    private static final int CMD_ARROW_UP   = 1001;
    private static final int CMD_ARROW_DOWN = 1002;
    private static final int CMD_ARROW_LEFT = 1003;
    private static final int CMD_ARROW_RIGHT= 1004;
    private static final int CMD_INFO       = 1013;

    // ── Resource pack font ────────────────────────────────────────────────────
    private static final Key RPG_FONT = Key.key("rpg", "skilltree");

    /**
     * The inventory title Component that renders the parchment background.
     *
     * Character breakdown (all rendered in font rpg:skilltree):
     *   \uE012  −9 px  shift left
     *   \uE019  +1 px  shift right  (net: −8 px total, aligns with GUI left edge)
     *   \uE000         the 176 px wide background sprite
     *   \uE010  −18 px shift left   (retracts cursor, hides any leaked text)
     *
     * Style: white (0xFFFFFF) so the texture is not colour-shifted, italic=false
     * so glyphs are not skewed by Adventure's default italic on item components.
     */
    private static final Component BACKGROUND =
            Component.text("\uE012\uE019\uE000\uE010")
                    .style(Style.style()
                            .font(RPG_FONT)
                            .color(TextColor.color(0xFFFFFF))
                            .decoration(TextDecoration.ITALIC, false)
                            .build());

    // ── Instance fields ───────────────────────────────────────────────────────
    private final AvatarLegacy plugin;
    private final Player        player;
    private final String        treeKey;
    private final SkillTreeManager manager;

    private final Inventory inventory;
    private int offsetX = 0;
    private int offsetY = 0;

    // ─────────────────────────────────────────────────────────────────────────

    public SkillTreeGUI(AvatarLegacy plugin, Player player,
                        String treeKey, SkillTreeManager manager) {
        this.plugin   = plugin;
        this.player   = player;
        this.treeKey  = treeKey.toLowerCase();
        this.manager  = manager;
        this.inventory = Bukkit.createInventory(this, 54, BACKGROUND);

        // Center the viewport on the START node so it always appears in the
        // middle slot of the 9x5 grid (col 4, row 2), regardless of scrolling.
        SkillNode start = manager.getNode(treeKey, "start");
        if (start != null) {
            this.offsetX = start.getPosX() - 4;
            this.offsetY = start.getPosY() - 2;
        }

        render();
    }

    @Override public Inventory getInventory() { return inventory; }

    public Player  getPlayer()  { return player;  }
    public String  getTreeKey() { return treeKey; }

    public void open()    { player.openInventory(inventory); }
    public void refresh() { render(); player.updateInventory(); }

    // ── Scroll (no bounds — the grid is infinite in every direction) ─────────
    public void scrollLeft()  { offsetX--; refresh(); }
    public void scrollRight() { offsetX++; refresh(); }
    public void scrollUp()    { offsetY--; refresh(); }
    public void scrollDown()  { offsetY++; refresh(); }

    // ─────────────────────────────────────────────────────────────────────────
    //  Render
    // ─────────────────────────────────────────────────────────────────────────

    private void render() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);

        PlayerSkillData        data = manager.loadPlayerData(player.getUniqueId());
        Map<String, SkillNode> tree = manager.getTree(treeKey);

        // Pass 1 — connector lines (drawn under nodes)
        for (SkillNode node : tree.values()) {
            if (node.isPassive()) continue;
            for (String depId : node.getDependsOn()) {
                SkillNode dep = tree.get(depId);
                if (dep != null)
                    drawLine(dep.getPosX(), dep.getPosY(), node.getPosX(), node.getPosY());
            }
        }

        // Pass 2 — nodes (overwrite line endpoints)
        for (SkillNode node : tree.values()) {
            if (node.isPassive()) continue;
            int sx = node.getPosX() - offsetX;
            int sy = node.getPosY() - offsetY;
            if (sx < 0 || sx > 8 || sy < 0 || sy > 4) continue;
            inventory.setItem(sy * 9 + sx, buildNodeItem(node, data));
        }

        // Pass 3 — nav bar (always on top)
        renderNavBar();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Node items
    // ─────────────────────────────────────────────────────────────────────────

    private ItemStack buildNodeItem(SkillNode node, PlayerSkillData data) {
        boolean unlocked  = node.isDefault() || data.isNodeUnlocked(treeKey, node.getNodeId());
        boolean canUnlock = !unlocked && manager.canUnlock(player, treeKey, node.getNodeId());

        int cmd = unlocked ? CMD_UNLOCKED : (canUnlock ? CMD_UNLOCKABLE : CMD_LOCKED);

        String prefix = unlocked ? "§a§l" : (canUnlock ? "§e§l" : "§c§l");
        String cleanName = stripColor(node.getDisplayName());

        List<String> lore = new ArrayList<>();
        for (String line : node.getLore()) lore.add("§7" + stripColor(line));

        if (node.getSubelement() != null) {
            boolean hasSub = manager.hasSubelementAccess(player, node.getSubelement());
            lore.add("");
            lore.add((hasSub ? "§d✦ " : "§c✦ ") + "§7Sub-element: §d" + node.getSubelement()
                    + (hasSub ? "" : " §c(you don't have this sub-element)"));
        }

        lore.add("");
        lore.add(unlocked ? "§a✔ Unlocked" : (canUnlock ? "§e▶ Click to unlock!" : "§c✖ Locked"));

        // Requirements
        lore.add("");
        lore.add("§8Requirements:");
        appendRequirements(lore, node);

        // Dependencies
        if (!node.getDependsOn().isEmpty()) {
            lore.add("");
            lore.add("§8Requires:");
            for (String depId : node.getDependsOn()) {
                SkillNode dep     = manager.getNode(treeKey, depId);
                boolean   depDone = data.isNodeUnlocked(treeKey, depId)
                        || (dep != null && dep.isDefault());
                String    depName = dep != null ? stripColor(dep.getDisplayName()) : depId;
                lore.add(depDone ? "§a✔ §f" + depName : "§c✖ §f" + depName);
            }
        }

        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(prefix + cleanName);
        meta.setLore(lore);
        meta.setCustomModelData(cmd);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP);

        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, NODE_ID_KEY),  PersistentDataType.STRING, node.getNodeId());
        meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, TREE_KEY_KEY), PersistentDataType.STRING, treeKey);

        item.setItemMeta(meta);
        return item;
    }

    private void appendRequirements(List<String> lore, SkillNode node) {
        SkillRequirements req = node.getRequirements();
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (pd == null) return;

        int xp      = pd.getCustomXP();
        int level   = manager.computeLevel(xp);
        long ptMin  = pd.getPlaytimeSeconds() / 60;
        int  kills  = plugin.getStatsManager().getKillCount(player.getUniqueId());

        if (req.getLevel() > 0) {
            boolean ok = level >= req.getLevel();
            lore.add((ok ? "§a✔" : "§c✖") + " §fLevel §e" + req.getLevel()
                    + " §8(you: " + (ok ? "§a" : "§c") + level + "§8)");
        }
        int reqXp = req.getEffectiveXp(manager.getBaseXp());
        if (reqXp > 0) {
            boolean ok = xp >= reqXp;
            lore.add((ok ? "§a✔" : "§c✖") + " §fXP §e" + reqXp
                    + " §8(you: " + (ok ? "§a" : "§c") + xp + "§8)");
        }
        int reqPt = req.getEffectivePlaytimeMin(manager.getBasePtMin());
        if (reqPt > 0) {
            boolean ok = ptMin >= reqPt;
            lore.add((ok ? "§a✔" : "§c✖") + " §fPlaytime §e" + reqPt + "m"
                    + " §8(you: " + (ok ? "§a" : "§c") + ptMin + "m§8)");
        }
        int reqKl = req.getEffectiveKills(manager.getBaseKills());
        if (reqKl > 0) {
            boolean ok = kills >= reqKl;
            lore.add((ok ? "§a✔" : "§c✖") + " §fKills §e" + reqKl
                    + " §8(you: " + (ok ? "§a" : "§c") + kills + "§8)");
        }

        // Show "none" if there are genuinely no requirements (not a default node)
        if (req.getLevel() == 0 && req.getEffectiveXp(manager.getBaseXp()) == 0
                && req.getEffectivePlaytimeMin(manager.getBasePtMin()) == 0
                && req.getEffectiveKills(manager.getBaseKills()) == 0
                && !node.isDefault()) {
            lore.add("§7None");
        }
        if (node.isDefault()) {
            lore.add("§a★ §7Default move — always unlocked");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Connector lines
    // ─────────────────────────────────────────────────────────────────────────

    private void drawLine(int x1, int y1, int x2, int y2) {
        if (x1 == x2) {
            int min = Math.min(y1, y2), max = Math.max(y1, y2);
            for (int y = min + 1; y < max; y++) placeLineIfEmpty(x1, y, CMD_LINE_V);
        } else if (y1 == y2) {
            int min = Math.min(x1, x2), max = Math.max(x1, x2);
            for (int x = min + 1; x < max; x++) placeLineIfEmpty(x, y1, CMD_LINE_H);
        }
        // Diagonal connections not supported — keep node positions axis-aligned in config
    }

    private void placeLineIfEmpty(int gx, int gy, int cmd) {
        int sx = gx - offsetX, sy = gy - offsetY;
        if (sx < 0 || sx > 8 || sy < 0 || sy > 4) return;
        int slot = sy * 9 + sx;
        if (slot < 0 || slot >= 45 || inventory.getItem(slot) != null) return;

        ItemStack line = new ItemStack(Material.PAPER);
        ItemMeta  meta = line.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.setCustomModelData(cmd);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            line.setItemMeta(meta);
        }
        inventory.setItem(slot, line);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Navigation bar
    //  45=◀  46=░  47=▲  48=░  49=ℹ  50=░  51=▼  52=░  53=▶
    // ─────────────────────────────────────────────────────────────────────────

    private void renderNavBar() {
        inventory.setItem(NAV_LEFT,  navItem(CMD_ARROW_LEFT,   treeColor() + "◀ §7Scroll Left"));
        inventory.setItem(NAV_UP,    navItem(CMD_ARROW_UP,     treeColor() + "▲ §7Scroll Up"));
        inventory.setItem(NAV_INFO,  buildInfoItem());
        inventory.setItem(NAV_DOWN,  navItem(CMD_ARROW_DOWN,   treeColor() + "▼ §7Scroll Down"));
        inventory.setItem(NAV_RIGHT, navItem(CMD_ARROW_RIGHT,  treeColor() + "▶ §7Scroll Right"));
        // Slots 46, 48, 50, 52 — left empty (resource pack paints them as dark separators)
    }

    private ItemStack navItem(int cmd, String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setCustomModelData(cmd);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack buildInfoItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(treeColor() + "§l" + treeLabel() + " Skill Tree");

        PlayerSkillData        data  = manager.loadPlayerData(player.getUniqueId());
        Map<String, SkillNode> tree  = manager.getTree(treeKey);
        long total    = tree.values().stream().filter(n -> !n.isPassive()).count();
        long unlocked = tree.values().stream()
                .filter(n -> !n.isPassive()
                        && (n.isDefault() || data.isNodeUnlocked(treeKey, n.getNodeId())))
                .count();

        List<String> lore = new ArrayList<>();
        lore.add("§7Tree: " + treeColor() + treeLabel());
        lore.add("§7Progress: §f" + unlocked + " §8/ §f" + total + " §7moves");
        lore.add("§8View offset: §7(" + offsetX + ", " + offsetY + ")");
        lore.add("");
        lore.add("§a█ §fGreen  §8= Unlocked");
        lore.add("§e█ §fYellow §8= Available to unlock");
        lore.add("§c█ §fRed    §8= Locked");
        lore.add("");
        lore.add("§7Use the arrows to scroll the tree.");
        if (treeKey.equals("avatar")) {
            lore.add("");
            lore.add("§e✦ §7Avatar moves are §enever §7lost on death.");
        }
        if (treeKey.equals("chi")) {
            lore.add("");
            lore.add("§6⚡ §7Chi abilities are §6exclusive §7to Chi benders.");
        }

        meta.setLore(lore);
        meta.setCustomModelData(CMD_INFO);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String treeColor() {
        return switch (treeKey) {
            case "water"  -> "§b";
            case "earth"  -> "§a";
            case "fire"   -> "§c";
            case "air"    -> "§f";
            case "chi"    -> "§6";
            case "avatar" -> "§e";
            default       -> "§7";
        };
    }

    private String treeLabel() {
        return switch (treeKey) {
            case "water"  -> "Water";
            case "earth"  -> "Earth";
            case "fire"   -> "Fire";
            case "air"    -> "Air";
            case "chi"    -> "Chi";
            case "avatar" -> "✦ Avatar";
            default       -> Character.toUpperCase(treeKey.charAt(0)) + treeKey.substring(1);
        };
    }

    private static String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                .replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }
}