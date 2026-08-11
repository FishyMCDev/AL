package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.guis.SkillTreeGUI;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.skilltree.PlayerSkillData;
import fishy.avatarlegacy.skilltree.SkillNode;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Handles all player interaction with the SkillTreeGUI.
 *
 *  1. InventoryClickEvent — cancel all; route scroll / unlock / info
 *  2. InventoryDragEvent  — cancel (prevents item dupe/ghost)
 *  3. InventoryCloseEvent — no-op (GUIs are ephemeral)
 *  4. PlayerDeathEvent    — remove one non-default move (if enabled in config)
 */
public class SkillTreeListener implements Listener {

    private final AvatarLegacy plugin;

    public SkillTreeListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Click
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof SkillTreeGUI gui)) return;

        // Always cancel — skill tree GUI is view-only except for explicit actions
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        // ── Navigation bar ────────────────────────────────────────────────────
        switch (slot) {
            case SkillTreeGUI.NAV_LEFT  -> { gui.scrollLeft();  return; }
            case SkillTreeGUI.NAV_RIGHT -> { gui.scrollRight(); return; }
            case SkillTreeGUI.NAV_UP    -> { gui.scrollUp();    return; }
            case SkillTreeGUI.NAV_DOWN  -> { gui.scrollDown();  return; }
            case SkillTreeGUI.NAV_INFO  -> { return; } // Info pane — click does nothing
        }
        if (slot >= 45) return; // Other nav-bar spacer slots

        // ── Node area (slots 0–44) ────────────────────────────────────────────
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        NamespacedKey nodeKey    = new NamespacedKey(plugin, SkillTreeGUI.NODE_ID_KEY);
        NamespacedKey treeKeyKey = new NamespacedKey(plugin, SkillTreeGUI.TREE_KEY_KEY);

        String nodeId  = meta.getPersistentDataContainer().get(nodeKey,    PersistentDataType.STRING);
        String treeKey = meta.getPersistentDataContainer().get(treeKeyKey, PersistentDataType.STRING);

        // Line items and spacers have no PDC tags — ignore silently
        if (nodeId == null || treeKey == null) return;

        SkillNode node = plugin.getSkillTreeManager().getNode(treeKey, nodeId);
        if (node == null) return;

        PlayerSkillData data = plugin.getSkillTreeManager().loadPlayerData(player.getUniqueId());

        // Default moves: always unlocked, inform player
        if (node.isDefault()) {
            player.sendMessage("§a✔ §fThis is a default move and is always unlocked.");
            return;
        }

        // Already unlocked
        if (data.isNodeUnlocked(treeKey, nodeId)) {
            player.sendMessage("§e▶ §fYou have already unlocked §a" + node.getDisplayName() + "§f.");
            return;
        }

        // Check requirements
        if (!plugin.getSkillTreeManager().canUnlock(player, treeKey, nodeId)) {
            player.sendMessage("§c✖ §fYou don't meet the requirements for §c" + node.getDisplayName() + "§f:");
            for (String line : plugin.getSkillTreeManager().getUnmetRequirements(player, treeKey, nodeId)) {
                player.sendMessage("  " + line);
            }
            return;
        }

        // ── Unlock! ───────────────────────────────────────────────────────────
        plugin.getSkillTreeManager().unlockNode(player, treeKey, nodeId);

        // Refresh GUI after a short delay so the LuckPerms async save settles
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()
                    && player.getOpenInventory().getTopInventory().getHolder() instanceof SkillTreeGUI g) {
                g.refresh();
            }
        }, 2L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Drag — cancel to prevent ghost items
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SkillTreeGUI) {
            event.setCancelled(true);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Close — no cleanup needed (GUIs are created on demand)
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // Intentionally empty — GUIs are ephemeral and hold no persistent state.
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Death — remove one random non-default node
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * MONITOR priority so we see the final event state after all other plugins.
     * Delayed by 1 tick so StatsManager (HIGHEST) processes XP/stat debuffs first.
     *
     * Skips:
     *  - Players with only default moves (nothing to remove).
     *  - Avatar players' avatar tree (never touched on death).
     *  - If skill-tree.remove-move-on-death is false in config.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (pd == null || pd.getElement() == null) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!plugin.getConfig().getBoolean("skill-tree.remove-move-on-death", true)) return;

            SkillNode removed = plugin.getSkillTreeManager().removeRandomMoveOnDeath(player);

            if (removed != null) {
                String template = plugin.getSkillTreeManager().getConfig()
                        .getString("messages.death-message",
                                "§4☠ §cYou lost knowledge of §e{move}§c!");
                String msg = template.replace("{move}", removed.getDisplayName());

                // Delay the message so it appears post-respawn (40 ticks = 2 s)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) player.sendMessage(msg);
                }, 40L);
            } else {
                // Inform the player if they had no moves to lose
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline())
                        player.sendMessage("§7You only have your default moves — nothing was lost.");
                }, 40L);
            }
        }, 1L);
    }
}