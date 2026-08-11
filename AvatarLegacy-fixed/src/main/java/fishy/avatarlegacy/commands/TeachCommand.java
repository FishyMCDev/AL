package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** /teach — teach your bending element (including Chi) to the current Avatar. */
public final class TeachCommand implements CommandExecutor {

    private final AvatarLegacy plugin;

    public TeachCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player teacher)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        UUID avatarUuid = plugin.getAvatarManager().getCurrentAvatar();
        if (avatarUuid == null) {
            teacher.sendMessage(MessageUtil.error("There is no Avatar currently."));
            return true;
        }

        Player avatar = Bukkit.getPlayer(avatarUuid);
        if (avatar == null || !avatar.isOnline()) {
            teacher.sendMessage(MessageUtil.error("The Avatar is not online."));
            return true;
        }

        if (avatar.getUniqueId().equals(teacher.getUniqueId())) {
            teacher.sendMessage(MessageUtil.error("You cannot teach yourself."));
            return true;
        }

        plugin.getTeachManager().startTeaching(teacher, avatar);
        return true;
    }
}