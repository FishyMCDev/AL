package fishy.avatarlegacy.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

public class MessageUtil {

    private static String currencySymbol = "¥";
    private static String currencyName   = "Yen";

    
    public static void init(org.bukkit.configuration.file.FileConfiguration config) {
        currencySymbol = config.getString("currency.symbol", "¥");
        currencyName   = config.getString("currency.name",   "Yen");
    }

    public static String getCurrencyName() { return currencyName; }

    public static Component colorize(String message) {
        return Component.text(message.replace("&", "§"));
    }

    public static Component success(String message) {
        return Component.text(message).color(NamedTextColor.GREEN);
    }

    public static Component error(String message) {
        return Component.text(message).color(NamedTextColor.RED);
    }

    public static Component warning(String message) {
        return Component.text(message).color(NamedTextColor.YELLOW);
    }

    public static Component info(String message) {
        return Component.text(message).color(NamedTextColor.AQUA);
    }

    public static Component prefix(String prefix, String message) {
        return Component.text("[" + prefix + "] ").color(NamedTextColor.GOLD)
                .append(Component.text(message).color(NamedTextColor.WHITE));
    }

    public static Component avatarPrefix(String message) {
        return prefix("AvatarLegacy", message);
    }

    public static String formatYen(double amount) {
        return currencySymbol + String.format("%.2f", amount);
    }

    public static String formatXP(int amount) {
        return amount + " XP";
    }

    public static String formatPlaytime(long seconds) {
        long hours   = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs    = seconds % 60;
        return String.format("%dh %dm %ds", hours, minutes, secs);
    }

    public static Component elementColor(String element, String text) {
        NamedTextColor color = switch (element.toLowerCase()) {
            case "fire"  -> NamedTextColor.RED;
            case "water" -> NamedTextColor.BLUE;
            case "earth" -> NamedTextColor.GREEN;
            case "air"   -> NamedTextColor.GRAY;
            case "chi"   -> NamedTextColor.YELLOW;
            default      -> NamedTextColor.WHITE;
        };
        return Component.text(text).color(color);
    }

    public static Component warBroadcast(String attacker, String defender) {
        return Component.text("⚔ WAR DECLARED! ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text(attacker, NamedTextColor.RED))
                .append(Component.text(" has declared war on ", NamedTextColor.WHITE))
                .append(Component.text(defender, NamedTextColor.RED))
                .append(Component.text("!", NamedTextColor.WHITE));
    }

    public static Component avatarDeathBroadcast(String playerName) {
        return Component.text("☠ ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text("The Avatar ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" has fallen! ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("The world mourns...", NamedTextColor.GRAY));
    }

    public static Component avatarReincarnationBroadcast(String playerName, String element) {
        return Component.text("✦ ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("A new Avatar has been chosen! ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(playerName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" of ", NamedTextColor.WHITE))
                .append(elementColor(element, element.toUpperCase()).decorate(TextDecoration.BOLD))
                .append(Component.text(" will restore balance!", NamedTextColor.WHITE));
    }
}
