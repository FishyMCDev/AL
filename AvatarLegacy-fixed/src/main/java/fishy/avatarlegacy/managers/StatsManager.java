package fishy.avatarlegacy.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.ability.PassiveAbility;
import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.Bukkit;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.entity.Player;
import fishy.avatarlegacy.skilltree.SkillTreeManager;
import fishy.avatarlegacy.skilltree.SkillNode;
import fishy.avatarlegacy.skilltree.PlayerSkillData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class StatsManager {

    private final AvatarLegacy plugin;
    private final Gson gson;
    private final Random random;
    private final Map<UUID, Map<String, Integer>> xpSourceRemainders;
    private final Map<UUID, Long> recentDeathProcessing;
    private static final long DUPLICATE_DEATH_WINDOW_MS = 5_000L;

    public StatsManager(AvatarLegacy plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
        this.random = new Random();
        this.xpSourceRemainders = new ConcurrentHashMap<>();
        this.recentDeathProcessing = new ConcurrentHashMap<>();
    }

    public void createStats(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO player_stats " +
                "(uuid, death_count, bending_strength_percent, moves_removed, removed_moves, pk_damage_dealt, blocks_broken, blocks_placed) " +
                "VALUES (?, 0, 100, 0, '[]', 0, 0, 0)")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create stats: " + e.getMessage());
        }
    }

    
    public void handleDeath(Player player, String cause, String location) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = recentDeathProcessing.getOrDefault(uuid, 0L);
        long deathGap = plugin.getConfig().getLong("death-system.count-gap-minutes", 10) * 60_000L;
        if (now - last < deathGap) {
            return;
        }
        recentDeathProcessing.put(uuid, now);
        logDeath(uuid, cause, location);

        createStats(uuid);

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);

        
        
        
        
        if (data != null && "chi".equalsIgnoreCase(data.getElement())) {
            
            return;
        }

        incrementDeathCount(uuid);

        
        awardConfiguredXp(uuid, "death", 1);

        
        int currentDeaths = getDeathCount(uuid);
        int maxDeaths = getSpiritCap(uuid);
        if (currentDeaths >= maxDeaths) {
            final UUID fUuid = uuid;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) plugin.getCharacterManager().deactivateCharacter(fUuid, "SPIRIT_BROKEN");
            }, 80L);
            return;
        }

        int remaining = maxDeaths - currentDeaths;
        if (remaining <= 3 && remaining > 0) {
            final int fr = remaining;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                if (fr == 3) {
                    player.sendMessage("§6§l⚠ Your bending spirit is weakening!");
                    player.sendMessage("§e§lOnly " + fr + " deaths remain before your profile is lost forever!");
                } else if (fr == 2) {
                    player.sendMessage("§c§l⚠⚠ CRITICAL: Your bending spirit is nearly broken!");
                    player.sendMessage("§c§lOnly " + fr + " deaths remain. Choose your battles wisely!");
                } else if (fr == 1) {
                    player.sendMessage("§4§l☠ ☠ ☠  F I N A L  W A R N I N G  ☠ ☠ ☠");
                    player.sendMessage("§4§lONE MORE DEATH and your profile is PERMANENTLY DESTROYED!");
                }
            }, 80L);
        }

        if (data == null || data.getElement() == null) {
            return;
        }

        int currentStrength = getBendingStrength(uuid);
        int minStrength     = plugin.getConfig().getInt("debuffs.minimum-bending-strength", 50);

        if (currentStrength > minStrength) {
            int reduction   = plugin.getConfig().getInt("debuffs.bending-strength-reduction-per-death", 5);
            int newStrength = Math.max(minStrength, currentStrength - reduction);
            setBendingStrength(uuid, newStrength);
    
            final int fs = newStrength;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§c§l☠ Your bending strength dropped to §e" + fs + "%§c!");
                }
            }, 40L);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("§7Your bending strength is at the minimum (" + minStrength + "%).");
                }
            }, 40L);
        }

        removeRandomMove(player);
    }

    public int getSpiritCap(UUID uuid) {
        return plugin.getAvatarManager().isAvatar(uuid)
                ? plugin.getConfig().getInt("bending-spirit.avatar-max-deaths", 3)
                : plugin.getConfig().getInt("bending-spirit.max-deaths", 30);
    }

    public boolean isSpiritBroken(UUID uuid) {
        return getDeathCount(uuid) >= getSpiritCap(uuid);
    }

    private void removeRandomMove(Player player) {
        UUID uuid = player.getUniqueId();
        int movesRemoved = getMovesRemoved(uuid);
        int maxRemovable = plugin.getConfig().getInt("debuffs.max-moves-removable", 5);

        if (movesRemoved >= maxRemovable) {
                return;
        }

        List<String> protectedMoves = plugin.getProtectedMovesManager().getProtectedMoves(uuid)
                .stream().map(String::toLowerCase).collect(Collectors.toList());
        List<String> alreadyRemoved = getRemovedMoves(uuid)
                .stream().map(String::toLowerCase).collect(Collectors.toList());
        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data == null || data.getElement() == null) {
            return;
        }

        Set<String> candidateMoves = new LinkedHashSet<>();
        List<String> elements = new ArrayList<>();
        if (plugin.getAvatarManager().isAvatar(uuid)) {
            elements.addAll(List.of("fire", "water", "earth", "air"));
        } else {
            elements.add(data.getElement());
        }
        candidateMoves.addAll(plugin.getLuckPermsIntegration().getLearnedAbilities(uuid));

        List<String> available = new ArrayList<>(candidateMoves);
        available.removeIf(move -> containsIgnoreCase(protectedMoves, move));
        available.removeIf(move -> containsIgnoreCase(alreadyRemoved, move));
        available.removeIf(move -> elements.stream().noneMatch(element -> isMoveForElement(move, element)));
        available.removeIf(move -> !isBindableProjectKorraMove(move));

        if (available.isEmpty()) {
                return;
        }

        String moveToRemove = available.get(random.nextInt(available.size()));

        com.projectkorra.projectkorra.ability.CoreAbility ca =
                com.projectkorra.projectkorra.ability.CoreAbility.getAbility(moveToRemove);
        if (ca != null) moveToRemove = ca.getName();

        plugin.getProjectKorraIntegration().removeAbilityFromSlot(player, moveToRemove);
        plugin.getProjectKorraIntegration().unlearnAbility(player, moveToRemove);

        SkillTreeManager stm = plugin.getSkillTreeManager();
        if (stm != null) {
            String element = data.getElement();
            SkillNode node = stm.getNodeByAbility(element, moveToRemove);
            if (node != null) {
                PlayerSkillData psd = stm.loadPlayerData(uuid);
                psd.lockNode(element, node.getNodeId());
                stm.savePlayerData(psd);
            }
        }
        plugin.getLuckPermsIntegration().revokePermission(uuid, "bending.ability." + moveToRemove.toLowerCase());

        alreadyRemoved.add(moveToRemove);
        setRemovedMoves(uuid, alreadyRemoved);
        setMovesRemoved(uuid, movesRemoved + 1);
        if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);

        final String fm = moveToRemove;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage("§c§l☠ You lost access to §e" + fm + "§c due to your death!");
                player.sendMessage("§7You can restore it via §e/stats§7.");
            }
        }, 40L);
    }

    private boolean isMoveForElement(String moveName, String element) {
        if (moveName == null || element == null) return false;
        CoreAbility ability = CoreAbility.getAbility(moveName);
        if (ability == null || ability.getElement() == null) return false;
        Element abilityElement = ability.getElement();
        String abilityElementName = abilityElement instanceof Element.SubElement subElement
                ? subElement.getParentElement().getName()
                : abilityElement.getName();
        return abilityElementName.equalsIgnoreCase(element);
    }

    private boolean isBindableProjectKorraMove(String moveName) {
        if (moveName == null || moveName.isBlank()) return false;
        CoreAbility ability = CoreAbility.getAbility(moveName);
        return ability != null && ability.isEnabled() && !ability.isHiddenAbility() && !(ability instanceof PassiveAbility);
    }

    private boolean containsIgnoreCase(List<String> values, String needle) {
        if (needle == null) return false;
        for (String value : values) {
            if (needle.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    public int getBendingStrength(UUID uuid) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT bending_strength_percent FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("bending_strength_percent");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get bending strength: " + e.getMessage());
        }
        return 100;
    }

    public void setBendingStrength(UUID uuid, int strength) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET bending_strength_percent = ? WHERE uuid = ?")) {
            stmt.setInt(1, Math.max(0, Math.min(100, strength)));
            stmt.setString(2, uuid.toString());
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                plugin.getLogger().warning("[Stats] setBendingStrength: no rows updated for " + uuid);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set bending strength: " + e.getMessage());
        }
    }

    public int getDeathCount(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT death_count FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("death_count");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get death count: " + e.getMessage());
        }
        return 0;
    }

    public void reduceDeathCount(UUID uuid, int amount) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET death_count = MAX(0, death_count - ?) WHERE uuid = ?")) {
            stmt.setInt(1, amount);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to reduce death count: " + e.getMessage());
        }
        if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);
    }

    private void incrementDeathCount(UUID uuid) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET death_count = death_count + 1 WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to increment death count: " + e.getMessage());
        }
    }

    public int getMovesRemoved(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT moves_removed FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("moves_removed");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get moves removed: " + e.getMessage());
        }
        return 0;
    }

    private void setMovesRemoved(UUID uuid, int count) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET moves_removed = ? WHERE uuid = ?")) {
            stmt.setInt(1, count);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set moves removed: " + e.getMessage());
        }
    }

    public List<String> getRemovedMoves(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT removed_moves FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String json = rs.getString("removed_moves");
                if (json == null || json.isEmpty()) return new ArrayList<>();
                List<String> result = gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
                return result != null ? new ArrayList<>(result) : new ArrayList<>();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get removed moves: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    private void setRemovedMoves(UUID uuid, List<String> moves) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET removed_moves = ? WHERE uuid = ?")) {
            stmt.setString(1, gson.toJson(moves));
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set removed moves: " + e.getMessage());
        }
    }

    public void clearRemovedMovePenalty(UUID uuid, String moveName) {
        if (uuid == null || moveName == null || moveName.isBlank()) return;
        List<String> removed = getRemovedMoves(uuid);
        boolean changed = removed.removeIf(move -> move != null && move.equalsIgnoreCase(moveName));
        if (!changed) return;
        setRemovedMoves(uuid, removed);
        setMovesRemoved(uuid, removed.size());
        if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);
    }

    public void restoreBendingStrength(UUID uuid) {
        setBendingStrength(uuid, 100);
    }

    
    public void clearRemovedMovesOnly(UUID uuid) {
        setRemovedMoves(uuid, new ArrayList<>());
        setMovesRemoved(uuid, 0);
        if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);
    }

    public void restoreMove(UUID uuid, String moveName) {
        List<String> removed = getRemovedMoves(uuid);
        removed.removeIf(m -> m != null && moveName != null && m.equalsIgnoreCase(moveName));
        setRemovedMoves(uuid, removed);
        setMovesRemoved(uuid, removed.size());
        if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            plugin.getLuckPermsIntegration().grantPermission(uuid, "bending.ability." + moveName.toLowerCase());

            com.projectkorra.projectkorra.BendingPlayer bPlayer =
                    com.projectkorra.projectkorra.BendingPlayer.getBendingPlayer(player);
            if (bPlayer != null) {
                java.util.Map<Integer, String> slots = bPlayer.getAbilities();
                for (int slot = 1; slot <= 9; slot++) {
                    if (slots.get(slot) == null || slots.get(slot).isEmpty()) {
                        slots.put(slot, moveName);
                        break;
                    }
                }
            }

            player.sendMessage(MessageUtil.success("§e" + moveName + "§a has been restored!"));
        }
    }

    public int getKillCount(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT kill_count FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("kill_count");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get kill count: " + e.getMessage());
        }
        return 0;
    }

    public void incrementKillCount(UUID uuid) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET kill_count = kill_count + 1 WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to increment kill count: " + e.getMessage());
        }

        
        awardConfiguredXp(uuid, "kill", 1);
    }

    public void addProjectKorraDamageDealt(UUID uuid, double damage) {
        if (damage <= 0) return;
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET pk_damage_dealt = pk_damage_dealt + ? WHERE uuid = ?")) {
            stmt.setDouble(1, damage);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add PK damage dealt: " + e.getMessage());
        }
    }

    public double getProjectKorraDamageDealt(UUID uuid) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT pk_damage_dealt FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("pk_damage_dealt");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get PK damage dealt: " + e.getMessage());
        }
        return 0.0;
    }

    public void incrementBlocksBroken(UUID uuid) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET blocks_broken = blocks_broken + 1 WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to increment blocks broken: " + e.getMessage());
        }
        awardConfiguredXp(uuid, "block-break", 1);
    }

    public void incrementBlocksPlaced(UUID uuid) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET blocks_placed = blocks_placed + 1 WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to increment blocks placed: " + e.getMessage());
        }
        awardConfiguredXp(uuid, "block-place", 1);
    }

    public long getBlocksBroken(UUID uuid) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT blocks_broken FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("blocks_broken");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get blocks broken: " + e.getMessage());
        }
        return 0;
    }

    public long getBlocksPlaced(UUID uuid) {
        createStats(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT blocks_placed FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("blocks_placed");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get blocks placed: " + e.getMessage());
        }
        return 0;
    }

    public void resetStats(UUID uuid) {
        if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);
        List<String> removed = new ArrayList<>(getRemovedMoves(uuid));
        Player player = Bukkit.getPlayer(uuid);
        for (String move : removed) {
            plugin.getLuckPermsIntegration().grantPermission(uuid, "bending.ability." + move.toLowerCase());
            if (player != null) {
                com.projectkorra.projectkorra.BendingPlayer bPlayer =
                        com.projectkorra.projectkorra.BendingPlayer.getBendingPlayer(player);
                if (bPlayer != null) {
                    java.util.Map<Integer, String> slots = bPlayer.getAbilities();
                    for (int slot = 1; slot <= 9; slot++) {
                        if (slots.get(slot) == null || slots.get(slot).isEmpty()) {
                            slots.put(slot, move);
                            break;
                        }
                    }
                }
            }
        }
        setBendingStrength(uuid, 100);
        setRemovedMoves(uuid, new ArrayList<>());
        setMovesRemoved(uuid, 0);
        clearPreChiSnapshot(uuid); 

        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET death_count = 0 WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to reset death count: " + e.getMessage());
        }
    }

    

    
    public void savePreChiState(UUID uuid) {
        int currentStrength = getBendingStrength(uuid);
        List<String> currentRemoved = getRemovedMoves(uuid);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET pre_chi_bending_strength = ?, pre_chi_removed_moves = ? WHERE uuid = ?")) {
            stmt.setInt(1, currentStrength);
            stmt.setString(2, gson.toJson(currentRemoved));
            stmt.setString(3, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save pre-chi state: " + e.getMessage());
        }
    }

    
    public boolean restorePreChiState(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT pre_chi_bending_strength, pre_chi_removed_moves FROM player_stats WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            java.sql.ResultSet rs = stmt.executeQuery();
            if (!rs.next()) return false;

            int savedStrength = rs.getInt("pre_chi_bending_strength");
            String savedMovesJson = rs.getString("pre_chi_removed_moves");

            
            if (savedStrength == -1 && (savedMovesJson == null || savedMovesJson.isEmpty())) {
                clearPreChiSnapshot(uuid);
                return false;
            }

            
            if (savedStrength >= 0) {
                setBendingStrength(uuid, savedStrength);
            }

            
            if (savedMovesJson != null && !savedMovesJson.isEmpty()) {
                List<String> savedMoves = gson.fromJson(savedMovesJson,
                        new com.google.gson.reflect.TypeToken<List<String>>(){}.getType());
                if (savedMoves == null) savedMoves = new ArrayList<>();
                setRemovedMovesRaw(uuid, savedMoves);
                setMovesRemovedRaw(uuid, savedMoves.size());
            }

            clearPreChiSnapshot(uuid);
            if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to restore pre-chi state: " + e.getMessage());
        }
        return false;
    }

    
    public void clearPreChiSnapshot(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET pre_chi_bending_strength = -1, pre_chi_removed_moves = NULL WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear pre-chi snapshot: " + e.getMessage());
        }
    }

    
    private void setRemovedMovesRaw(UUID uuid, List<String> moves) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET removed_moves = ? WHERE uuid = ?")) {
            stmt.setString(1, gson.toJson(moves));
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set removed moves (raw): " + e.getMessage());
        }
    }

    private void setMovesRemovedRaw(UUID uuid, int count) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE player_stats SET moves_removed = ? WHERE uuid = ?")) {
            stmt.setInt(1, count);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set moves removed (raw): " + e.getMessage());
        }
    }

    public void awardConfiguredXp(UUID uuid, String source, int multiplier) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data == null) return;
        String basePath = "custom-xp.sources." + source;
        String amountPath = basePath + ".amount";
        if (!plugin.getConfig().getBoolean(basePath + ".enabled", true)) return;
        int repetitionsPerXp = Math.max(1, plugin.getConfig().getInt(basePath + ".repetitions-per-xp", 1));
        int base = plugin.getConfig().contains(amountPath) ? plugin.getConfig().getInt(amountPath, 0) : getLegacyXpSource(source);
        if (base == 0) return;
        int eventCount = Math.max(1, multiplier);

        int amountUnits;
        if (repetitionsPerXp <= 1) {
            amountUnits = eventCount;
        } else {
            Map<String, Integer> perSource = xpSourceRemainders.computeIfAbsent(uuid, k -> new HashMap<>());
            int carried = perSource.getOrDefault(source, 0);
            int total = carried + eventCount;
            amountUnits = total / repetitionsPerXp;
            int remainder = total % repetitionsPerXp;
            if (remainder > 0) {
                perSource.put(source, remainder);
            } else {
                perSource.remove(source);
                if (perSource.isEmpty()) {
                    xpSourceRemainders.remove(uuid);
                }
            }
        }

        if (amountUnits <= 0) return;
        int before = data.getCustomXP();
        int amount = base * amountUnits;
        int after = Math.max(0, before + amount);
        int applied = after - before;
        if (applied == 0) return;
        data.setCustomXP(after);

        if (plugin.getConfig().getBoolean("custom-xp.notify-messages", true)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                String prettySource = source.replace('-', ' ');
                String sign = applied > 0 ? "+" : "";
                player.sendMessage("§6[XP] §7" + sign + applied + " XP §8(" + prettySource + ")");
            }
        }
    }

    private int getLegacyXpSource(String source) {
        return switch (source.toLowerCase()) {
            case "kill" -> plugin.getConfig().getInt("custom-xp.per-kill", 1);
            case "death" -> plugin.getConfig().getInt("custom-xp.per-death", -1);
            case "playtime" -> plugin.getConfig().getInt("custom-xp.per-playtime-interval", 5);
            default -> 0;
        };
    }

    private void logDeath(UUID uuid, String cause, String location) {
        if (!plugin.getConfig().getBoolean("death-logging", true)) return;
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO death_log (uuid, timestamp, cause, location) VALUES (?, ?, ?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setString(3, cause);
            stmt.setString(4, location);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log death: " + e.getMessage());
        }
    }

    public int getDeathbanCount(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT deathban_count FROM player_penalties WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("deathban_count");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get deathban count: " + e.getMessage());
        }
        return 0;
    }

    public void incrementDeathbanCount(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO player_penalties (uuid, deathban_count) VALUES (?, 1) " +
                        "ON CONFLICT(uuid) DO UPDATE SET deathban_count = deathban_count + 1")) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to increment deathban count: " + e.getMessage());
        }
    }
}
