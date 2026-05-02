package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CharacterManager {

    private final AvatarLegacy plugin;
    private final Map<UUID, String> characterNameCache = new ConcurrentHashMap<>();

    public CharacterManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public void cacheCharacterName(UUID uuid, String name) {
        if (name != null) characterNameCache.put(uuid, name);
        else characterNameCache.remove(uuid);
    }

    public void evictCache(UUID uuid) {
        characterNameCache.remove(uuid);
    }

    public String getCachedCharacterName(UUID uuid) {
        return characterNameCache.get(uuid);
    }

    public void createCharacter(UUID uuid, String characterName) {
        long now = System.currentTimeMillis();
        int cooldownDays = plugin.getConfig().getInt("character.deletion-cooldown-days", 7);
        long deletionAllowed = now + (cooldownDays * 24L * 60 * 60 * 1000);

        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO characters " +
                "(uuid, creation_timestamp, deletion_allowed_timestamp, character_name, restore_count, last_restore_timestamp) " +
                "VALUES (?, ?, ?, ?, 0, 0)")) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, now);
            stmt.setLong(3, deletionAllowed);
            stmt.setString(4, characterName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create character: " + e.getMessage());
        }
    }

    public boolean hasActiveCharacter(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT uuid FROM characters WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check active character: " + e.getMessage());
        }
        return false;
    }

    public String getCharacterName(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT character_name FROM characters WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("character_name");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get character name: " + e.getMessage());
        }
        return null;
    }

    public int getRestoreCount(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT restore_count FROM characters WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("restore_count");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get restore count: " + e.getMessage());
        }
        return 0;
    }

    public long getLastRestoreTimestamp(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT last_restore_timestamp FROM characters WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("last_restore_timestamp");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get last restore timestamp: " + e.getMessage());
        }
        return 0;
    }

    public void incrementRestoreCount(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE characters SET restore_count = restore_count + 1, last_restore_timestamp = ? WHERE uuid = ?")) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to increment restore count: " + e.getMessage());
        }
    }

    
    public void deactivateCharacter(UUID uuid, String reason) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data == null) return;

        String characterName  = getCharacterName(uuid);
        int deaths    = plugin.getStatsManager().getDeathCount(uuid);
        int kills     = plugin.getStatsManager().getKillCount(uuid);
        long playtime = data.getPlaytimeSeconds();
        int xp        = data.getCustomXP();
        String element = data.getElement();
        boolean wasAvatar = plugin.getAvatarManager().isAvatar(uuid);
        long createdAt = getCreationTimestamp(uuid);

        archiveToHistory(uuid, characterName, data.getUsername(), element,
                deaths, kills, playtime, xp, wasAvatar, reason, createdAt);

        wipeActiveData(uuid);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            if ("SPIRIT_BROKEN".equalsIgnoreCase(reason) || "AVATAR_DEATH".equalsIgnoreCase(reason)) {
                int banDays = plugin.getConfig().getInt("character.death-ban-days", 1);
                if (banDays > 0) {
                    java.util.Date expires = new java.util.Date(System.currentTimeMillis() + (banDays * 24L * 60 * 60 * 1000));
                    String banReason = "Character death (" + reason + "). You may rejoin in " + banDays + " day(s).";
                    plugin.getServer().getBanList(org.bukkit.BanList.Type.NAME)
                            .addBan(player.getName(), banReason, expires, null);
                    org.bukkit.Bukkit.broadcastMessage(
                            "§4§l☠ §c" + player.getName() + "§7's character has died and they are banned for §e"
                                    + banDays + "§7 day(s).");
                    player.kickPlayer(banReason);
                    return;
                }
            }

            if (plugin.getConfig().getBoolean("character.delete-items-on-deletion", true)) {
                player.getInventory().clear();
                player.getEnderChest().clear();
            }

            
            org.bukkit.configuration.ConfigurationSection spawnCfg =
                    plugin.getConfig().getConfigurationSection("spawn-locations.spawn-realm");
            if (spawnCfg != null) {
                String worldName = spawnCfg.getString("world", "world");
                org.bukkit.World w = Bukkit.getWorld(worldName);
                if (w != null) {
                    player.teleport(new org.bukkit.Location(w,
                            spawnCfg.getDouble("x"), spawnCfg.getDouble("y"), spawnCfg.getDouble("z")));
                }
            }

            
            
            plugin.getSkyFreezeManager().freezePlayer(player);

            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                switch (reason) {
                    case "AVATAR_DEATH" -> {
                        player.sendMessage("§4§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§4§l  ★ THE AVATAR HAS FALLEN ★");
                        player.sendMessage("§4§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("");
                        player.sendMessage("§cYour character §e" + (characterName != null ? characterName : "Unknown")
                                + " §chas died as the Avatar.");
                        player.sendMessage("§7Their story is permanently over.");
                    }
                    case "SPIRIT_BROKEN" -> {
                        player.sendMessage("§8§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§8§l  ✦ BENDING SPIRIT BROKEN ✦");
                        player.sendMessage("§8§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("");
                        player.sendMessage("§7Your character §e" + (characterName != null ? characterName : "Unknown")
                                + " §7has died too many times.");
                        player.sendMessage("§7Their bending spirit could not recover.");
                    }
                    case "DELETED" -> {
                        player.sendMessage("§7§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§7§l  CHARACTER DELETED");
                        player.sendMessage("§7§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("");
                        player.sendMessage("§7Your character §e" + (characterName != null ? characterName : "Unknown")
                                + " §7has been deleted.");
                    }
                    case "ADMIN_RESET" -> {
                        player.sendMessage("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("§c§l  DATA RESET BY ADMIN");
                        player.sendMessage("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage("");
                        player.sendMessage("§7An admin has reset your character data.");
                    }
                    default -> {
                        player.sendMessage("§7Your character has been deactivated.");
                    }
                }

                player.sendMessage("");
                player.sendMessage("§7You are suspended in the sky. Begin your new life:");
                player.sendMessage("");
                player.sendMessage("§a§lSTEP 1 §7— §e/character create <n>");
                player.sendMessage("§a§lSTEP 2 §7— §e/b choose <element>");
                player.sendMessage("§7Elements: §cfire §bwater §aearth §7air §6chi");
                player.sendMessage("");
            }, 60L);
        }
    }

    private void archiveToHistory(UUID uuid, String charName, String username, String element,
                                   int deaths, int kills, long playtime, int xp,
                                   boolean wasAvatar, String reason, long createdAt) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO character_history " +
                "(player_uuid, character_name, player_username, element, death_count, kill_count, " +
                "playtime_seconds, custom_xp, was_avatar, inactive_reason, deactivated_timestamp, creation_timestamp) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, charName != null ? charName : "Unknown");
            stmt.setString(3, username);
            stmt.setString(4, element);
            stmt.setInt(5, deaths);
            stmt.setInt(6, kills);
            stmt.setLong(7, playtime);
            stmt.setInt(8, xp);
            stmt.setBoolean(9, wasAvatar);
            stmt.setString(10, reason);
            stmt.setLong(11, System.currentTimeMillis());
            stmt.setLong(12, createdAt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to archive character: " + e.getMessage());
        }
    }

    private void wipeActiveData(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        String uuidStr = uuid.toString();

        String[] tables = {"characters", "player_stats", "protected_moves", "economy", "death_log"};
        for (String table : tables) {
            try (PreparedStatement s = conn.prepareStatement("DELETE FROM " + table + " WHERE uuid = ?")) {
                s.setString(1, uuidStr);
                s.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to wipe " + table + ": " + e.getMessage());
            }
        }

        try (PreparedStatement s = conn.prepareStatement(
                "DELETE FROM nation_citizens WHERE player_uuid = ?")) {
            s.setString(1, uuidStr);
            s.executeUpdate();
        } catch (SQLException ignored) {}

        try (PreparedStatement s = conn.prepareStatement("DELETE FROM players WHERE uuid = ?")) {
            s.setString(1, uuidStr);
            s.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to wipe players for " + uuid + ": " + e.getMessage());
        }

        plugin.getPlayerDataManager().removeFromCache(uuid);
        plugin.getLuckPermsIntegration().revokeCharacterPermissions(uuid);

        if (plugin.getBendingHook() != null) plugin.getBendingHook().invalidateCache(uuid);

        if (plugin.getProjectKorraIntegration().isScrollsEnabled()) {
            Player onlineForScrolls = Bukkit.getPlayer(uuid);
            if (onlineForScrolls != null) {
                plugin.getScrollManager().resetAllScrollProgress(onlineForScrolls);
            }
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            com.projectkorra.projectkorra.BendingPlayer bPlayer =
                    com.projectkorra.projectkorra.BendingPlayer.getBendingPlayer(online);
            if (bPlayer != null) {
                bPlayer.getAbilities().clear();
                bPlayer.getElements().clear();
                bPlayer.saveElements();
            }
        }
    }

    private long getCreationTimestamp(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT creation_timestamp FROM characters WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getLong("creation_timestamp");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get creation timestamp: " + e.getMessage());
        }
        return 0;
    }

    public boolean canDeleteCharacter(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data != null && !data.isElementPermanent() && data.getElement() != null) return false;
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT deletion_allowed_timestamp FROM characters WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return System.currentTimeMillis() >= rs.getLong("deletion_allowed_timestamp");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check deletion: " + e.getMessage());
        }
        return false;
    }

    
    public void deleteCharacter(UUID uuid) {
        deactivateCharacter(uuid, "DELETED");
    }

    public void resetTutorial(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data != null) data.setTutorialCompleted(false);
    }

    public void setCharacterName(UUID uuid, String name) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE characters SET character_name = ? WHERE uuid = ?")) {
            stmt.setString(1, name);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set character name: " + e.getMessage());
        }
    }

    public void setRestoreCount(UUID uuid, int count) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE characters SET restore_count = ? WHERE uuid = ?")) {
            stmt.setInt(1, count);
            stmt.setString(2, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set restore count: " + e.getMessage());
        }
    }
}
