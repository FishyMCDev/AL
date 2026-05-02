package fishy.avatarlegacy.database;

import fishy.avatarlegacy.AvatarLegacy;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseManager {

    private final AvatarLegacy plugin;
    private Connection connection;

    public DatabaseManager(AvatarLegacy plugin) {
        this.plugin = plugin;
        initializeConnection();
    }

    private void initializeConnection() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) dataFolder.mkdirs();

            String path = plugin.getConfig().getString("database.path", "plugins/AvatarLegacy/avatarlegacy.db");
            File dbFile = new File(path);
            if (!dbFile.getParentFile().exists()) dbFile.getParentFile().mkdirs();

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            plugin.getLogger().info("Database connection established.");
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().severe("Failed to initialize database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void initializeTables() {
        
        createPlayersTable();
        createProtectedMovesTable();
        createOfflineNotificationsTable();
        createServerConfigTable();
        createEconomyTable();
        createShopItemsTable();
        createTransactionLogTable();
        createCharactersTable();
        createPlayerStatsTable();
        createDeathLogTable();
        createNationsTable();
        createNationCitizensTable();
        createNationWarsTable();
        createNationAlliancesTable();
        createNationTrucesTable();
        createNationUpgradesTable();
        createNationSpawnTable();
        createKickCooldownsTable();
        createAvatarCycleTable();
        createAvatarCandidatesTable();
        createAvatarHistoryTable();
        createNationClaimsTable();
        createNationCoreHitsTable();
        createNationShieldTable();

        migrateNationsTable();
        migrateCharactersTable();
        migratePlayerStatsTable();
        migrateNationUpgradesTable();
        migratePlayersTable();
        createCharacterHistoryTable();
    }

    private void createPlayersTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS players (" +
                "uuid TEXT PRIMARY KEY," +
                "username TEXT," +
                "tutorial_completed BOOLEAN DEFAULT 0," +
                "element TEXT," +
                "element_selection_timestamp INTEGER," +
                "element_selection_playtime INTEGER DEFAULT 0," +
                "element_permanent BOOLEAN DEFAULT 0," +
                "playtime_seconds INTEGER DEFAULT 0," +
                "custom_xp INTEGER DEFAULT 0," +
                "last_login INTEGER," +
                "visited_fire_territory BOOLEAN DEFAULT 0," +
                "visited_water_territory BOOLEAN DEFAULT 0," +
                "visited_earth_territory BOOLEAN DEFAULT 0," +
                "visited_air_territory BOOLEAN DEFAULT 0," +
                "last_activity_time INTEGER," +
                "bed_spawn_world TEXT," +
                "bed_spawn_x REAL," +
                "bed_spawn_y REAL," +
                "bed_spawn_z REAL," +
                "is_refugee BOOLEAN DEFAULT 0," +
                "has_ever_chosen BOOLEAN DEFAULT 0" +
                ");");
    }

    private void createProtectedMovesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS protected_moves (" +
                "uuid TEXT," +
                "move_name TEXT," +
                "PRIMARY KEY (uuid, move_name)," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)" +
                ");");
    }

    private void createOfflineNotificationsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS offline_notifications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT," +
                "message TEXT," +
                "timestamp INTEGER," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)" +
                ");");
    }

    private void createServerConfigTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS server_config (key TEXT PRIMARY KEY, value TEXT);");
    }

    private void createEconomyTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS economy (" +
                "uuid TEXT PRIMARY KEY," +
                "balance REAL DEFAULT 0," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)" +
                ");");
    }

    private void createShopItemsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS shop_items (" +
                "item_id TEXT PRIMARY KEY," +
                "material TEXT," +
                "buy_price REAL," +
                "sell_price REAL," +
                "sellable BOOLEAN DEFAULT 0" +
                ");");
    }

    private void createTransactionLogTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS transaction_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "timestamp INTEGER," +
                "from_uuid TEXT," +
                "to_uuid TEXT," +
                "amount REAL," +
                "type TEXT," +
                "description TEXT" +
                ");");
    }

    private void createCharactersTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS characters (" +
                "uuid TEXT PRIMARY KEY," +
                "creation_timestamp INTEGER," +
                "deletion_allowed_timestamp INTEGER," +
                "character_name TEXT DEFAULT 'Unknown'," +
                "restore_count INTEGER DEFAULT 0," +
                "last_restore_timestamp INTEGER DEFAULT 0," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)" +
                ");");
    }

    private void createPlayerStatsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS player_stats (" +
                "uuid TEXT PRIMARY KEY," +
                "death_count INTEGER DEFAULT 0," +
                "bending_strength_percent INTEGER DEFAULT 100," +
                "moves_removed INTEGER DEFAULT 0," +
                "removed_moves TEXT DEFAULT '[]'," +
                "kill_count INTEGER DEFAULT 0," +
                "pk_damage_dealt REAL DEFAULT 0," +
                "blocks_broken INTEGER DEFAULT 0," +
                "blocks_placed INTEGER DEFAULT 0," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)" +
                ");");
    }

    private void createDeathLogTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS death_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "uuid TEXT," +
                "timestamp INTEGER," +
                "cause TEXT," +
                "location TEXT" +
                ");");
    }

    private void createNationsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT UNIQUE," +
                "leader_uuid TEXT," +
                "creation_timestamp INTEGER," +
                "core_placed BOOLEAN DEFAULT 0," +
                "core_world TEXT," +
                "core_x INTEGER," +
                "core_y INTEGER," +
                "core_z INTEGER," +
                "treasury REAL DEFAULT 0," +
                "tax_rate REAL DEFAULT 0," +
                "allow_multi_element BOOLEAN DEFAULT 0," +
                "allow_refugees BOOLEAN DEFAULT 1," +
                "grace_period_end INTEGER," +
                "nation_element TEXT DEFAULT NULL" +
                ");");
    }

    private void createNationCitizensTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_citizens (" +
                "nation_id INTEGER," +
                "player_uuid TEXT," +
                "join_timestamp INTEGER," +
                "status TEXT DEFAULT 'citizen'," +
                "PRIMARY KEY (nation_id, player_uuid)," +
                "FOREIGN KEY (nation_id) REFERENCES nations(id)," +
                "FOREIGN KEY (player_uuid) REFERENCES players(uuid)" +
                ");");
    }

    private void createNationWarsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_wars (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "attacker_nation_id INTEGER," +
                "defender_nation_id INTEGER," +
                "start_timestamp INTEGER," +
                "end_timestamp INTEGER," +
                "status TEXT DEFAULT 'active'," +
                "FOREIGN KEY (attacker_nation_id) REFERENCES nations(id)," +
                "FOREIGN KEY (defender_nation_id) REFERENCES nations(id)" +
                ");");
    }

    private void createNationAlliancesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_alliances (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nation_1_id INTEGER," +
                "nation_2_id INTEGER," +
                "proposed_by_nation_id INTEGER," +
                "status TEXT DEFAULT 'pending'," +
                "proposal_timestamp INTEGER," +
                "FOREIGN KEY (nation_1_id) REFERENCES nations(id)," +
                "FOREIGN KEY (nation_2_id) REFERENCES nations(id)" +
                ");");
    }

    private void createNationTrucesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_truces (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "war_id INTEGER," +
                "proposed_by_nation_id INTEGER," +
                "proposal_timestamp INTEGER," +
                "expiration_timestamp INTEGER," +
                "status TEXT DEFAULT 'pending'," +
                "FOREIGN KEY (war_id) REFERENCES nation_wars(id)" +
                ");");
    }

    private void createNationUpgradesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_upgrades (" +
                "nation_id INTEGER PRIMARY KEY," +
                "resource_drop_rate_level INTEGER DEFAULT 0," +
                "citizen_strength_level INTEGER DEFAULT 0," +
                "core_shield_level INTEGER DEFAULT 0," +
                "FOREIGN KEY (nation_id) REFERENCES nations(id)" +
                ");");
    }

    private void createNationSpawnTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_spawn (" +
                "nation_id INTEGER PRIMARY KEY," +
                "world TEXT," +
                "x REAL," +
                "y REAL," +
                "z REAL," +
                "yaw REAL," +
                "pitch REAL," +
                "FOREIGN KEY (nation_id) REFERENCES nations(id)" +
                ");");
    }

    private void createKickCooldownsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS kick_cooldowns (" +
                "player_uuid TEXT PRIMARY KEY," +
                "cooldown_end INTEGER" +
                ");");
    }

    private void createAvatarCycleTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS avatar_cycle (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "current_avatar_uuid TEXT," +
                "current_cycle_element TEXT," +
                "cycle_start_timestamp INTEGER," +
                "reincarnation_active BOOLEAN DEFAULT 0," +
                "reincarnation_start_timestamp INTEGER," +
                "reincarnation_end_timestamp INTEGER" +
                ");");
    }

    private void createAvatarCandidatesTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS avatar_candidates (" +
                "uuid TEXT PRIMARY KEY," +
                "interconnection_score INTEGER DEFAULT 0," +
                "experience_score INTEGER DEFAULT 0," +
                "playtime_score INTEGER DEFAULT 0," +
                "average_score REAL DEFAULT 0," +
                "eligible BOOLEAN DEFAULT 1," +
                "FOREIGN KEY (uuid) REFERENCES players(uuid)" +
                ");");
    }

    private void createAvatarHistoryTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS avatar_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "avatar_uuid TEXT," +
                "element TEXT," +
                "start_timestamp INTEGER," +
                "end_timestamp INTEGER," +
                "death_cause TEXT" +
                ");");
    }

    public void executeUpdate(String sql) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to execute update: " + sql);
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                initializeConnection();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Connection check failed: " + e.getMessage());
        }
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close database connection: " + e.getMessage());
        }
    }

    private void migratePlayerStatsTable() {
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE player_stats ADD COLUMN kill_count INTEGER DEFAULT 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
        
        
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE player_stats ADD COLUMN pre_chi_bending_strength INTEGER DEFAULT -1")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
        
        
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE player_stats ADD COLUMN pre_chi_removed_moves TEXT DEFAULT NULL")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}

        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE player_stats ADD COLUMN pk_damage_dealt REAL DEFAULT 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}

        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE player_stats ADD COLUMN blocks_broken INTEGER DEFAULT 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}

        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE player_stats ADD COLUMN blocks_placed INTEGER DEFAULT 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
    }

    private void migrateNationsTable() {
        
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(
                "ALTER TABLE nations ADD COLUMN nation_element TEXT DEFAULT NULL")) {
            stmt.executeUpdate();
        } catch (java.sql.SQLException ignored) {}

        
        try (java.sql.PreparedStatement stmt = connection.prepareStatement(
                "ALTER TABLE player_stats ADD COLUMN kill_count INTEGER DEFAULT 0")) {
            stmt.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
    }

    private void createNationClaimsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_claims (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "nation_id INTEGER NOT NULL," +
                "world TEXT NOT NULL," +
                "chunk_x INTEGER NOT NULL," +
                "chunk_z INTEGER NOT NULL," +
                "region_id TEXT NOT NULL," +
                "UNIQUE(world, chunk_x, chunk_z)," +
                "FOREIGN KEY (nation_id) REFERENCES nations(id)" +
                ");");
    }

    private void createNationCoreHitsTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_core_hits (" +
                "nation_id INTEGER PRIMARY KEY," +
                "hit_count INTEGER DEFAULT 0," +
                "last_hit_timestamp INTEGER DEFAULT 0," +
                "FOREIGN KEY (nation_id) REFERENCES nations(id)" +
                ");");
    }

    private void createNationShieldTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS nation_shield (" +
                "nation_id INTEGER PRIMARY KEY," +
                "shield_active_until INTEGER DEFAULT 0," +
                "shield_cooldown_until INTEGER DEFAULT 0," +
                "FOREIGN KEY (nation_id) REFERENCES nations(id)" +
                ");");
    }

    private void migrateNationUpgradesTable() {
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE nation_upgrades ADD COLUMN core_shield_level INTEGER DEFAULT 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE nation_upgrades RENAME COLUMN core_protection_level TO core_shield_level")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
    }

    private void migratePlayersTable() {
        
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE players ADD COLUMN has_ever_chosen BOOLEAN DEFAULT 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}

        
        
        
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "UPDATE players SET has_ever_chosen = 1 WHERE element IS NOT NULL AND has_ever_chosen = 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
    }

    private void createCharacterHistoryTable() {
        executeUpdate("CREATE TABLE IF NOT EXISTS character_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "character_name TEXT NOT NULL," +
                "player_username TEXT," +
                "element TEXT," +
                "death_count INTEGER DEFAULT 0," +
                "kill_count INTEGER DEFAULT 0," +
                "playtime_seconds INTEGER DEFAULT 0," +
                "custom_xp INTEGER DEFAULT 0," +
                "was_avatar BOOLEAN DEFAULT 0," +
                "inactive_reason TEXT," +
                "deactivated_timestamp INTEGER," +
                "creation_timestamp INTEGER" +
                ");");
    }

    private void migrateCharactersTable() {
        
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE characters ADD COLUMN character_name TEXT DEFAULT 'Unknown'")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
        
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE characters ADD COLUMN restore_count INTEGER DEFAULT 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
        
        try (java.sql.PreparedStatement s = connection.prepareStatement(
                "ALTER TABLE characters ADD COLUMN last_restore_timestamp INTEGER DEFAULT 0")) {
            s.executeUpdate();
        } catch (java.sql.SQLException ignored) {}
    }
}