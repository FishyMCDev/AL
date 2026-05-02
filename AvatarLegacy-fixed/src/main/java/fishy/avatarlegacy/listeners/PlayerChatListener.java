package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

public class PlayerChatListener implements Listener {

    private final AvatarLegacy plugin;

    public PlayerChatListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    
    private String getLuckPermsPrefix(Player player) {
        LuckPerms lp = plugin.getLuckPermsIntegration().getLuckPermsApi();
        if (lp == null) return "";
        User user = lp.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "";
        CachedMetaData meta = user.getCachedData().getMetaData();
        String prefix = meta.getPrefix();
        return (prefix != null && !prefix.isBlank()) ? prefix : "";
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        String charName = plugin.getCharacterManager().getCachedCharacterName(uuid);
        if (charName == null || charName.isBlank()) return;

        boolean isAvatar = plugin.getAvatarManager().isAvatarCached(uuid);

        
        String rawPrefix = getLuckPermsPrefix(player);
        Component prefixComponent = rawPrefix.isBlank()
                ? Component.empty()
                : LegacyComponentSerializer.legacyAmpersand().deserialize(rawPrefix)
                        .append(Component.text(" ", NamedTextColor.WHITE));

        
        Component nameComponent = isAvatar
                ? Component.text("\u2605 ", NamedTextColor.GOLD, TextDecoration.BOLD)
                        .append(Component.text(charName, NamedTextColor.YELLOW, TextDecoration.BOLD))
                : Component.text(charName, NamedTextColor.WHITE);

        
        Component suffix = Component.text(" [", NamedTextColor.GRAY)
                .append(Component.text(player.getName(), NamedTextColor.DARK_GRAY))
                .append(Component.text("]", NamedTextColor.GRAY))
                .append(Component.text(" \u00bb ", NamedTextColor.WHITE));

        
        Component fullPrefix = prefixComponent
                .append(nameComponent)
                .append(suffix);

        event.renderer((source, sourceDisplayName, message, viewer) ->
                fullPrefix.append(message));
    }
}
