package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;

public final class RerollCommand implements CommandExecutor {
    private final AvatarLegacy plugin;
    public RerollCommand(AvatarLegacy plugin) { this.plugin = plugin; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("avatarlegacy.traits.reroll") && !player.hasPermission("avatarlegacy.admin")) { player.sendMessage(MessageUtil.error("You cannot reroll traits.")); return true; }
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getElement() == null || "chi".equalsIgnoreCase(data.getElement())) { player.sendMessage(MessageUtil.error("Your current element has no rerollable traits.")); return true; }
        List<String> traits = plugin.getTraitManager().applyTraits(player, data.getElement(), true);
        player.sendMessage(MessageUtil.success(traits.isEmpty() ? "Traits rerolled: none." : "Traits rerolled: " + String.join(", ", traits)));
        return true;
    }
}
