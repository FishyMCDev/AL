package fishy.avatarlegacy.integrations;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WorldGuardIntegration {

    private final AvatarLegacy plugin;
    private boolean enabled;

    public WorldGuardIntegration(AvatarLegacy plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
    }

    public boolean isEnabled() { return enabled; }

    public boolean claimChunk(int nationId, Chunk chunk) {
        if (!enabled) return false;
        try {
            World world = chunk.getWorld();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));
            if (regions == null) return false;

            int cx = chunk.getX();
            int cz = chunk.getZ();
            int minX = cx * 16;
            int minZ = cz * 16;
            int maxX = minX + 15;
            int maxZ = minZ + 15;

            BlockVector3 min = BlockVector3.at(minX, world.getMinHeight(), minZ);
            BlockVector3 max = BlockVector3.at(maxX, world.getMaxHeight(), maxZ);

            String regionId = "nation_" + nationId + "_" + world.getName() + "_" + cx + "_" + cz;

            if (regions.hasRegion(regionId)) return false;

            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, min, max);
            region.setFlag(Flags.BUILD, StateFlag.State.DENY);
            region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
            region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.DENY);

            com.sk89q.worldguard.protection.managers.storage.StorageException ignored1 = null;
            try {
                for (java.util.UUID citizenUuid : plugin.getNationManager().getNationCitizens(nationId)) {
                    org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(citizenUuid);
                    region.getMembers().addPlayer(op.getUniqueId());
                }
            } catch (Exception ignored) {}

            regions.addRegion(region);
            try { regions.save(); } catch (Exception e2) {
                plugin.getLogger().warning("Failed to save WG regions: " + e2.getMessage());
            }

            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT OR IGNORE INTO nation_claims (nation_id, world, chunk_x, chunk_z, region_id) VALUES (?, ?, ?, ?, ?)")) {
                stmt.setInt(1, nationId);
                stmt.setString(2, world.getName());
                stmt.setInt(3, cx);
                stmt.setInt(4, cz);
                stmt.setString(5, regionId);
                stmt.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to claim chunk for nation " + nationId + ": " + e.getMessage());
            return false;
        }
    }

    public boolean unclaimChunk(int nationId, Chunk chunk) {
        if (!enabled) return false;
        try {
            World world = chunk.getWorld();
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(world));
            if (regions == null) return false;

            String regionId = "nation_" + nationId + "_" + world.getName() + "_" + chunk.getX() + "_" + chunk.getZ();
            regions.removeRegion(regionId);
            try { regions.save(); } catch (Exception e2) {
                plugin.getLogger().warning("Failed to save WG regions: " + e2.getMessage());
            }

            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM nation_claims WHERE nation_id = ? AND world = ? AND chunk_x = ? AND chunk_z = ?")) {
                stmt.setInt(1, nationId);
                stmt.setString(2, world.getName());
                stmt.setInt(3, chunk.getX());
                stmt.setInt(4, chunk.getZ());
                stmt.executeUpdate();
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to unclaim chunk: " + e.getMessage());
            return false;
        }
    }

    public int getClaimCount(int nationId) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM nation_claims WHERE nation_id = ?")) {
            stmt.setInt(1, nationId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get claim count: " + e.getMessage());
        }
        return 0;
    }

    public boolean isChunkClaimed(Chunk chunk) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT nation_id FROM nation_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check chunk claim: " + e.getMessage());
        }
        return false;
    }

    public Integer getChunkOwner(Chunk chunk) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT nation_id FROM nation_claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
            stmt.setString(1, chunk.getWorld().getName());
            stmt.setInt(2, chunk.getX());
            stmt.setInt(3, chunk.getZ());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("nation_id");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get chunk owner: " + e.getMessage());
        }
        return null;
    }

    public void updateNationMembers(int nationId) {
        if (!enabled) return;
        try {
            java.util.List<java.util.UUID> citizens = plugin.getNationManager().getNationCitizens(nationId);
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            for (World world : plugin.getServer().getWorlds()) {
                RegionManager regions = container.get(BukkitAdapter.adapt(world));
                if (regions == null) continue;
                boolean changed = false;
                for (ProtectedRegion region : regions.getRegions().values()) {
                    if (!region.getId().startsWith("nation_" + nationId + "_")) continue;
                    region.getMembers().clear();
                    for (java.util.UUID uuid : citizens) {
                        region.getMembers().addPlayer(uuid);
                    }
                    changed = true;
                }
                if (changed) {
                    try { regions.save(); } catch (Exception e2) {
                        plugin.getLogger().warning("Failed to save WG regions: " + e2.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to update nation members: " + e.getMessage());
        }
    }

    public void removeNationRegions(int nationId) {
        if (!enabled) return;
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            for (World world : plugin.getServer().getWorlds()) {
                RegionManager regions = container.get(BukkitAdapter.adapt(world));
                if (regions == null) continue;
                List<String> toRemove = new ArrayList<>(regions.getRegions().keySet().stream()
                        .filter(key -> key.startsWith("nation_" + nationId + "_"))
                        .toList());
                for (String key : toRemove) regions.removeRegion(key);
                if (!toRemove.isEmpty()) {
                    try { regions.save(); } catch (Exception e2) {
                        plugin.getLogger().warning("Failed to save WG regions: " + e2.getMessage());
                    }
                }
            }

            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM nation_claims WHERE nation_id = ?")) {
                stmt.setInt(1, nationId);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to remove WorldGuard regions for nation " + nationId + ": " + e.getMessage());
        }
    }
}
