package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ScrollManager {

    private final AvatarLegacy plugin;

    private Boolean primaryIsScroll = null;

    public ScrollManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public boolean isScrollsPluginEnabled() {
        return Bukkit.getPluginManager().getPlugin("ProjectKorraScrolls") != null;
    }

    public int getScrollCostForMove(String abilityName) {
        if (abilityName == null) return plugin.getConfig().getInt("scrolls.unlock-count", 2);

        int perMove = plugin.getConfig().getInt("scrolls.move-costs." + abilityName, -1);
        if (perMove >= 0) return perMove;

        return plugin.getConfig().getInt("scrolls.unlock-count", 2);
    }

    public void giveScroll(Player player, String abilityName, int amount) {
        if (!isScrollsPluginEnabled() || player == null || abilityName == null) return;
        dispatch("give " + player.getName() + " " + abilityName + " " + amount);
    }

    public void giveScrollForUnlock(Player player, String abilityName) {
        int cost = getScrollCostForMove(abilityName);
        giveScroll(player, abilityName, cost);
    }

    public void giveScrollForRestore(Player player, String abilityName) {
        if (!isScrollsPluginEnabled() || player == null || abilityName == null) return;

        resetScrollProgress(player, abilityName);

        int cost = getScrollCostForMove(abilityName);
        giveScroll(player, abilityName, cost);
    }

    public void resetScrollProgress(Player player, String abilityName) {
        if (!isScrollsPluginEnabled() || player == null || abilityName == null) return;
        dispatch("reset " + player.getName() + " " + abilityName);
    }

    public void resetAllScrollProgress(Player player) {
        if (!isScrollsPluginEnabled() || player == null) return;
        dispatch("resetprogress " + player.getName());
    }

    public void handleMoveRestoration(Player player, String moveName) {
        giveScrollForRestore(player, moveName);
    }

    public void handleMoveRemoval(Player player, String moveName) {
        resetScrollProgress(player, moveName);
    }

    private void dispatch(String subCommand) {
        if (primaryIsScroll == null) {
            boolean okScroll = Bukkit.dispatchCommand(plugin.getSilentSender(), "scroll " + subCommand);
            if (okScroll) {
                primaryIsScroll = true;
                plugin.getLogger().info("[ScrollManager] Detected command prefix: /scroll");
                return;
            }
            boolean okScrolls = Bukkit.dispatchCommand(plugin.getSilentSender(), "scrolls " + subCommand);
            if (okScrolls) {
                primaryIsScroll = false;
                plugin.getLogger().info("[ScrollManager] Detected command prefix: /scrolls");
                return;
            }
            primaryIsScroll = true;
            plugin.getLogger().warning("[ScrollManager] Neither /scroll nor /scrolls was recognised. "
                    + "Verify ProjectKorraScrolls is installed and loaded correctly.");
        } else if (primaryIsScroll) {
            if (!Bukkit.dispatchCommand(plugin.getSilentSender(), "scroll " + subCommand)) {
                Bukkit.dispatchCommand(plugin.getSilentSender(), "scrolls " + subCommand);
            }
        } else {
            if (!Bukkit.dispatchCommand(plugin.getSilentSender(), "scrolls " + subCommand)) {
                Bukkit.dispatchCommand(plugin.getSilentSender(), "scroll " + subCommand);
            }
        }
    }
}
