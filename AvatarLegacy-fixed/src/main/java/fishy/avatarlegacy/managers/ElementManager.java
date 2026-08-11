package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ElementManager {

    private final AvatarLegacy plugin;
    private final java.util.Map<java.util.UUID, Long> playtimeAtSelectionCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ElementManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public void cachePlaytimeAtSelection(java.util.UUID uuid, long playtime) {
        playtimeAtSelectionCache.put(uuid, playtime);
    }

    public void evictPlaytimeCache(java.util.UUID uuid) {
        playtimeAtSelectionCache.remove(uuid);
    }

    public void setElement(Player player, String element) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String oldElement = data.getElement();
        long currentPlaytime = data.getPlaytimeSeconds();
        boolean isFirstChoice = (oldElement == null);

        data.setElement(element.toLowerCase());
        data.setElementPermanent(false);

        if (isFirstChoice) {
            data.setElementSelectionTimestamp(System.currentTimeMillis());
        }

        plugin.getProjectKorraIntegration().setPlayerElement(player, element);
        clearBendingSlots(player);

        if (oldElement != null && !oldElement.equalsIgnoreCase(element)) {
            
            plugin.getLuckPermsIntegration().revokeProtectedMovePermissions(player, oldElement);

            
            
            java.util.List<String> previouslyRemoved = plugin.getStatsManager().getRemovedMoves(player.getUniqueId());
            for (String m : previouslyRemoved) {
                plugin.getLuckPermsIntegration().grantPermission(player.getUniqueId(), "bending.ability." + m.toLowerCase());
            }
            
            
            
            
            if ("chi".equalsIgnoreCase(element)) {
                plugin.getStatsManager().savePreChiState(player.getUniqueId());
                plugin.getStatsManager().clearRemovedMovesOnly(player.getUniqueId());
                plugin.getStatsManager().restoreBendingStrength(player.getUniqueId());
                plugin.getLogger().info("[AvatarLegacy] " + player.getName()
                        + " switched to Chi — debuff state saved, immunity active.");
                player.sendMessage("§e§lYou are now a Chi Blocker. §7Your spirit is unbreakable — "
                        + "bending debuffs do not affect you.");
            } else {
                
                
                
                
                plugin.getStatsManager().clearRemovedMovesOnly(player.getUniqueId());
                if ("chi".equalsIgnoreCase(oldElement)) {
                    boolean restored = plugin.getStatsManager().restorePreChiState(player.getUniqueId());
                    if (restored) {
                        player.sendMessage("§c§lYour previous bending debuffs have been restored.");
                        player.sendMessage("§7Your spirit remembers the damage it carried before you trained as a Chi Blocker.");
                    }
                }
            }
        }

        plugin.getLuckPermsIntegration().grantProtectedMovePermissions(player, element);
        plugin.getProtectedMovesManager().setProtectedMoves(player.getUniqueId(), element);
        plugin.getProjectKorraIntegration().grantDefaultMoves(player, element);

        
        
        if (isFirstChoice && "chi".equalsIgnoreCase(element)) {
            player.sendMessage("\u00a7e\u00a7lYou are a Chi Blocker. \u00a77Your spirit is unbreakable \u2014 "
                    + "bending debuffs do not affect you.");
        }

        if (isFirstChoice) {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE players SET element_selection_playtime = ? WHERE uuid = ?")) {
                stmt.setLong(1, currentPlaytime);
                stmt.setString(2, player.getUniqueId().toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to store element selection playtime: " + e.getMessage());
            }
            cachePlaytimeAtSelection(player.getUniqueId(), currentPlaytime);
        }

        teleportToRuins(player, element);
    }

    public void checkAndMakePermanent(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null || data.isElementPermanent() || data.getElement() == null) return;
        if (data.getElementSelectionTimestamp() == 0) return;

        long timerHours = plugin.getConfig().getLong("element-selection.timer-hours", 2);
        long requiredSeconds = timerHours * 3600;
        long currentPlaytime = data.getPlaytimeSeconds();

        java.util.UUID uuid = player.getUniqueId();
        long playtimeAtSelection = playtimeAtSelectionCache.computeIfAbsent(uuid, k -> {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT element_selection_playtime FROM players WHERE uuid = ?")) {
                stmt.setString(1, k.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return rs.getLong("element_selection_playtime");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get playtime at selection: " + e.getMessage());
            }
            return 0L;
        });

        if (currentPlaytime - playtimeAtSelection >= requiredSeconds) {
            data.setElementPermanent(true);
            plugin.getPlayerDataManager().savePlayerData(data);

            plugin.getLuckPermsIntegration().revokePermission(player.getUniqueId(), "bending.command.rechoose");
            plugin.getProtectedMovesManager().setProtectedMoves(player.getUniqueId(), data.getElement());
            plugin.getLuckPermsIntegration().grantProtectedMovePermissions(player, data.getElement());

            player.sendMessage(MessageUtil.success("Your element is now \u00a7cPERMANENT\u00a7a!"));
            player.sendMessage(MessageUtil.info("You are forever a \u00a7e" + data.getElement().toUpperCase() + "\u00a77 bender."));
        }
    }

    public long getTimeUntilPermanent(UUID uuid) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);
        if (data == null || data.isElementPermanent() || data.getElement() == null) return 0;

        long timerHours = plugin.getConfig().getLong("element-selection.timer-hours", 2);
        long requiredSeconds = timerHours * 3600;

        long playtimeAtSelection = playtimeAtSelectionCache.computeIfAbsent(uuid, k -> {
            Connection conn = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT element_selection_playtime FROM players WHERE uuid = ?")) {
                stmt.setString(1, k.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return rs.getLong("element_selection_playtime");
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get playtime at selection: " + e.getMessage());
            }
            return 0L;
        });

        long playtimeSinceSelection = data.getPlaytimeSeconds() - playtimeAtSelection;
        return Math.max(0, requiredSeconds - playtimeSinceSelection);
    }

    private void teleportToRuins(Player player, String element) {
        if (element.equalsIgnoreCase("chi")) return; 
        Location stored = getStoredSpawn(element);
        if (stored != null) { player.teleport(stored); return; }
        String path = "spawn-locations." + element.toLowerCase() + "-ruins";
        ConfigurationSection config = plugin.getConfig().getConfigurationSection(path);
        if (config == null) return;
        String worldName = config.getString("world");
        if (worldName == null) return;
        org.bukkit.World w = Bukkit.getWorld(worldName);
        if (w == null) return;
        Location loc = new Location(w, config.getDouble("x"), config.getDouble("y"), config.getDouble("z"));
        player.teleport(loc);
    }

    public void setSpawn(String element, int slot, Location location) {
        if (slot < 1 || slot > 3 || location == null || location.getWorld() == null) throw new IllegalArgumentException("slot must be 1-3");
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT INTO element_spawns (element, slot, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(element, slot) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z, yaw=excluded.yaw, pitch=excluded.pitch")) {
            stmt.setString(1, element.toLowerCase()); stmt.setInt(2, slot); stmt.setString(3, location.getWorld().getName());
            stmt.setDouble(4, location.getX()); stmt.setDouble(5, location.getY()); stmt.setDouble(6, location.getZ());
            stmt.setFloat(7, location.getYaw()); stmt.setFloat(8, location.getPitch()); stmt.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("Could not save element spawn", e); }
    }

    private Location getStoredSpawn(String element) {
        try (PreparedStatement stmt = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT world, x, y, z, yaw, pitch FROM element_spawns WHERE element = ? ORDER BY slot")) {
            stmt.setString(1, element.toLowerCase());
            ResultSet rs = stmt.executeQuery();
            java.util.List<Location> spawns = new java.util.ArrayList<>();
            while (rs.next()) {
                org.bukkit.World world = Bukkit.getWorld(rs.getString("world"));
                if (world != null) spawns.add(new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
            }
            return spawns.isEmpty() ? null : spawns.get(ThreadLocalRandom.current().nextInt(spawns.size()));
        } catch (SQLException e) { plugin.getLogger().warning("Could not load element spawns: " + e.getMessage()); return null; }
    }

    public void changeElementViaChanger(Player player, String newElement) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String oldElement = data.getElement();
        if (oldElement != null) {
            plugin.getLuckPermsIntegration().revokeProtectedMovePermissions(player, oldElement);
            plugin.getProtectedMovesManager().clearProtectedMoves(player.getUniqueId());
            
            java.util.List<String> previouslyRemoved = plugin.getStatsManager().getRemovedMoves(player.getUniqueId());
            for (String m : previouslyRemoved) {
                plugin.getLuckPermsIntegration().grantPermission(player.getUniqueId(), "bending.ability." + m.toLowerCase());
            }
            
            if ("chi".equalsIgnoreCase(newElement)) {
                plugin.getStatsManager().savePreChiState(player.getUniqueId());
                plugin.getStatsManager().clearRemovedMovesOnly(player.getUniqueId());
                plugin.getStatsManager().restoreBendingStrength(player.getUniqueId());
                player.sendMessage("§e§lYou are now a Chi Blocker. §7Your spirit is unbreakable — "
                        + "bending debuffs do not affect you.");
            } else {
                plugin.getStatsManager().clearRemovedMovesOnly(player.getUniqueId());
                if ("chi".equalsIgnoreCase(oldElement)) {
                    boolean restored = plugin.getStatsManager().restorePreChiState(player.getUniqueId());
                    if (restored) {
                        player.sendMessage("§c§lYour previous bending debuffs have been restored.");
                        player.sendMessage("§7Your spirit remembers the damage it carried before you trained as a Chi Blocker.");
                    }
                }
            }
        }

        data.setElement(newElement.toLowerCase());
        data.setElementPermanent(true);
        data.setElementSelectionTimestamp(0);

        
        
        boolean isAvatar = plugin.getAvatarManager().isAvatar(player.getUniqueId());
        if (isAvatar) {
            plugin.getProjectKorraIntegration().addAllElements(player);
        } else {
            plugin.getProjectKorraIntegration().setPlayerElement(player, newElement);
        }
        clearBendingSlots(player);
        plugin.getLuckPermsIntegration().grantProtectedMovePermissions(player, newElement);
        plugin.getProtectedMovesManager().setProtectedMoves(player.getUniqueId(), newElement);
        
        if (isAvatar) {
            for (String el : new String[]{"fire", "water", "earth", "air"}) {
                plugin.getLuckPermsIntegration().grantProtectedMovePermissions(player, el, player.getUniqueId());
                plugin.getProtectedMovesManager().addProtectedMovesForElement(player.getUniqueId(), el);
            }
        }
        plugin.getProjectKorraIntegration().grantDefaultMoves(player, newElement);
        plugin.getLuckPermsIntegration().revokePermission(player.getUniqueId(), "bending.command.rechoose");
        plugin.getPlayerDataManager().savePlayerData(data);

        if (newElement.equalsIgnoreCase("chi")) {
            int radius = plugin.getConfig().getInt("chi.spawn-radius", 1000);
            String worldName = plugin.getConfig().getString("chi.spawn-world", "world");
            org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
            if (world != null) {
                java.util.Random rand = new java.util.Random();
                org.bukkit.Location loc = findSafeChiSpawn(world, radius, rand);
                if (loc != null) {
                    player.teleport(loc);
                    player.setBedSpawnLocation(loc, true);
                }
            }
        } else {
            teleportToRuins(player, newElement);
        }

        plugin.getCharacterManager().cacheCharacterName(player.getUniqueId(),
                plugin.getCharacterManager().getCharacterName(player.getUniqueId()));

        player.sendMessage(fishy.avatarlegacy.utils.MessageUtil.success(
                "Your element has been changed to \u00a7e" + newElement.toUpperCase() + "\u00a7a via an Element Changer!"));
    }

    public void changeElement(Player player, String newElement) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) return;

        String oldElement = data.getElement();
        if (oldElement != null) {
            plugin.getLuckPermsIntegration().revokeProtectedMovePermissions(player, oldElement);
        }

        data.setElement(newElement.toLowerCase());
        data.setElementPermanent(true);

        plugin.getProjectKorraIntegration().setPlayerElement(player, newElement);
        clearBendingSlots(player);
        plugin.getLuckPermsIntegration().grantProtectedMovePermissions(player, newElement);
        plugin.getProtectedMovesManager().setProtectedMoves(player.getUniqueId(), newElement);
        plugin.getProjectKorraIntegration().grantDefaultMoves(player, newElement);
        plugin.getPlayerDataManager().savePlayerData(data);

        player.sendMessage(MessageUtil.success("Your element has been changed to " + newElement.toUpperCase() + "!"));
    }

    public void clearBendingSlots(Player player) {
        com.projectkorra.projectkorra.BendingPlayer bPlayer =
                com.projectkorra.projectkorra.BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        bPlayer.getAbilities().clear();
    }

    
    private org.bukkit.Location findSafeChiSpawn(org.bukkit.World world, int radius, java.util.Random rand) {
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = rand.nextInt(radius * 2 + 1) - radius;
            int z = rand.nextInt(radius * 2 + 1) - radius;
            int highY = world.getHighestBlockYAt(x, z);
            org.bukkit.block.Block surface = world.getBlockAt(x, highY, z);
            org.bukkit.Material surfaceType = surface.getType();
            if (isDangerousOrLiquid(surfaceType)) continue;
            if (!surfaceType.isSolid()) continue;
            org.bukkit.Material above1 = world.getBlockAt(x, highY + 1, z).getType();
            org.bukkit.Material above2 = world.getBlockAt(x, highY + 2, z).getType();
            if (!above1.isAir() && above1 != org.bukkit.Material.CAVE_AIR) continue;
            if (!above2.isAir() && above2 != org.bukkit.Material.CAVE_AIR) continue;
            return new org.bukkit.Location(world, x + 0.5, highY + 1, z + 0.5);
        }
        return null;
    }

    private boolean isDangerousOrLiquid(org.bukkit.Material mat) {
        if (mat == null) return true;
        String n = mat.name();
        return n.contains("WATER") || n.contains("LAVA")
                || mat == org.bukkit.Material.FIRE
                || mat == org.bukkit.Material.CAMPFIRE
                || mat == org.bukkit.Material.SOUL_CAMPFIRE
                || mat == org.bukkit.Material.MAGMA_BLOCK
                || mat == org.bukkit.Material.WITHER_ROSE
                || mat == org.bukkit.Material.CACTUS
                || mat == org.bukkit.Material.SWEET_BERRY_BUSH
                || mat == org.bukkit.Material.VOID_AIR;
    }
}
