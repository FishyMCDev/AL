package fishy.avatarlegacy.utils;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.entity.Player;

public class CommandUtil {

    
    public static boolean requiresCharacter(AvatarLegacy plugin, Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(MessageUtil.error("You must create a character first!"));
            player.sendMessage(MessageUtil.info("Use §e/character create <n>§7 to begin your journey."));
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
