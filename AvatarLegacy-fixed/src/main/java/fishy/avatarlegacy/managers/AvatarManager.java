package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class AvatarManager {

    private final AvatarLegacy plugin;
    private final String[] cycleOrder;
    private volatile java.util.UUID cachedCurrentAvatar = null;

    public AvatarManager(AvatarLegacy plugin) {
        this.plugin = plugin;
        
        java.util.List<String> configCycle = plugin.getConfig().getStringList("avatar.cycle-order");
        if (configCycle != null && configCycle.size() >= 2) {
            cycleOrder = configCycle.stream().map(String::toLowerCase).toArray(String[]::new);
        } else {
            cycleOrder = new String[]{"fire", "air", "water", "earth"};
        }
        initializeAvatarCycle();
        cachedCurrentAvatar = getCurrentAvatar();
    }

    private void initializeAvatarCycle() {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) as count FROM avatar_cycle")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt("count") == 0) {
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO avatar_cycle (current_cycle_element, cycle_start_timestamp, reincarnation_active) VALUES (?, ?, ?)")) {
                    insertStmt.setString(1, "fire");
                    insertStmt.setLong(2, System.currentTimeMillis());
                    insertStmt.setBoolean(3, false);
                    insertStmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize avatar cycle: " + e.getMessage());
        }
    }

    public void selectFirstAvatar() {
        String cycleElement = getCurrentCycleElement();
        List<UUID> eligible = getEligiblePlayersForCycle(cycleElement);
        if (eligible.isEmpty()) {
            plugin.getLogger().warning(
                "No eligible players for first Avatar selection! (cycle=" + cycleElement + ")");
            return;
        }
        setAvatar(eligible.get(new Random().nextInt(eligible.size())));
    }

    
    private List<UUID> getEligiblePlayersForCycle(String cycleElement) {
        List<UUID> eligible = new ArrayList<>();
        int minPlaytimeHours = plugin.getConfig().getInt("avatar.first-avatar.min-playtime-hours", 5);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid FROM players WHERE element_permanent = 1 AND element = ? AND playtime_seconds >= ?")) {
            stmt.setString(1, cycleElement.toLowerCase());
            stmt.setLong(2, minPlaytimeHours * 3600L);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) eligible.add(UUID.fromString(rs.getString("uuid")));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get eligible players for cycle: " + e.getMessage());
        }
        return eligible;
    }

    public void setAvatar(UUID uuid) {
        UUID existing = getCurrentAvatar();
        if (existing != null && !existing.equals(uuid)) {
            plugin.getLuckPermsIntegration().revokeAvatarPermissions(existing);
            plugin.getLogger().info("[Avatar] Revoking avatar status from previous avatar before assigning new one.");
        }

        String currentCycle = getCurrentCycleElement();
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE avatar_cycle SET current_avatar_uuid = ?, reincarnation_active = 0")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set avatar: " + e.getMessage());
        }

        cachedCurrentAvatar = uuid;

        plugin.getLuckPermsIntegration().grantAvatarPermissions(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            plugin.getProjectKorraIntegration().addAllElements(player);

            boolean scrollsEnabled = plugin.getProjectKorraIntegration().isScrollsEnabled();
            for (String el : new String[]{"fire", "water", "earth", "air"}) {
                
                plugin.getLuckPermsIntegration().grantProtectedMovePermissions(player, el, uuid);
                plugin.getProtectedMovesManager().addProtectedMovesForElement(uuid, el);
                if (scrollsEnabled) {
                    for (String move : plugin.getConfig().getStringList("protected-default-moves." + el)) {
                        plugin.getScrollManager().giveScrollForUnlock(player, move);
                    }
                }
            }

            
            
            
            if (!scrollsEnabled) {
                List<String> alreadyLearned = plugin.getLuckPermsIntegration().getLearnedAbilities(uuid);
                for (String move : alreadyLearned) {
                    com.projectkorra.projectkorra.ability.CoreAbility ca =
                            com.projectkorra.projectkorra.ability.CoreAbility.getAbility(move);
                    if (ca != null && ca.getElement() != null
                            && ca.getElement().toString().equalsIgnoreCase("chi")) continue;
                    plugin.getLuckPermsIntegration().grantPermission(uuid, "bending.ability." + move.toLowerCase());
                }
            }
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        String playerName = data != null ? data.getUsername() : "Unknown";
        Bukkit.broadcast(MessageUtil.avatarReincarnationBroadcast(playerName, currentCycle));
        logAvatarHistory(uuid, currentCycle);
    }

    public void stripAvatarState(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        boolean scrollsEnabled = plugin.getProjectKorraIntegration().isScrollsEnabled();

        plugin.getLuckPermsIntegration().revokeAvatarPermissions(uuid);

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        final String originalElement = data != null ? data.getElement() : null;

        
        for (String el : new String[]{"fire", "water", "earth", "air"}) {
            List<String> moves = plugin.getConfig().getStringList("protected-default-moves." + el);
            boolean isOwnElement = el.equalsIgnoreCase(originalElement);
            for (String move : moves) {
                if (scrollsEnabled) {
                    
                    
                    if (!isOwnElement && player != null) {
                        plugin.getScrollManager().resetScrollProgress(player, move);
                    }
                } else {
                    
                    if (!isOwnElement) {
                        plugin.getLuckPermsIntegration().revokePermission(uuid, "bending.ability." + move.toLowerCase());
                    }
                }
            }
        }

        plugin.getProtectedMovesManager().clearProtectedMoves(uuid);

        if (player != null) {
            com.projectkorra.projectkorra.BendingPlayer bPlayer =
                    com.projectkorra.projectkorra.BendingPlayer.getBendingPlayer(player);
            if (bPlayer != null) {
                bPlayer.getAbilities().clear();
                bPlayer.getElements().clear();
                if (originalElement != null) {
                    plugin.getProjectKorraIntegration().setPlayerElement(player, originalElement);
                    plugin.getProjectKorraIntegration().grantDefaultMoves(player, originalElement);
                }
            }

            if (originalElement != null) {
                
                plugin.getLuckPermsIntegration().grantProtectedMovePermissions(player, originalElement);
                plugin.getProtectedMovesManager().setProtectedMoves(uuid, originalElement);
            }
        }

        if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);
    }

    public void handleAvatarDeath(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isAvatar(uuid)) return;

        endCurrentAvatarReign(uuid, player.getLastDamageCause() != null ?
                player.getLastDamageCause().getCause().name() : "UNKNOWN");

        cachedCurrentAvatar = null;

        Bukkit.broadcast(MessageUtil.avatarDeathBroadcast(player.getName()));
        startReincarnationPeriod();

        stripAvatarState(uuid);

        advanceCycle();

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) plugin.getCharacterManager().deactivateCharacter(uuid, "AVATAR_DEATH");
        }, 80L);
    }

    private void endCurrentAvatarReign(UUID uuid, String deathCause) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE avatar_history SET end_timestamp = ?, death_cause = ? WHERE avatar_uuid = ? AND end_timestamp IS NULL")) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, deathCause);
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to end avatar reign: " + e.getMessage());
        }
    }

    private void startReincarnationPeriod() {
        int days = plugin.getConfig().getInt("avatar.reincarnation-period-days", 7);
        long now = System.currentTimeMillis();
        long endTime = now + (days * 24L * 60 * 60 * 1000);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE avatar_cycle SET current_avatar_uuid = NULL, reincarnation_active = 1, reincarnation_start_timestamp = ?, reincarnation_end_timestamp = ?")) {
            stmt.setLong(1, now);
            stmt.setLong(2, endTime);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to start reincarnation period: " + e.getMessage());
        }
    }

    private void endReincarnationPeriod() {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE avatar_cycle SET reincarnation_active = 0")) {
            stmt.executeUpdate();
            plugin.getLogger().info("Reincarnation period has ended. A new Avatar can now be selected.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to end reincarnation period: " + e.getMessage());
        }
    }

    private void advanceCycle() {
        String current = getCurrentCycleElement();
        int currentIndex = Arrays.asList(cycleOrder).indexOf(current);
        String nextElement = cycleOrder[(currentIndex + 1) % cycleOrder.length];
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE avatar_cycle SET current_cycle_element = ?, cycle_start_timestamp = ?")) {
            stmt.setString(1, nextElement);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to advance cycle: " + e.getMessage());
        }
    }

    public String getCurrentCycleElement() {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT current_cycle_element FROM avatar_cycle LIMIT 1")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("current_cycle_element");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get current cycle: " + e.getMessage());
        }
        return "fire";
    }

    public UUID getCurrentAvatar() {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT current_avatar_uuid FROM avatar_cycle LIMIT 1")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String uuidStr = rs.getString("current_avatar_uuid");
                return uuidStr != null ? UUID.fromString(uuidStr) : null;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get current avatar: " + e.getMessage());
        }
        return null;
    }

    public boolean isAvatar(UUID uuid) {
        UUID current = getCurrentAvatar();
        return current != null && current.equals(uuid);
    }

    public boolean isAvatarCached(UUID uuid) {
        return cachedCurrentAvatar != null && cachedCurrentAvatar.equals(uuid);
    }

    public void clearCachedAvatar() {
        cachedCurrentAvatar = null;
    }

    
    public void refreshCachedAvatar() {
        cachedCurrentAvatar = getCurrentAvatar();
    }

    public boolean isReincarnationActive() {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT reincarnation_active, reincarnation_end_timestamp FROM avatar_cycle LIMIT 1")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                boolean active = rs.getBoolean("reincarnation_active");
                if (!active) return false;
                long endTimestamp = rs.getLong("reincarnation_end_timestamp");
                if (endTimestamp > 0 && System.currentTimeMillis() >= endTimestamp) {
                    endReincarnationPeriod();
                    return false;
                }
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check reincarnation status: " + e.getMessage());
        }
        return false;
    }

    public void updateCandidateScores() {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid FROM players WHERE element_permanent = 1 AND element != 'chi'")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) updateCandidateScore(UUID.fromString(rs.getString("uuid")));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update candidate scores: " + e.getMessage());
        }
    }

    public void updateCandidateScoreForPlayer(UUID uuid) {
        updateCandidateScore(uuid);
    }

    private void updateCandidateScore(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        long playtimeSeconds;

        if (data != null) {
            playtimeSeconds = data.getPlaytimeSeconds();
        } else {
            Connection conn2 = plugin.getDatabaseManager().getConnection();
            long[] vals = {0};
            try (PreparedStatement s = conn2.prepareStatement(
                    "SELECT playtime_seconds FROM players WHERE uuid = ?")) {
                s.setString(1, uuid.toString());
                ResultSet r = s.executeQuery();
                if (r.next()) { vals[0] = r.getLong("playtime_seconds"); }
            } catch (SQLException ignored) {}
            playtimeSeconds = vals[0];
        }

        int interconnection = getInterconnectionScore(uuid);
        double pkDamageDealt = plugin.getStatsManager().getProjectKorraDamageDealt(uuid);
        long blocksBroken = plugin.getStatsManager().getBlocksBroken(uuid);
        long blocksPlaced = plugin.getStatsManager().getBlocksPlaced(uuid);
        long blocksModified = Math.max(0, blocksBroken + blocksPlaced);

        int movesLearned = 0;
        if (plugin.getProjectKorraIntegration().isScrollsEnabled()) {
            Player online = Bukkit.getPlayer(uuid);
            java.util.Set<String> unique = new java.util.HashSet<>();
            if (online != null) {
                for (String a : plugin.getProjectKorraIntegration().getSlottedAbilities(online)) {
                    if (a != null) unique.add(a.toLowerCase());
                }
            }
            for (String a : plugin.getProtectedMovesManager().getProtectedMoves(uuid)) {
                if (a != null) unique.add(a.toLowerCase());
            }
            unique.removeIf(s -> s == null || s.isBlank());
            movesLearned = unique.size();
        } else {
            movesLearned = plugin.getLuckPermsIntegration().getLearnedAbilities(uuid).size();
        }

        double playtimeHours = playtimeSeconds / 3600.0;
        double capHours = plugin.getConfig().getDouble("avatar.score.playtime-cap-hours", 24.0);
        double cappedHours = Math.min(capHours, Math.max(0.0, playtimeHours));

        double baseScore = cappedHours
                * Math.max(1.0, pkDamageDealt)
                * Math.max(1.0, (double) blocksModified)
                * Math.max(1.0, (double) movesLearned);

        double score = baseScore * (1.0 + (Math.max(0, Math.min(100, interconnection)) / 100.0));

        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO avatar_candidates (uuid, interconnection_score, experience_score, playtime_score, average_score, eligible) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, interconnection);
            stmt.setInt(3, Math.max(0, movesLearned));
            stmt.setInt(4, (int) Math.floor(cappedHours));
            stmt.setDouble(5, score);
            stmt.setBoolean(6, true);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update candidate score: " + e.getMessage());
        }
    }

    private int getInterconnectionScore(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT interconnection_score FROM avatar_candidates WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("interconnection_score");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get interconnection score: " + e.getMessage());
        }
        return 0;
    }

    private void logAvatarHistory(UUID uuid, String element) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO avatar_history (avatar_uuid, element, start_timestamp) VALUES (?, ?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, element);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log avatar history: " + e.getMessage());
        }
    }

    public void setInterconnectionScore(UUID uuid, int score) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE avatar_candidates SET interconnection_score = ? WHERE uuid = ?")) {
            stmt.setInt(1, Math.min(score, 100));
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set interconnection score: " + e.getMessage());
        }
    }
}
