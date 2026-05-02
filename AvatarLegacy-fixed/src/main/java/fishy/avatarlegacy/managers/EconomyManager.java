package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class EconomyManager {

    private final AvatarLegacy plugin;

    public EconomyManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public void createAccount(UUID uuid) {
        double startingBalance = plugin.getConfig().getDouble("currency.starting-balance", 1000);
        setBalance(uuid, startingBalance);
    }

    public double getBalance(UUID uuid) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("SELECT balance FROM economy WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get balance: " + e.getMessage());
        }
        return 0.0;
    }

    public void setBalance(UUID uuid, double amount) {
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement("INSERT OR REPLACE INTO economy (uuid, balance) VALUES (?, ?)")) {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, amount);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to set balance: " + e.getMessage());
        }
    }

    public boolean withdraw(UUID uuid, double amount) {
        double balance = getBalance(uuid);
        if (balance >= amount) {
            setBalance(uuid, balance - amount);
            logTransaction(uuid, null, amount, "withdraw", "Withdrawal");
            return true;
        }
        return false;
    }

    public void deposit(UUID uuid, double amount) {
        double balance = getBalance(uuid);
        setBalance(uuid, balance + amount);
        logTransaction(null, uuid, amount, "deposit", "Deposit");
    }

    public boolean transfer(UUID from, UUID to, double amount) {
        double balance = getBalance(from);
        if (balance >= amount) {
            setBalance(from, balance - amount);
            setBalance(to, getBalance(to) + amount);
            logTransaction(from, to, amount, "transfer", "Player transfer");
            return true;
        }
        return false;
    }

    private void logTransaction(UUID from, UUID to, double amount, String type, String description) {
        if (!plugin.getConfig().getBoolean("transaction-logging", true)) return;
        Connection conn = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO transaction_log (timestamp, from_uuid, to_uuid, amount, type, description) VALUES (?, ?, ?, ?, ?, ?)")) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, from != null ? from.toString() : null);
            stmt.setString(3, to != null ? to.toString() : null);
            stmt.setDouble(4, amount);
            stmt.setString(5, type);
            stmt.setString(6, description);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log transaction: " + e.getMessage());
        }
    }
}
