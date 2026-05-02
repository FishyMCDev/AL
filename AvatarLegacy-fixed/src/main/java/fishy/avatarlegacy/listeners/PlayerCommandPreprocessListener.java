package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerCommandPreprocessListener implements Listener {

    private final AvatarLegacy plugin;

    
    private static final Set<String> ALWAYS_ALLOWED = Set.of(
            "/character", "/character create",
            "/characterwho", "/charwho",
            "/help", "/login", "/register"
    );

    
    private static final Set<String> TELEPORT_COMMANDS = Set.of(
            "/tp", "/teleport",
            "/home", "/sethome",
            "/spawn", "/warp",
            "/back", "/tpa", "/tpaccept", "/tpdeny", "/tphere"
    );

    
    private final Map<UUID, Long> tpCommandCooldowns = new HashMap<>();

    
    private static final long TP_COOLDOWN_MS = 5_000L;

    public PlayerCommandPreprocessListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        String message = event.getMessage().toLowerCase().trim();
        
        String baseCommand = message.split("\\s+")[0];

        
        for (String allowed : ALWAYS_ALLOWED) {
            if (message.equals(allowed) || message.startsWith(allowed + " ")) return;
        }

        // While frozen (character creation / element selection), only allow /b choose
        if (plugin.getSkyFreezeManager().isFrozen(player.getUniqueId())) {
            boolean isChoose = message.equals("/b choose") || message.startsWith("/b choose ")
                    || message.equals("/bending choose") || message.startsWith("/bending choose ");
            if (!isChoose) {
                event.setCancelled(true);
                player.sendMessage(MessageUtil.error("You cannot use commands while choosing your element!"));
                player.sendMessage(MessageUtil.warning("Use §e/b choose <element>§6 to be released."));
                return;
            }
        }

        
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            event.setCancelled(true);
            player.sendMessage(MessageUtil.error("You do not have an active character!"));
            player.sendMessage(MessageUtil.warning("Use \u00a7e/character create <n>\u00a76 to begin your journey."));
            return;
        }

        
        if (plugin.getAvatarManager().isAvatarCached(player.getUniqueId())) {
            if (message.startsWith("/b add ") || message.startsWith("/bending add ")) {
                String[] parts = message.split("\\s+");
                if (parts.length >= 3 && parts[2].equalsIgnoreCase("chi")) {
                    event.setCancelled(true);
                    player.sendMessage(MessageUtil.error("The Avatar cannot add Chi bending!"));
                    return;
                }
            }
        }

        
        boolean isTpCommand = TELEPORT_COMMANDS.contains(baseCommand);
        if (isTpCommand) {
            long tpCooldownMs = plugin.getConfig().getLong("teleport.command-cooldown-seconds", 5) * 1000L;
            UUID uuid = player.getUniqueId();
            Long lastUsed = tpCommandCooldowns.get(uuid);
            long now = System.currentTimeMillis();
            if (lastUsed != null && (now - lastUsed) < tpCooldownMs) {
                long remaining = (tpCooldownMs - (now - lastUsed)) / 1000L + 1;
                event.setCancelled(true);
                player.sendMessage(MessageUtil.error("Teleport commands are on cooldown! Available in §e" + remaining + "s§c."));
                return;
            }
            tpCommandCooldowns.put(uuid, now);
        }
    }

    public void evict(UUID uuid) {
        tpCommandCooldowns.remove(uuid);
    }
}

