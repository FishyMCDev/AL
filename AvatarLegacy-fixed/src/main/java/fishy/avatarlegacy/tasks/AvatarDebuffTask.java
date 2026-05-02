package fishy.avatarlegacy.tasks;

import fishy.avatarlegacy.AvatarLegacy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.scheduler.BukkitRunnable;

public class AvatarDebuffTask extends BukkitRunnable {
    private final AvatarLegacy plugin;
    private int tickCount = 0;

    public AvatarDebuffTask(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tickCount++;

        
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin,
                () -> plugin.getAvatarManager().updateCandidateScores());

        
        if (tickCount % 30 == 0 && plugin.getAvatarManager().isReincarnationActive()) {
            int debuff = plugin.getConfig().getInt("avatar.global-debuff-during-reincarnation", 10);
            plugin.getServer().getOnlinePlayers().forEach(player ->
                    player.sendMessage(
                            Component.text("⚠ The Avatar has not yet reincarnated. All bending is weakened by "
                                    + debuff + "%.", NamedTextColor.DARK_RED)
                    )
            );
        }
    }
}
