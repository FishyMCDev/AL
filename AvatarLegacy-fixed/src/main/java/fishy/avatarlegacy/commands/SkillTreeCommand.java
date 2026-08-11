package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.guis.SkillTreeGUI;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * /skilltree [tree]
 * Aliases: /st  /skills  /abilities   (plugin.yml)
 *
 * ── Access rules ──────────────────────────────────────────────────────────────
 *
 *   No argument:
 *     • Normal bender   → opens their own element tree (water/earth/fire/air)
 *     • Chi bender      → opens the chi tree
 *     • Avatar          → opens their own element tree (they still have one element)
 *
 *   /skilltree chi:
 *     • Only chi benders may open this (NOT the Avatar — avatar cannot chi-bend)
 *
 *   /skilltree avatar:
 *     • Only the current Avatar may open this
 *
 *   /skilltree <element>:
 *     • Normal bender   → only their own element
 *     • Avatar          → any of water/earth/fire/air (they have all 4 trees)
 *     • avatarlegacy.admin → any tree, for testing/admin purposes
 *
 * ── Why these rules exist ─────────────────────────────────────────────────────
 *   Chi is a separate martial arts discipline — the Avatar is a spiritual
 *   bridge between elements, not a chi master. Giving the Avatar access to
 *   chi would break the lore.
 *   The Avatar tree is a bonus, parallel tree; the Avatar still progresses
 *   their own element tree the same as any other bender.
 */
public class SkillTreeCommand implements CommandExecutor, TabCompleter {

    private final AvatarLegacy plugin;

    private static final List<String> ELEMENT_TREES =
            List.of("water", "earth", "fire", "air");

    public SkillTreeCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use /" + label + ".");
            return true;
        }

        boolean isAdmin  = player.hasPermission("avatarlegacy.admin");
        boolean isAvatar = plugin.getAvatarManager().isAvatarCached(player.getUniqueId());

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (pd == null || pd.getElement() == null) {
            player.sendMessage("§cYou have not chosen an element yet!");
            return true;
        }

        String playerElement = pd.getElement().toLowerCase();

        // ── No argument — open the player's primary tree ──────────────────────
        if (args.length == 0) {
            // Chi benders open the chi tree; everyone else opens their element tree
            String treeKey = playerElement; // "water", "earth", "fire", "air", or "chi"
            openGUI(player, treeKey);
            return true;
        }

        // ── Argument supplied ─────────────────────────────────────────────────
        String requested = args[0].toLowerCase();

        switch (requested) {

            case "chi" -> {
                // Chi tree: chi benders only. Avatar cannot use chi.
                if (!playerElement.equals("chi") && !isAdmin) {
                    player.sendMessage("§cOnly Chi benders can open the Chi skill tree.");
                    return true;
                }
                if (isAvatar && !isAdmin) {
                    player.sendMessage("§cThe Avatar cannot use Chi bending.");
                    return true;
                }
                openGUI(player, "chi");
            }

            case "avatar" -> {
                // Avatar tree: only the current Avatar (or admin)
                if (!isAvatar && !isAdmin) {
                    player.sendMessage("§cOnly the Avatar can open the Avatar skill tree.");
                    return true;
                }
                openGUI(player, "avatar");
            }

            default -> {
                // Element tree: water / earth / fire / air
                if (!ELEMENT_TREES.contains(requested)) {
                    player.sendMessage("§cUnknown skill tree §e" + args[0] +
                            "§c. Valid: water, earth, fire, air, chi, avatar.");
                    return true;
                }

                // Avatar can open any element tree.
                // Normal benders can only open their own.
                // Admins can open any tree.
                if (!isAvatar && !isAdmin && !requested.equals(playerElement)) {
                    player.sendMessage("§cYou can only open your own element's skill tree.");
                    return true;
                }

                openGUI(player, requested);
            }
        }

        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void openGUI(Player player, String treeKey) {
        SkillTreeGUI gui = new SkillTreeGUI(
                plugin, player, treeKey, plugin.getSkillTreeManager());
        gui.open();
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (args.length != 1) return Collections.emptyList();

        if (!(sender instanceof Player player)) return Collections.emptyList();

        boolean isAdmin  = player.hasPermission("avatarlegacy.admin");
        boolean isAvatar = plugin.getAvatarManager().isAvatarCached(player.getUniqueId());

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String playerElement = (pd != null && pd.getElement() != null)
                ? pd.getElement().toLowerCase() : "";

        List<String> options = new ArrayList<>();

        if (isAdmin) {
            // Admins see every tree
            options.addAll(List.of("water", "earth", "fire", "air", "chi", "avatar"));
        } else if (isAvatar) {
            // Avatar sees all 4 element trees + their own avatar tree
            options.addAll(ELEMENT_TREES);
            options.add("avatar");
        } else if (playerElement.equals("chi")) {
            options.add("chi");
        } else if (!playerElement.isEmpty()) {
            options.add(playerElement);
        }

        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], options, completions);
        Collections.sort(completions);
        return completions;
    }
}