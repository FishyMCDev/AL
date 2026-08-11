package fishy.avatarlegacy.utils;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.entity.Player;

public class CommandUtil {

    
    public static boolean requiresProfile(AvatarLegacy plugin, Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(MessageUtil.error("Your profile is still loading. Please try again."));
            return true;
        }
        return false;
    }

    
    public static boolean requiresOp(Player player) {
        if (!player.isOp()) {
            player.sendMessage(MessageUtil.error("This command is for server operators only!"));
            return true;
        }
        return false;
    }
}
