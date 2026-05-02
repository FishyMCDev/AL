package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TreasuryCommand implements CommandExecutor {
    private final AvatarLegacy plugin;

    public TreasuryCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!"));
            return true;
        }

        if (CommandUtil.requiresCharacter(plugin, player)) return true;

        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());

        if (nationId == null) {
            player.sendMessage(MessageUtil.error("You are not in a nation!"));
            return true;
        }

        double treasury = plugin.getNationManager().getTreasury(nationId);
        player.sendMessage(MessageUtil.info("Nation treasury: " + MessageUtil.formatYen(treasury)));

        return true;
    }
}