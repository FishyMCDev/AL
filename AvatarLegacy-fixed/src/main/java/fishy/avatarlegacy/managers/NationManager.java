package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.Nation;
import fishy.avatarlegacy.models.PlayerData;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NationManager {

    private final AvatarLegacy plugin;

    public NationManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public boolean canCreateNation(UUID uuid) {
        int playtimeMinutes = plugin.getConfig().getInt("nation-creation.costs.playtime-minutes", 30);
        return plugin.getPlaytimeManager().getPlaytimeMinutes(uuid) >= playtimeMinutes;
    }

    public int createNation(String name, UUID leaderUuid, Location coreLocation) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        long now = System.currentTimeMillis();
        long gracePeriodMinutes = plugin.getConfig().getLong("nation-creation.grace-period-minutes", 30);
        long gracePeriodEnd = now + (gracePeriodMinutes * 60L * 1000L);

        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO nations (name, leader_uuid, creation_timestamp, grace_period_end) VALUES (?, ?, ?, ?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, name);
            stmt.setString(2, leaderUuid.toString());
            stmt.setLong(3, now);
            stmt.setLong(4, gracePeriodEnd);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int nationId = rs.getInt(1);
                addCitizen(nationId, leaderUuid, "leader");

                try (PreparedStatement upgradeStmt = conn.prepareStatement(
                        "INSERT INTO nation_upgrades (nation_id) VALUES (?)")) {
                    upgradeStmt.setInt(1, nationId);
                    upgradeStmt.executeUpdate();
                }
                return nationId;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create nation: " + e.getMessage());
        }
        return -1;
    }

    public void setNationCore(int nationId, Location location) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE nations SET core_placed = 1, core_world = ?, core_x = ?, core_y = ?, core_z = ? WHERE id = ?")) {
            stmt.setString(1, location.getWorld().getName());
            stmt.setInt(2, location.getBlockX());
            stmt.setInt(3, location.getBlockY());
            stmt.setInt(4, location.getBlockZ());
            stmt.setInt(5, nationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set nation core: " + e.getMessage());
        }
    }

    public Location getNationCore(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT core_world, core_x, core_y, core_z FROM nations WHERE id = ? AND core_placed = 1")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Location(plugin.getServer().getWorld(rs.getString("core_world")),
                        rs.getInt("core_x"), rs.getInt("core_y"), rs.getInt("core_z"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation core: " + e.getMessage());
        }
        return null;
    }

    public Integer getNationByCore(Location location) {
        if (location == null || location.getWorld() == null) return null;
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM nations WHERE core_placed = 1 AND core_world = ? AND core_x = ? AND core_y = ? AND core_z = ?")) {
            stmt.setString(1, location.getWorld().getName());
            stmt.setInt(2, location.getBlockX());
            stmt.setInt(3, location.getBlockY());
            stmt.setInt(4, location.getBlockZ());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation by core: " + e.getMessage());
        }
        return null;
    }

    public void addCitizen(int nationId, UUID playerUuid, String status) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO nation_citizens (nation_id, player_uuid, join_timestamp, status) VALUES (?, ?, ?, ?)")) {
            stmt.setInt(1, nationId);
            stmt.setString(2, playerUuid.toString());
            stmt.setLong(3, System.currentTimeMillis());
            stmt.setString(4, status);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add citizen: " + e.getMessage());
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(playerUuid);
        if (data != null && data.isRefugee()) {
            data.setRefugee(false);
            plugin.getPlayerDataManager().savePlayerData(data);
        }
    }

    public void removeCitizen(int nationId, UUID playerUuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM nation_citizens WHERE nation_id = ? AND player_uuid = ?")) {
            stmt.setInt(1, nationId);
            stmt.setString(2, playerUuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove citizen: " + e.getMessage());
        }
    }

    public void setKickCooldown(UUID playerUuid) {
        long cooldownMinutes = plugin.getConfig().getLong("citizenship.kick-cooldown-minutes", 5);
        long cooldownEnd = System.currentTimeMillis() + (cooldownMinutes * 60 * 1000);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO kick_cooldowns (player_uuid, cooldown_end) VALUES (?, ?)")) {
            stmt.setString(1, playerUuid.toString());
            stmt.setLong(2, cooldownEnd);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set kick cooldown: " + e.getMessage());
        }
    }

    public boolean isOnKickCooldown(UUID playerUuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT cooldown_end FROM kick_cooldowns WHERE player_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return System.currentTimeMillis() < rs.getLong("cooldown_end");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check kick cooldown: " + e.getMessage());
        }
        return false;
    }

    public Integer getPlayerNation(UUID playerUuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT nation_id FROM nation_citizens WHERE player_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("nation_id");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get player nation: " + e.getMessage());
        }
        return null;
    }

    public List<UUID> getNationCitizens(int nationId) {
        List<UUID> citizens = new ArrayList<>();
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT player_uuid FROM nation_citizens WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) citizens.add(UUID.fromString(rs.getString("player_uuid")));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation citizens: " + e.getMessage());
        }
        return citizens;
    }

    public String getNationName(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT name FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("name");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation name: " + e.getMessage());
        }
        return null;
    }

    public UUID getNationLeader(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT leader_uuid FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return UUID.fromString(rs.getString("leader_uuid"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation leader: " + e.getMessage());
        }
        return null;
    }

    public boolean isNationLeader(UUID playerUuid, int nationId) {
        UUID leader = getNationLeader(nationId);
        return leader != null && leader.equals(playerUuid);
    }

    public boolean hasSpawnSet(int nationId) {
        return getNationSpawn(nationId) != null;
    }

    public boolean hasCorePlaced(int nationId) {
        return getNationCore(nationId) != null;
    }

    public boolean isSetupComplete(int nationId) {
        return hasSpawnSet(nationId) && hasCorePlaced(nationId);
    }

    public void disbandNation(int nationId) {
        plugin.getWorldGuardIntegration().removeNationRegions(nationId);

        List<UUID> citizens = getNationCitizens(nationId);
        for (UUID citizen : citizens) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(citizen);
            if (data != null) {
                data.setRefugee(true);
                plugin.getPlayerDataManager().savePlayerData(data);
            }
        }

        Connection conn = plugin.getDatabaseManager().getConnection();
        for (String table : new String[]{"nation_citizens", "nation_spawn", "nation_upgrades", "nation_core_hits", "nation_shield"}) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + table + " WHERE nation_id = ?")) {
                stmt.setInt(1, nationId);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete from " + table + ": " + e.getMessage());
            }
        }

        try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete nation: " + e.getMessage());
        }
    }

    public double getTreasury(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT treasury FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("treasury");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get treasury: " + e.getMessage());
        }
        return 0.0;
    }

    public void setTreasury(int nationId, double amount) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE nations SET treasury = ? WHERE id = ?")) {
            stmt.setDouble(1, amount);
            stmt.setInt(2, nationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set treasury: " + e.getMessage());
        }
    }

    public void depositTreasury(int nationId, double amount) { setTreasury(nationId, getTreasury(nationId) + amount); }

    public boolean withdrawTreasury(int nationId, double amount) {
        double current = getTreasury(nationId);
        if (current >= amount) { setTreasury(nationId, current - amount); return true; }
        return false;
    }

    public void setTaxRate(int nationId, double rate) {
        rate = Math.min(rate, plugin.getConfig().getDouble("taxes.max-rate-percent", 50));
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE nations SET tax_rate = ? WHERE id = ?")) {
            stmt.setDouble(1, rate);
            stmt.setInt(2, nationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set tax rate: " + e.getMessage());
        }
    }

    public double getTaxRate(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT tax_rate FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("tax_rate");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get tax rate: " + e.getMessage());
        }
        return 0.0;
    }

    public Integer getNationByName(String name) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id FROM nations WHERE name = ?")) {
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("id");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation by name: " + e.getMessage());
        }
        return null;
    }

    public boolean isInGracePeriod(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT grace_period_end FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return System.currentTimeMillis() < rs.getLong("grace_period_end");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check grace period: " + e.getMessage());
        }
        return false;
    }

    public boolean isPlayerNationInGracePeriod(UUID playerUuid) {
        Integer nationId = getPlayerNation(playerUuid);
        return nationId != null && isInGracePeriod(nationId);
    }

    public boolean getAllowMultiElement(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT allow_multi_element FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getBoolean("allow_multi_element");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get multi-element setting: " + e.getMessage());
        }
        return false;
    }

    public void setAllowMultiElement(int nationId, boolean allow) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE nations SET allow_multi_element = ? WHERE id = ?")) {
            stmt.setBoolean(1, allow);
            stmt.setInt(2, nationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set multi-element setting: " + e.getMessage());
        }
    }

    public boolean getAllowRefugees(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT allow_refugees FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getBoolean("allow_refugees");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get refugees setting: " + e.getMessage());
        }
        return true;
    }

    public void setAllowRefugees(int nationId, boolean allow) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE nations SET allow_refugees = ? WHERE id = ?")) {
            stmt.setBoolean(1, allow);
            stmt.setInt(2, nationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set refugees setting: " + e.getMessage());
        }
    }

    public Location getNationSpawn(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT world, x, y, z, yaw, pitch FROM nation_spawn WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Location(plugin.getServer().getWorld(rs.getString("world")),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                        rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation spawn: " + e.getMessage());
        }
        return null;
    }

    public void setNationSpawn(int nationId, Location location) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO nation_spawn (nation_id, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            stmt.setInt(1, nationId);
            stmt.setString(2, location.getWorld().getName());
            stmt.setDouble(3, location.getX());
            stmt.setDouble(4, location.getY());
            stmt.setDouble(5, location.getZ());
            stmt.setFloat(6, location.getYaw());
            stmt.setFloat(7, location.getPitch());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set nation spawn: " + e.getMessage());
        }
    }

    public void applyCitizenStrengthBonus(org.bukkit.entity.Player player) {
        Integer nationId = getPlayerNation(player.getUniqueId());
        if (nationId == null) return;
        int level = getCitizenStrengthLevel(nationId);
        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attr == null) return;
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "nation.strength." + nationId);
        attr.getModifiers().stream()
                .filter(m -> m.getKey().equals(key))
                .forEach(attr::removeModifier);
        if (level > 0) {
            double bonus = level * 4.0;
            org.bukkit.attribute.AttributeModifier mod = new org.bukkit.attribute.AttributeModifier(
                    key, bonus,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
            attr.addModifier(mod);
        }
    }

    public int getCitizenStrengthLevel(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT citizen_strength_level FROM nation_upgrades WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("citizen_strength_level");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get citizen strength level: " + e.getMessage());
        }
        return 0;
    }

    public String getNationElement(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT nation_element FROM nations WHERE id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getString("nation_element");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get nation element: " + e.getMessage());
        }
        return null;
    }

    public boolean canClaimMore(int nationId) {
        int base = plugin.getConfig().getInt("claiming.chunks-per-nation", 5);
        int perUpgrade = plugin.getConfig().getInt("claiming.extra-chunks-per-upgrade", 2);
        int maxTotal = plugin.getConfig().getInt("claiming.max-total-chunks", 15);
        int upgradeLevel = getShieldLevel(nationId);
        int allowed = Math.min(base + (upgradeLevel * perUpgrade), maxTotal);
        return plugin.getWorldGuardIntegration().getClaimCount(nationId) < allowed;
    }

    public int getMaxClaims(int nationId) {
        int base = plugin.getConfig().getInt("claiming.chunks-per-nation", 5);
        int perMember = plugin.getConfig().getInt("claiming.chunks-per-member", 1);
        int perUpgrade = plugin.getConfig().getInt("claiming.extra-chunks-per-upgrade", 2);
        int maxTotal = plugin.getConfig().getInt("claiming.max-total-chunks", 15);
        int upgradeLevel = getShieldLevel(nationId);
        return Math.min(base + (getNationCitizens(nationId).size() * perMember) + (upgradeLevel * perUpgrade), maxTotal);
    }

    public boolean isAllied(int firstNationId, int secondNationId) {
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT 1 FROM nation_alliances WHERE status = 'active' AND ((nation_1_id = ? AND nation_2_id = ?) OR (nation_1_id = ? AND nation_2_id = ?)) LIMIT 1")) {
            stmt.setInt(1, firstNationId); stmt.setInt(2, secondNationId);
            stmt.setInt(3, secondNationId); stmt.setInt(4, firstNationId);
            return stmt.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public void setAllyBuildPermission(int ownerNationId, int allyNationId, boolean allowed) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement statement = conn.prepareStatement(allowed
                ? "INSERT OR IGNORE INTO nation_build_permissions (owner_nation_id, allowed_nation_id) VALUES (?, ?)"
                : "DELETE FROM nation_build_permissions WHERE owner_nation_id = ? AND allowed_nation_id = ?")) {
            statement.setInt(1, ownerNationId); statement.setInt(2, allyNationId); statement.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("Could not save nation build permission: " + e.getMessage()); }
        plugin.getWorldGuardIntegration().updateNationMembers(ownerNationId);
    }

    public List<Integer> getBuildAllowedAllies(int ownerNationId) {
        List<Integer> result = new ArrayList<>();
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT allowed_nation_id FROM nation_build_permissions WHERE owner_nation_id = ?")) {
            stmt.setInt(1, ownerNationId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int ally = rs.getInt(1);
                if (isAllied(ownerNationId, ally)) result.add(ally);
            }
        } catch (SQLException e) { plugin.getLogger().warning("Could not load nation build permissions: " + e.getMessage()); }
        return result;
    }

    public int getShieldLevel(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT core_shield_level FROM nation_upgrades WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("core_shield_level");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get shield level: " + e.getMessage());
        }
        return 0;
    }

    public boolean isShieldActive(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT shield_active_until FROM nation_shield WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return System.currentTimeMillis() < rs.getLong("shield_active_until");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check shield: " + e.getMessage());
        }
        return false;
    }

    public long getShieldRemainingMs(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT shield_active_until FROM nation_shield WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Math.max(0, rs.getLong("shield_active_until") - System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get shield remaining: " + e.getMessage());
        }
        return 0;
    }

    public long getShieldCooldownRemainingMs(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT shield_cooldown_until FROM nation_shield WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return Math.max(0, rs.getLong("shield_cooldown_until") - System.currentTimeMillis());
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get shield cooldown: " + e.getMessage());
        }
        return 0;
    }

    public boolean activateShield(int nationId) {
        if (isShieldActive(nationId)) return false;
        if (getShieldCooldownRemainingMs(nationId) > 0) return false;
        int shieldLevel = getShieldLevel(nationId);
        if (shieldLevel <= 0) return false;

        long durationMs = plugin.getConfig().getLong("core.shield-duration-minutes", 60) * 60 * 1000L;
        long cooldownMs = plugin.getConfig().getLong("core.shield-cooldown-hours", 6) * 3600 * 1000L;
        long now = System.currentTimeMillis();

        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO nation_shield (nation_id, shield_active_until, shield_cooldown_until) VALUES (?, ?, ?)")) {
            stmt.setInt(1, nationId);
            stmt.setLong(2, now + durationMs);
            stmt.setLong(3, now + durationMs + cooldownMs);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to activate shield: " + e.getMessage());
        }
        return false;
    }

    public int getCoreHits(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT hit_count, last_hit_timestamp FROM nation_core_hits WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                long lastHit = rs.getLong("last_hit_timestamp");
                long resetMs = plugin.getConfig().getLong("core.hit-reset-minutes", 10) * 60 * 1000L;
                if (System.currentTimeMillis() - lastHit > resetMs) {
                    resetCoreHits(nationId);
                    return 0;
                }
                return rs.getInt("hit_count");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get core hits: " + e.getMessage());
        }
        return 0;
    }

    public int incrementCoreHits(int nationId) {
        int maxDurability = getCoreDurability(nationId);
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO nation_core_hits (nation_id, hit_count, last_hit_timestamp) VALUES (?, 1, ?) " +
                "ON CONFLICT(nation_id) DO UPDATE SET hit_count = MIN(hit_count + 1, ?), last_hit_timestamp = ?")) {
            stmt.setInt(1, nationId);
            stmt.setLong(2, System.currentTimeMillis());
            stmt.setInt(3, maxDurability);
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to increment core hits: " + e.getMessage());
        }
        return getCoreHits(nationId);
    }

    public int getCoreDurability(int nationId) {
        int base = plugin.getConfig().getInt("core.base-durability", plugin.getConfig().getInt("core.durability", 20));
        int perUpgrade = plugin.getConfig().getInt("core.durability-per-upgrade", 10);
        return Math.max(1, base + (getShieldLevel(nationId) * perUpgrade));
    }

    public void resetCoreHits(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR REPLACE INTO nation_core_hits (nation_id, hit_count, last_hit_timestamp) VALUES (?, 0, 0)")) {
            stmt.setInt(1, nationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to reset core hits: " + e.getMessage());
        }
    }

    public void setNationElement(int nationId, String element) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE nations SET nation_element = ? WHERE id = ?")) {
            stmt.setString(1, element.toLowerCase());
            stmt.setInt(2, nationId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set nation element: " + e.getMessage());
        }
    }
}
