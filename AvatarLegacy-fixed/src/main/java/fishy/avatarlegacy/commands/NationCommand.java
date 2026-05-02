package fishy.avatarlegacy.commands;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.utils.CommandUtil;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NationCommand implements CommandExecutor {
    private final AvatarLegacy plugin;
    private final Map<UUID, Integer> pendingInvites  = new HashMap<>();
    private final Map<Integer, Integer> pendingTruces     = new HashMap<>();
    private final Map<Integer, Integer> pendingAlliances  = new HashMap<>();

    public NationCommand(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.error("Only players can use this command!"));
            return true;
        }
        if (CommandUtil.requiresCharacter(plugin, player)) return true;
        if (args.length == 0) { sendUsage(player); return true; }

        String subCmd = args[0].toLowerCase();

        Set<String> setupFreeCommands = Set.of(
                "create", "accept", "leave", "info", "list", "admin", "setspawn", "disband"
        );

        if (!setupFreeCommands.contains(subCmd)) {
            Integer playerNationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
            if (playerNationId != null && !plugin.getNationManager().isSetupComplete(playerNationId)) {
                boolean hasSpawn = plugin.getNationManager().hasSpawnSet(playerNationId);
                boolean hasCore  = plugin.getNationManager().hasCorePlaced(playerNationId);
                player.sendMessage(MessageUtil.error("\u00a7c\u00a7lYour nation is not fully set up yet!"));
                player.sendMessage(MessageUtil.warning((hasSpawn ? "\u00a7a\u2714" : "\u00a7c\u2718") + " Nation spawn (/nation setspawn)"));
                player.sendMessage(MessageUtil.warning((hasCore  ? "\u00a7a\u2714" : "\u00a7c\u2718") + " Nation core (place a beacon within " +
                        plugin.getConfig().getInt("core.max-distance-from-spawn", 10) + " blocks of spawn)"));
                return true;
            }
        }

        switch (subCmd) {
            case "create"   -> handleCreate(player, args);
            case "invite"   -> handleInvite(player, args);
            case "accept"   -> handleAccept(player);
            case "leave"    -> handleLeave(player);
            case "kick"     -> handleKick(player, args);
            case "setspawn" -> handleSetSpawn(player);
            case "spawn"    -> handleSpawn(player);
            case "tax"      -> handleTax(player, args);
            case "treasury" -> handleTreasury(player);
            case "deposit"  -> handleDeposit(player, args);
            case "withdraw" -> handleWithdraw(player, args);
            case "upgrade"  -> handleUpgrade(player);
            case "claim"    -> handleClaim(player);
            case "unclaim"  -> handleUnclaim(player);
            case "shield"   -> handleShield(player);
            case "war"      -> handleWar(player, args);
            case "truce"    -> handleTruce(player, args);
            case "ally"     -> handleAlly(player, args);
            case "enemy"    -> handleEnemy(player);
            case "toggle"   -> handleToggle(player, args);
            case "element"  -> handleElementSet(player, args);
            case "info"     -> handleInfo(player);
            case "list"     -> handleList(player);
            case "admin"    -> handleAdmin(player, args);
            case "disband"  -> handleDisband(player);
            default         -> sendUsage(player);
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(MessageUtil.error("Usage: /nation create <name>")); return; }
        if (plugin.getNationManager().getPlayerNation(player.getUniqueId()) != null) {
            player.sendMessage(MessageUtil.error("You are already in a nation!")); return;
        }
        if (!plugin.getNationManager().canCreateNation(player.getUniqueId())) {
            int requiredMinutes = plugin.getConfig().getInt("nation-creation.costs.playtime-minutes", 30);
            player.sendMessage(MessageUtil.error("You need at least " + requiredMinutes + " minutes of playtime to create a nation!")); return;
        }

        String nationName = args[1];
        if (plugin.getNationManager().getNationByName(nationName) != null) {
            player.sendMessage(MessageUtil.error("A nation with that name already exists!")); return;
        }

        int woodStacks   = plugin.getConfig().getInt("nation-creation.costs.wood-stacks", 5);
        int cobbleStacks = plugin.getConfig().getInt("nation-creation.costs.cobblestone-stacks", 5);
        int xpLevels     = plugin.getConfig().getInt("nation-creation.costs.xp-levels", 5);

        if (!hasEnoughItems(player, Material.OAK_LOG, woodStacks * 64) ||
                !hasEnoughItems(player, Material.COBBLESTONE, cobbleStacks * 64)) {
            player.sendMessage(MessageUtil.error("You need " + woodStacks + " stacks of oak logs and "
                    + cobbleStacks + " stacks of cobblestone!")); return;
        }
        if (player.getLevel() < xpLevels) {
            player.sendMessage(MessageUtil.error("You need at least " + xpLevels + " Minecraft XP levels! (You have "
                    + player.getLevel() + ")")); return;
        }

        removeItems(player, Material.OAK_LOG, woodStacks * 64);
        removeItems(player, Material.COBBLESTONE, cobbleStacks * 64);
        player.setLevel(player.getLevel() - xpLevels);

        int nationId = plugin.getNationManager().createNation(nationName, player.getUniqueId(), player.getLocation());
        if (nationId == -1) { player.sendMessage(MessageUtil.error("Failed to create nation! Please try again.")); return; }

        player.getInventory().addItem(new ItemStack(Material.BEACON, 1));
        player.sendMessage(MessageUtil.success("Nation '" + nationName + "' created!"));
        int graceDays = plugin.getConfig().getInt("nation-creation.grace-period-days", 7);
        player.sendMessage(MessageUtil.info("Your nation is protected from wars for " + graceDays + " days."));
        player.sendMessage(MessageUtil.warning("\u00a7e\u00a7lBefore using any nation commands, you must complete setup:"));
        player.sendMessage(MessageUtil.warning("\u00a7c\u2718 Step 1: /nation setspawn \u00a77- Stand where you want your nation spawn"));
        int maxDist = plugin.getConfig().getInt("core.max-distance-from-spawn", 10);
        player.sendMessage(MessageUtil.warning("\u00a7c\u2718 Step 2: Place the Beacon (in your inventory) within " + maxDist + " blocks of your spawn"));
        player.sendMessage(MessageUtil.info("The core must be above Y=" + plugin.getConfig().getInt("core.min-y-level", 62) + " and not buried underground."));
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(MessageUtil.error("Usage: /nation invite <player>")); return; }
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can invite players!")); return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(MessageUtil.error("Player not found or is offline!")); return; }
        if (plugin.getNationManager().getPlayerNation(target.getUniqueId()) != null) {
            player.sendMessage(MessageUtil.error("That player is already in a nation!")); return;
        }
        if (plugin.getNationManager().isOnKickCooldown(target.getUniqueId())) {
            player.sendMessage(MessageUtil.error("That player was recently kicked and must wait before joining!")); return;
        }

        PlayerData targetData = plugin.getPlayerDataManager().getPlayerData(target.getUniqueId());
        String targetElement = targetData != null ? targetData.getElement() : null;

        if (!plugin.getNationManager().getAllowMultiElement(nationId) && targetElement != null) {
            String nationElement = plugin.getNationManager().getNationElement(nationId);
            PlayerData leaderData = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            String referenceElement = (nationElement != null) ? nationElement
                    : (leaderData != null ? leaderData.getElement() : null);
            if (referenceElement != null && !targetElement.equalsIgnoreCase(referenceElement)) {
                player.sendMessage(MessageUtil.error("Your nation does not allow different element citizens! (" + referenceElement.toUpperCase() + " only)"));
                return;
            }
        }

        pendingInvites.put(target.getUniqueId(), nationId);
        target.sendMessage(MessageUtil.info("You have been invited to join " +
                plugin.getNationManager().getNationName(nationId) + "!"));
        target.sendMessage(MessageUtil.info("Use /nation accept to join. Expires in 5 minutes."));
        player.sendMessage(MessageUtil.success("Invitation sent to " + target.getName()));

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pendingInvites.getOrDefault(target.getUniqueId(), -1).equals(nationId)) {
                pendingInvites.remove(target.getUniqueId());
                if (target.isOnline()) target.sendMessage(MessageUtil.warning("Nation invitation expired!"));
            }
        }, 20L * 60 * 5);
    }

    private void handleAccept(Player player) {
        Integer nationId = pendingInvites.remove(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You have no pending nation invitation!")); return; }
        if (plugin.getNationManager().getPlayerNation(player.getUniqueId()) != null) {
            player.sendMessage(MessageUtil.error("You are already in a nation!")); return;
        }
        String nationName = plugin.getNationManager().getNationName(nationId);
        if (nationName == null) { player.sendMessage(MessageUtil.error("That nation no longer exists!")); return; }
        plugin.getNationManager().addCitizen(nationId, player.getUniqueId(), "citizen");
        plugin.getWorldGuardIntegration().updateNationMembers(nationId);
        player.sendMessage(MessageUtil.success("You have joined " + nationName + "!"));
        plugin.getNotificationManager().queueNotificationForNation(nationId, player.getName() + " has joined the nation!");
    }

    private void handleLeave(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("The leader cannot leave! Use /nation disband to disband the nation.")); return;
        }
        plugin.getNationManager().removeCitizen(nationId, player.getUniqueId());
        plugin.getWorldGuardIntegration().updateNationMembers(nationId);
        player.sendMessage(MessageUtil.success("You have left your nation."));
        plugin.getNotificationManager().queueNotificationForNation(nationId, player.getName() + " has left the nation.");
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(MessageUtil.error("Usage: /nation kick <player>")); return; }
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can kick players!")); return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { player.sendMessage(MessageUtil.error("Player not found!")); return; }
        UUID targetUuid = target.getUniqueId();
        if (targetUuid.equals(player.getUniqueId())) { player.sendMessage(MessageUtil.error("You cannot kick yourself!")); return; }
        Integer targetNation = plugin.getNationManager().getPlayerNation(targetUuid);
        if (targetNation == null || !targetNation.equals(nationId)) {
            player.sendMessage(MessageUtil.error("That player is not in your nation!")); return;
        }
        plugin.getNationManager().removeCitizen(nationId, targetUuid);
            plugin.getWorldGuardIntegration().updateNationMembers(nationId);
        plugin.getNationManager().setKickCooldown(targetUuid);
        target.sendMessage(MessageUtil.warning("You have been kicked from your nation!"));
        player.sendMessage(MessageUtil.success("Kicked " + target.getName() + " from the nation."));
        plugin.getNotificationManager().queueNotificationForNation(nationId, target.getName() + " was kicked from the nation.");
    }

    private void handleSetSpawn(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can set the spawn!")); return;
        }
        plugin.getNationManager().setNationSpawn(nationId, player.getLocation());
        player.sendMessage(MessageUtil.success("Nation spawn set!"));

        boolean hasCore = plugin.getNationManager().hasCorePlaced(nationId);
        if (!hasCore) {
            int maxDist = plugin.getConfig().getInt("core.max-distance-from-spawn", 10);
            player.sendMessage(MessageUtil.warning("\u00a7eNext: place a Beacon within " + maxDist
                    + " blocks of this location to set your nation core."));
            player.sendMessage(MessageUtil.info("The core must be above Y=" +
                    plugin.getConfig().getInt("core.min-y-level", 62) + " and visible (not buried)."));
        } else {
            player.sendMessage(MessageUtil.success("\u00a7aSetup complete! All nation commands are now available."));
        }
    }

    private void handleSpawn(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        org.bukkit.Location spawn = plugin.getNationManager().getNationSpawn(nationId);
        if (spawn == null) { player.sendMessage(MessageUtil.error("Nation spawn has not been set!")); return; }
        player.teleport(spawn);
        player.sendMessage(MessageUtil.success("Teleported to nation spawn!"));
    }

    private void handleTax(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(MessageUtil.error("Usage: /nation tax <rate>")); return; }
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can set the tax rate!")); return;
        }
        try {
            double rate = Double.parseDouble(args[1]);
            double maxRate = plugin.getConfig().getDouble("taxes.max-rate-percent", 50);
            if (rate < 0 || rate > maxRate) { player.sendMessage(MessageUtil.error("Tax rate must be between 0% and " + (int) maxRate + "%!")); return; }
            plugin.getNationManager().setTaxRate(nationId, rate);
            player.sendMessage(MessageUtil.success("Tax rate set to " + rate + "%"));
            plugin.getNotificationManager().queueNotificationForNation(nationId, "Tax rate changed to " + rate + "%");
        } catch (NumberFormatException e) { player.sendMessage(MessageUtil.error("Invalid number!")); }
    }

    private void handleTreasury(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        player.sendMessage(MessageUtil.prefix("Treasury", "Nation Treasury"));
        player.sendMessage(MessageUtil.info("Balance: " + MessageUtil.formatYen(plugin.getNationManager().getTreasury(nationId))));
        player.sendMessage(MessageUtil.info("Tax Rate: " + plugin.getNationManager().getTaxRate(nationId) + "%"));
    }

    private void handleDeposit(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(MessageUtil.error("Usage: /nation deposit <amount>")); return; }
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        try {
            double amount = Double.parseDouble(args[1]);
            if (amount <= 0) { player.sendMessage(MessageUtil.error("Amount must be positive!")); return; }
            if (plugin.getEconomyManager().withdraw(player.getUniqueId(), amount)) {
                plugin.getNationManager().depositTreasury(nationId, amount);
                player.sendMessage(MessageUtil.success("Deposited " + MessageUtil.formatYen(amount) + " to the nation treasury!"));
            } else {
                player.sendMessage(MessageUtil.error("Insufficient funds!"));
            }
        } catch (NumberFormatException e) { player.sendMessage(MessageUtil.error("Invalid amount!")); }
    }

    private void handleUpgrade(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can manage upgrades!")); return;
        }
        plugin.getNationUpgradeGUI().openGUI(player, nationId);
    }

    private void handleWar(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(MessageUtil.error("Usage: /nation war <declare|surrender> [nation]")); return; }
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can manage wars!")); return;
        }

        switch (args[1].toLowerCase()) {
            case "declare" -> {
                if (args.length < 3) { player.sendMessage(MessageUtil.error("Usage: /nation war declare <nation>")); return; }
                Integer targetNation = plugin.getNationManager().getNationByName(args[2]);
                if (targetNation == null) { player.sendMessage(MessageUtil.error("Nation not found!")); return; }
                if (targetNation.equals(nationId)) { player.sendMessage(MessageUtil.error("You cannot declare war on yourself!")); return; }
                if (!plugin.getWarManager().canDeclareWar(nationId, targetNation)) {
                    player.sendMessage(MessageUtil.error("Cannot declare war! Target is in grace period or you are already at war.")); return;
                }
                plugin.getWarManager().declareWar(nationId, targetNation);
                player.sendMessage(MessageUtil.success("War declared on " + args[2] + "!"));
            }
            case "surrender" -> {
                List<Integer> enemies = plugin.getWarManager().getEnemyNations(nationId);
                if (enemies.isEmpty()) { player.sendMessage(MessageUtil.error("You are not at war with anyone!")); return; }
                for (int enemyId : enemies) {
                    int warId = plugin.getWarManager().getActiveWarId(nationId, enemyId);
                    if (warId != -1) {
                        plugin.getWarManager().endWar(warId, "surrender");
                        String enemyName = plugin.getNationManager().getNationName(enemyId);
                        Bukkit.broadcast(MessageUtil.warning("Nation " +
                                plugin.getNationManager().getNationName(nationId) +
                                " has surrendered to " + enemyName + "!"));
                    }
                }
                player.sendMessage(MessageUtil.success("You have surrendered and all wars have ended."));
            }
            default -> player.sendMessage(MessageUtil.error("Usage: /nation war <declare|surrender> [nation]"));
        }
    }

    private void handleTruce(Player player, String[] args) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can manage truces!")); return;
        }

        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Usage: /nation truce <propose <nation>|accept>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "propose" -> {
                if (args.length < 3) { player.sendMessage(MessageUtil.error("Usage: /nation truce propose <nation>")); return; }
                Integer targetNation = plugin.getNationManager().getNationByName(args[2]);
                if (targetNation == null) { player.sendMessage(MessageUtil.error("Nation not found!")); return; }
                if (!plugin.getWarManager().isAtWar(nationId, targetNation)) {
                    player.sendMessage(MessageUtil.error("You are not at war with that nation!")); return;
                }
                pendingTruces.put(targetNation, nationId);
                player.sendMessage(MessageUtil.success("Truce proposed to " + args[2] + "!"));
                notifyNationLeader(targetNation,
                        plugin.getNationManager().getNationName(nationId) +
                        " has proposed a truce! Use /nation truce accept to end the war.");
            }
            case "accept" -> {
                Integer proposingNation = pendingTruces.remove(nationId);
                if (proposingNation == null) { player.sendMessage(MessageUtil.error("No pending truce proposal for your nation!")); return; }
                int warId = plugin.getWarManager().getActiveWarId(nationId, proposingNation);
                if (warId == -1) { player.sendMessage(MessageUtil.error("No active war found!")); return; }
                plugin.getWarManager().endWar(warId, "truce");
                String n1 = plugin.getNationManager().getNationName(nationId);
                String n2 = plugin.getNationManager().getNationName(proposingNation);
                Bukkit.broadcast(MessageUtil.info("A truce has been agreed between " + n1 + " and " + n2 + ". Peace restored."));
            }
            default -> player.sendMessage(MessageUtil.error("Usage: /nation truce <propose <nation>|accept>"));
        }
    }

    private void handleAlly(Player player, String[] args) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can manage alliances!")); return;
        }

        if (args.length < 2) {
            player.sendMessage(MessageUtil.error("Usage: /nation ally <propose <nation>|accept>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "propose" -> {
                if (args.length < 3) { player.sendMessage(MessageUtil.error("Usage: /nation ally propose <nation>")); return; }
                Integer targetNation = plugin.getNationManager().getNationByName(args[2]);
                if (targetNation == null) { player.sendMessage(MessageUtil.error("Nation not found!")); return; }
                if (targetNation.equals(nationId)) { player.sendMessage(MessageUtil.error("You cannot ally with yourself!")); return; }
                if (plugin.getWarManager().isAtWar(nationId, targetNation)) {
                    player.sendMessage(MessageUtil.error("You cannot propose an alliance during wartime!")); return;
                }
                pendingAlliances.put(targetNation, nationId);
                player.sendMessage(MessageUtil.success("Alliance proposed to " + args[2] + "!"));
                notifyNationLeader(targetNation,
                        plugin.getNationManager().getNationName(nationId) +
                        " has proposed an alliance! Use /nation ally accept to confirm.");
            }
            case "accept" -> {
                Integer proposingNation = pendingAlliances.remove(nationId);
                if (proposingNation == null) { player.sendMessage(MessageUtil.error("No pending alliance proposal for your nation!")); return; }
                saveAlliance(proposingNation, nationId);
                String n1 = plugin.getNationManager().getNationName(nationId);
                String n2 = plugin.getNationManager().getNationName(proposingNation);
                Bukkit.broadcast(MessageUtil.success("An alliance has been formed between " + n1 + " and " + n2 + "!"));
            }
            default -> player.sendMessage(MessageUtil.error("Usage: /nation ally <propose <nation>|accept>"));
        }
    }

    private void saveAlliance(int nation1Id, int nation2Id) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO nation_alliances (nation_1_id, nation_2_id, proposed_by_nation_id, status, proposal_timestamp) VALUES (?, ?, ?, 'active', ?)")) {
            stmt.setInt(1, nation1Id);
            stmt.setInt(2, nation2Id);
            stmt.setInt(3, nation1Id);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save alliance: " + e.getMessage());
        }
    }

    private void handleEnemy(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        List<UUID> enemies = plugin.getWarManager().getEnemyPlayers(nationId);
        if (enemies.isEmpty()) { player.sendMessage(MessageUtil.info("Your nation is not at war with anyone.")); return; }
        player.sendMessage(MessageUtil.prefix("Enemies", "Enemy Players:"));
        for (UUID enemy : enemies) {
            Player ep = Bukkit.getPlayer(enemy);
            String name = ep != null ? ep.getName()
                    : (plugin.getPlayerDataManager().getPlayerData(enemy) != null
                       ? plugin.getPlayerDataManager().getPlayerData(enemy).getUsername()
                       : enemy.toString());
            player.sendMessage(MessageUtil.info("- " + name));
        }
    }

    private void handleToggle(Player player, String[] args) {
        if (args.length < 2) { player.sendMessage(MessageUtil.error("Usage: /nation toggle <multielement|refugees>")); return; }
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can toggle settings!")); return;
        }
        switch (args[1].toLowerCase()) {
            case "multielement", "refugees" -> {
                boolean cur = plugin.getNationManager().getAllowMultiElement(nationId);
                plugin.getNationManager().setAllowMultiElement(nationId, !cur);
                player.sendMessage(MessageUtil.success("Multi-element / refugee citizenship: " + (!cur ? "ENABLED" : "DISABLED")));
                player.sendMessage(MessageUtil.info(!cur
                        ? "Players of any element can now join your nation."
                        : "Only players matching your nation's element can now join."));
            }
            default -> player.sendMessage(MessageUtil.error("Unknown setting! Use: multielement or refugees."));
        }
    }

    private void handleInfo(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }

        UUID leaderUuid = plugin.getNationManager().getNationLeader(nationId);
        PlayerData ld   = plugin.getPlayerDataManager().getPlayerData(leaderUuid);
        Player leaderOnline = Bukkit.getPlayer(leaderUuid);
        String leaderName = leaderOnline != null ? leaderOnline.getName()
                : (ld != null ? ld.getUsername() : "Unknown");

        player.sendMessage(MessageUtil.prefix("Nation", plugin.getNationManager().getNationName(nationId)));
        player.sendMessage(MessageUtil.info("Leader: " + leaderName));
        player.sendMessage(MessageUtil.info("Citizens: " + plugin.getNationManager().getNationCitizens(nationId).size()));
        player.sendMessage(MessageUtil.info("Treasury: " + MessageUtil.formatYen(plugin.getNationManager().getTreasury(nationId))));
        player.sendMessage(MessageUtil.info("Tax Rate: " + plugin.getNationManager().getTaxRate(nationId) + "%"));
        player.sendMessage(MessageUtil.info("Grace Period: " + (plugin.getNationManager().isInGracePeriod(nationId) ? "Active" : "Expired")));
        player.sendMessage(MessageUtil.info("Multi-Element / Refugees: " + (plugin.getNationManager().getAllowMultiElement(nationId) ? "Allowed" : "Own element only")));
        player.sendMessage(MessageUtil.info("At War: " + (!plugin.getWarManager().getEnemyNations(nationId).isEmpty() ? "Yes" : "No")));
    }

    private void handleList(Player player) {
        player.sendMessage(MessageUtil.prefix("Nations", "All Nations:"));
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT n.id, n.name, n.leader_uuid, " +
                "(SELECT COUNT(*) FROM nation_citizens nc WHERE nc.nation_id = n.id) as cnt " +
                "FROM nations n ORDER BY cnt DESC")) {
            ResultSet rs = stmt.executeQuery();
            boolean any = false;
            int rank = 1;
            while (rs.next()) {
                any = true;
                String name   = rs.getString("name");
                String leader;
                try {
                    UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
                    PlayerData ld = plugin.getPlayerDataManager().getPlayerData(leaderUuid);
                    leader = ld != null ? ld.getUsername() : "Unknown";
                } catch (Exception e) {
                    leader = "Unknown";
                }
                int cnt = rs.getInt("cnt");
                String el = plugin.getNationManager().getNationElement(rs.getInt("id"));
                String elTag = el != null ? " | Element: §e" + el.toUpperCase() + "§7" : "";
                player.sendMessage(MessageUtil.info(rank + ". " + name
                        + " | Leader: " + leader + " | Citizens: " + cnt + elTag));
                rank++;
            }
            if (!any) player.sendMessage(MessageUtil.warning("No nations exist yet."));
        } catch (SQLException e) {
            player.sendMessage(MessageUtil.error("Failed to load nation list!"));
            plugin.getLogger().severe("Failed to get nation list: " + e.getMessage());
        }
    }

    private void handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("avatarlegacy.admin") && !player.isOp()) { player.sendMessage(MessageUtil.error("No permission!")); return; }
        if (args.length < 3 || !args[1].equalsIgnoreCase("disband")) {
            player.sendMessage(MessageUtil.error("Usage: /nation admin disband <nation>")); return;
        }
        Integer nationId = plugin.getNationManager().getNationByName(args[2]);
        if (nationId == null) { player.sendMessage(MessageUtil.error("Nation not found!")); return; }
        plugin.getNationManager().disbandNation(nationId);
        player.sendMessage(MessageUtil.success("Nation disbanded!"));
        Bukkit.broadcast(MessageUtil.warning("Nation " + args[2] + " has been disbanded by an admin!"));
    }

    private boolean hasEnoughItems(Player player, org.bukkit.Material material, int amount) {
        int total = 0;
        for (org.bukkit.inventory.ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) total += stack.getAmount();
            if (total >= amount) return true;
        }
        return false;
    }

    private void removeItems(Player player, org.bukkit.Material material, int amount) {
        int remaining = amount;
        org.bukkit.inventory.ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            org.bukkit.inventory.ItemStack stack = contents[i];
            if (stack == null || stack.getType() != material) continue;
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
    }

    private void notifyNationLeader(int nationId, String message) {
        UUID leaderUuid = plugin.getNationManager().getNationLeader(nationId);
        if (leaderUuid == null) return;
        Player leader = Bukkit.getPlayer(leaderUuid);
        if (leader != null) {
            leader.sendMessage(MessageUtil.info(message));
        } else {
            plugin.getNotificationManager().queueNotification(leaderUuid, message);
        }
    }

    private void handleElementSet(Player player, String[] args) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can set the nation element!")); return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("set")) {
            player.sendMessage(MessageUtil.error("Usage: /nation element set <fire|water|earth|air|chi>"));
            String current = plugin.getNationManager().getNationElement(nationId);
            player.sendMessage(MessageUtil.info("Current nation element: " + (current != null ? current.toUpperCase() : "None")));
            return;
        }
        String element = args[2].toLowerCase();
        if (!element.equals("fire") && !element.equals("water") && !element.equals("earth")
                && !element.equals("air") && !element.equals("chi")) {
            player.sendMessage(MessageUtil.error("Valid elements: fire, water, earth, air, chi")); return;
        }
        plugin.getNationManager().setNationElement(nationId, element);
        player.sendMessage(MessageUtil.success("Nation element set to §e" + element.toUpperCase() + "§a!"));
        plugin.getNotificationManager().queueNotificationForNation(nationId,
                "Nation element has been set to " + element.toUpperCase() + " by " + player.getName());
    }

    private void handleClaim(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can claim land!")); return;
        }
        if (!plugin.getNationManager().canClaimMore(nationId)) {
            int max = plugin.getNationManager().getMaxClaims(nationId);
            player.sendMessage(MessageUtil.error("Your nation has reached its claim limit of " + max + " chunks! Upgrade Core Shield to expand."));
            return;
        }

        double costPerChunk = plugin.getConfig().getDouble("claiming.cost-per-chunk", 5000);
        if (!plugin.getNationManager().withdrawTreasury(nationId, costPerChunk)) {
            player.sendMessage(MessageUtil.error("Insufficient treasury funds! Claiming costs " + MessageUtil.formatYen(costPerChunk) + " per chunk."));
            return;
        }

        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        Integer existingOwner = plugin.getWorldGuardIntegration().getChunkOwner(chunk);
        if (existingOwner != null) {
            plugin.getNationManager().depositTreasury(nationId, costPerChunk);
            if (existingOwner.equals(nationId)) {
                player.sendMessage(MessageUtil.error("Your nation already owns this chunk!"));
            } else {
                player.sendMessage(MessageUtil.error("This chunk is already claimed by another nation!"));
            }
            return;
        }

        if (plugin.getWorldGuardIntegration().claimChunk(nationId, chunk)) {
            player.sendMessage(MessageUtil.success("Chunk claimed! Cost: " + MessageUtil.formatYen(costPerChunk)
                    + " | Claims: " + plugin.getWorldGuardIntegration().getClaimCount(nationId)
                    + "/" + plugin.getNationManager().getMaxClaims(nationId)));
        } else {
            plugin.getNationManager().depositTreasury(nationId, costPerChunk);
            player.sendMessage(MessageUtil.error("Failed to claim chunk. Try again."));
        }
    }

    private void handleUnclaim(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can unclaim land!")); return;
        }

        org.bukkit.Chunk chunk = player.getLocation().getChunk();
        Integer owner = plugin.getWorldGuardIntegration().getChunkOwner(chunk);
        if (owner == null || !owner.equals(nationId)) {
            player.sendMessage(MessageUtil.error("Your nation does not own this chunk!")); return;
        }

        if (plugin.getWorldGuardIntegration().unclaimChunk(nationId, chunk)) {
            player.sendMessage(MessageUtil.success("Chunk unclaimed."));
        } else {
            player.sendMessage(MessageUtil.error("Failed to unclaim chunk."));
        }
    }

    private void handleShield(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can activate the shield!")); return;
        }

        if (plugin.getNationManager().isShieldActive(nationId)) {
            long mins = plugin.getNationManager().getShieldRemainingMs(nationId) / 60000;
            player.sendMessage(MessageUtil.info("Shield is already active! Expires in " + mins + " minute(s)."));
            return;
        }

        long cooldownMs = plugin.getNationManager().getShieldCooldownRemainingMs(nationId);
        if (cooldownMs > 0) {
            long hours = cooldownMs / 3600000;
            long mins = (cooldownMs % 3600000) / 60000;
            player.sendMessage(MessageUtil.error("Shield is on cooldown! Available in " + hours + "h " + mins + "m."));
            return;
        }

        if (plugin.getNationManager().getShieldLevel(nationId) <= 0) {
            player.sendMessage(MessageUtil.error("Your nation has not purchased the Core Shield upgrade!"));
            return;
        }

        if (plugin.getNationManager().activateShield(nationId)) {
            long duration = plugin.getConfig().getLong("core.shield-duration-minutes", 60);
            player.sendMessage(MessageUtil.success("Nation core shield activated for " + duration + " minutes!"));
            org.bukkit.Bukkit.broadcast(MessageUtil.warning(plugin.getNationManager().getNationName(nationId)
                    + " has activated their nation core shield!"));
        } else {
            player.sendMessage(MessageUtil.error("Failed to activate shield."));
        }
    }

    private void handleWithdraw(Player player, String[] args) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can withdraw from the treasury!")); return;
        }
        if (args.length < 2) { player.sendMessage(MessageUtil.error("Usage: /nation withdraw <amount>")); return; }
        double amount;
        try { amount = Double.parseDouble(args[1]); }
        catch (NumberFormatException e) { player.sendMessage(MessageUtil.error("Invalid amount.")); return; }
        if (amount <= 0) { player.sendMessage(MessageUtil.error("Amount must be positive.")); return; }

        double treasury = plugin.getNationManager().getTreasury(nationId);
        if (treasury < amount) {
            player.sendMessage(MessageUtil.error("Insufficient treasury funds! Available: " + MessageUtil.formatYen(treasury)));
            return;
        }
        plugin.getNationManager().withdrawTreasury(nationId, amount);
        plugin.getEconomyManager().deposit(player.getUniqueId(), amount);
        player.sendMessage(MessageUtil.success("Withdrew " + MessageUtil.formatYen(amount) + " from the nation treasury."));
        player.sendMessage(MessageUtil.info("Treasury remaining: " + MessageUtil.formatYen(plugin.getNationManager().getTreasury(nationId))));
    }

    private void handleDisband(Player player) {
        Integer nationId = plugin.getNationManager().getPlayerNation(player.getUniqueId());
        if (nationId == null) { player.sendMessage(MessageUtil.error("You are not in a nation!")); return; }
        if (!player.isOp() && !plugin.getNationManager().isNationLeader(player.getUniqueId(), nationId)) {
            player.sendMessage(MessageUtil.error("Only the nation leader can disband the nation!")); return;
        }
        String nationName = plugin.getNationManager().getNationName(nationId);
        plugin.getNationManager().disbandNation(nationId);
        org.bukkit.Bukkit.broadcast(MessageUtil.warning("Nation " + nationName + " has been disbanded by its leader!"));
    }

    private void sendUsage(Player player) {
        player.sendMessage(MessageUtil.prefix("Nation", "Commands:"));
        player.sendMessage(MessageUtil.info("/nation create <name> | invite <p> | accept | leave | kick <p>"));
        player.sendMessage(MessageUtil.info("/nation setspawn | spawn | tax <rate> | treasury | deposit <amt>"));
        player.sendMessage(MessageUtil.info("/nation upgrade | info | list | enemy"));
        player.sendMessage(MessageUtil.info("/nation claim | unclaim | shield"));
        player.sendMessage(MessageUtil.info("/nation war <declare <n>|surrender>"));
        player.sendMessage(MessageUtil.info("/nation truce <propose <n>|accept>"));
        player.sendMessage(MessageUtil.info("/nation ally <propose <n>|accept>"));
        player.sendMessage(MessageUtil.info("/nation toggle <multielement|refugees>"));
        player.sendMessage(MessageUtil.info("/nation element set <fire|water|earth|air|chi>"));
    }
}
