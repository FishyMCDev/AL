package fishy.avatarlegacy.hooks;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.hooks.CanBendHook;
import com.projectkorra.projectkorra.hooks.CanBindHook;
import fishy.avatarlegacy.AvatarLegacy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AvatarLegacyBendingHook implements CanBendHook, CanBindHook {

    private final AvatarLegacy plugin;

    private final Map<UUID, List<String>> removedCache   = new HashMap<>();
    private final Map<UUID, List<String>> protectedCache = new HashMap<>();
    private final Map<UUID, Long>         cacheTime      = new HashMap<>();
    private static final long CACHE_TTL_MS = 5_000L;

    public AvatarLegacyBendingHook(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<Boolean> canBend(BendingPlayer bPlayer,
                                     CoreAbility ability,
                                     boolean ignoreBinds,
                                     boolean ignoreCooldowns) {
        if (bPlayer == null || ability == null) return Optional.empty();
        return evaluate(bPlayer.getUUID(), ability.getName());
    }

    @Override
    public Optional<Boolean> canBind(BendingPlayer bPlayer, CoreAbility ability) {
        if (bPlayer == null || ability == null) return Optional.empty();
        return evaluate(bPlayer.getUUID(), ability.getName());
    }

    private Optional<Boolean> evaluate(UUID uuid, String abilityName) {
        try {
            refreshCache(uuid);

            List<String> removed = removedCache.getOrDefault(uuid, List.of());
            for (String move : removed) {
                if (move.equalsIgnoreCase(abilityName)) return Optional.of(false);
            }

            List<String> Protected = protectedCache.getOrDefault(uuid, List.of());
            for (String move : Protected) {
                if (move.equalsIgnoreCase(abilityName)) {
                    if (plugin.getAvatarManager().isAvatarCached(uuid)) {
                        com.projectkorra.projectkorra.ability.CoreAbility lookedUpAbility =
                                com.projectkorra.projectkorra.ability.CoreAbility.getAbility(abilityName);
                        if (lookedUpAbility != null) {
                            com.projectkorra.projectkorra.Element el = lookedUpAbility.getElement();
                            if (el != null && el.toString().equalsIgnoreCase("chi")) {
                                return Optional.of(false);
                            }
                        }
                    }
                    return Optional.of(true);
                }
            }

        } catch (Exception e) {
            plugin.getLogger().warning("[BendingHook] Error for " + uuid + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private void refreshCache(UUID uuid) {
        long now = System.currentTimeMillis();
        Long last = cacheTime.get(uuid);
        if (last != null && (now - last) < CACHE_TTL_MS) return;

        removedCache.put(uuid, plugin.getStatsManager().getRemovedMoves(uuid));
        protectedCache.put(uuid, plugin.getProtectedMovesManager().getProtectedMoves(uuid));
        cacheTime.put(uuid, now);
    }

    public void invalidateCache(UUID uuid) {
        removedCache.remove(uuid);
        protectedCache.remove(uuid);
        cacheTime.remove(uuid);
    }
}
