package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProtectedMovesManager {

    private final AvatarLegacy plugin;

    public ProtectedMovesManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public void setProtectedMoves(UUID uuid, String element) {
        clearProtectedMoves(uuid);

        List<String> moves = plugin.getConfig().getStringList("protected-default-moves." + element.toLowerCase());

        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "INSERT INTO protected_moves (uuid, move_name) VALUES (?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (String move : moves) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, move);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set protected moves: " + e.getMessage());
        }
    }

    public List<String> getProtectedMoves(UUID uuid) {
        List<String> moves = new ArrayList<>();
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "SELECT move_name FROM protected_moves WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                moves.add(rs.getString("move_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get protected moves: " + e.getMessage());
        }

        return moves;
    }

    public void clearProtectedMoves(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        String sql = "DELETE FROM protected_moves WHERE uuid = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear protected moves: " + e.getMessage());
        }
    }

    public boolean isProtectedMove(UUID uuid, String moveName) {
        return getProtectedMoves(uuid).stream().anyMatch(m -> m.equalsIgnoreCase(moveName));
    }

    
    public void addProtectedMovesForElement(UUID uuid, String element) {
        List<String> moves = plugin.getConfig().getStringList("protected-default-moves." + element.toLowerCase());
        if (moves.isEmpty()) return;

        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO protected_moves (uuid, move_name) VALUES (?, ?)")) {
            for (String move : moves) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, move);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add protected moves for element " + element + ": " + e.getMessage());
        }
    }

    public void addProtectedMove(UUID uuid, String moveName) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO protected_moves (uuid, move_name) VALUES (?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, moveName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add protected move " + moveName + ": " + e.getMessage());
        }
    }
}