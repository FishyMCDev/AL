package fishy.avatarlegacy.hooks;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ability.CoreAbility;
import com.projectkorra.projectkorra.hooks.CanBendHook;
import com.projectkorra.projectkorra.hooks.CanBindHook;
import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.skilltree.PlayerSkillData;
import fishy.avatarlegacy.skilltree.SkillNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ProjectKorra bending hook — enforces skill-tree ownership and the
 * "Avatar cannot use Chi" rule at the PK ability-check level.
 *
 * ── Evaluation order ─────────────────────────────────────────────────────────
 *  1. AVATAR + CHI → always false. No exceptions. Uses SkillTreeManager's
 *     isChiAbility() which correctly resolves SubElement parent chains, so
 *     ALL chi-rooted sub-abilities (Blocking, etc.) are also denied.
 *
 *  2. DEFAULT / PROTECTED MOVES → always true (these are never removable).
 *
 *  3. SKILL TREE LOOKUP:
 *     a. Find the node for this ability in the player's active tree(s).
 *     b. If node found AND unlocked → true.
 *     c. If node found AND locked   → false (explicit deny, no LP fallback).
 *     d. If node not found in any tree → defer to PK/LP (Optional.empty).
 *
 *  Avatars check BOTH their current element tree AND the avatar tree.
 *  Normal benders check only their element tree.
 *  Chi benders check only the chi tree.
 *
 * ── Cache ────────────────────────────────────────────────────────────────────
 *  Protected-move and avatar-status lookups are cached for 5 s per player.
 *  Call invalidateCache(uuid) after any lock/unlock to force re-evaluation.
 */
public class AvatarLegacyBendingHook implements CanBendHook, CanBindHook {

    private static final long CACHE_TTL_MS = 5_000L;

    private final AvatarLegacy plugin;

    // Per-UUID caches
    private final Map<UUID, List<String>> protectedCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean>      avatarCache    = new ConcurrentHashMap<>();
    private final Map<UUID, Long>         cacheTime      = new ConcurrentHashMap<>();

    public AvatarLegacyBendingHook(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Optional<Boolean> canBend(BendingPlayer bPlayer,
                                     CoreAbility ability,
                                     boolean ignoreBinds,
                                     boolean ignoreCooldowns) {
        if (bPlayer == null || ability == null) return Optional.empty();
        return evaluate(bPlayer.getUUID(), ability);
    }

    @Override
    public Optional<Boolean> canBind(BendingPlayer bPlayer, CoreAbility ability) {
        if (bPlayer == null || ability == null) return Optional.empty();
        return evaluate(bPlayer.getUUID(), ability);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Core evaluation
    // ─────────────────────────────────────────────────────────────────────────

    private Optional<Boolean> evaluate(UUID uuid, CoreAbility ability) {
        try {
            refreshCache(uuid);

            // ── Rule 1: Avatar cannot use Chi ─────────────────────────────────
            // SkillTreeManager.isChiAbility() resolves SubElement parent chains,
            // so Blocking (a Chi sub-ability) is correctly caught here too.
            if (avatarCache.getOrDefault(uuid, false)
                    && plugin.getSkillTreeManager().isChiAbility(ability)) {
                return Optional.of(false);
            }

            // ── Rule 2: Protected / default moves always allowed ───────────────
            List<String> protectedMoves = protectedCache.getOrDefault(uuid, Collections.emptyList());
            for (String move : protectedMoves) {
                if (move.equalsIgnoreCase(ability.getName())) {
                    // Still block chi even if somehow listed as protected for an Avatar
                    if (avatarCache.getOrDefault(uuid, false)
                            && plugin.getSkillTreeManager().isChiAbility(ability)) {
                        return Optional.of(false);
                    }
                    return Optional.of(true);
                }
            }

            // ── Rule 3: Skill-tree ownership check ────────────────────────────
            PlayerData pd = plugin.getPlayerDataManager().getPlayerData(uuid);
            if (pd == null || pd.getElement() == null) return Optional.empty();

            String  element  = pd.getElement().toLowerCase();
            boolean isAvatar = avatarCache.getOrDefault(uuid, false);

            // Build ordered list of trees to check.
            // Avatar checks their element tree first, then the avatar tree.
            List<String> treesToCheck;
            if (isAvatar) {
                treesToCheck = List.of(element, "avatar");
            } else {
                treesToCheck = List.of(element);
            }

            PlayerSkillData skillData = plugin.getSkillTreeManager().loadPlayerData(uuid);

            for (String treeKey : treesToCheck) {
                SkillNode node = plugin.getSkillTreeManager()
                        .getNodeByAbility(treeKey, ability.getName());
                if (node == null) continue;

                // Default nodes are always unlocked
                if (node.isDefault()) return Optional.of(true);

                // Node found in this tree — check unlock state
                return Optional.of(skillData.isNodeUnlocked(treeKey, node.getNodeId()));
            }

            // ── Rule 4: Ability not found in any tree — defer to PK/LP ────────
            // This handles edge cases: add-on abilities not yet in config, etc.

        } catch (Exception e) {
            plugin.getLogger().warning("[BendingHook] Evaluation error for "
                    + uuid + " / " + ability.getName() + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void refreshCache(UUID uuid) {
        long now  = System.currentTimeMillis();
        Long last = cacheTime.get(uuid);
        if (last != null && (now - last) < CACHE_TTL_MS) return;

        protectedCache.put(uuid, plugin.getProtectedMovesManager().getProtectedMoves(uuid));
        avatarCache.put(uuid, plugin.getAvatarManager().isAvatarCached(uuid));
        cacheTime.put(uuid, now);
    }

    public void invalidateCache(UUID uuid) {
        protectedCache.remove(uuid);
        avatarCache.remove(uuid);
        cacheTime.remove(uuid);
    }
}