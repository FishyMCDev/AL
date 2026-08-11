package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Admin-only persistent spawn editor: /elementspawn set <element> <1|2|3>. */
public final class ElementSpawnCommand implements CommandExecutor {
    private final AvatarLegacy plugin;
    public ElementSpawnCommand(AvatarLegacy plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
        if (!player.hasPermission("avatarlegacy.admin")) { player.sendMessage(MessageUtil.error("No permission.")); return true; }
        if (args.length != 3 || !args[0].equalsIgnoreCase("set")) {
            player.sendMessage(MessageUtil.error("Usage: /elementspawn set <fire|water|earth|air> <1|2|3>")); return true;
        }
        String element = args[1].toLowerCase();
        if (!element.matches("fire|water|earth|air")) { player.sendMessage(MessageUtil.error("Choose fire, water, earth, or air.")); return true; }
        try {
            int slot = Integer.parseInt(args[2]);
            plugin.getElementManager().setSpawn(element, slot, player.getLocation());
            player.sendMessage(MessageUtil.success("Saved " + element + " spawn " + slot + " to the database."));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(MessageUtil.error("Spawn slot must be 1, 2, or 3."));
        } catch (Exception ex) {
            player.sendMessage(MessageUtil.error("Could not save the spawn; check the server log."));
        }
        return true;
    }
}
