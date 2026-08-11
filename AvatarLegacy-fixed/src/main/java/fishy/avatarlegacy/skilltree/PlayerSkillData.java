package fishy.avatarlegacy.skilltree;

import java.util.*;

/**
 * In-memory representation of one player's unlocked skill tree nodes.
 *
 * Keyed by treeKey (lower-case: water/earth/fire/air/chi/avatar) → set of
 * unlocked nodeIds. Backed by HashSet for O(1) contains checks.
 *
 * Not thread-safe by design — all mutation happens on the main server thread.
 * SkillTreeManager caches instances in a ConcurrentHashMap but only writes
 * them from the main thread.
 */
public class PlayerSkillData {

    private final UUID playerUUID;

    /** treeKey → unlocked nodeIds */
    private final Map<String, Set<String>> unlockedNodes = new HashMap<>();

    public PlayerSkillData(UUID playerUUID) {
        this.playerUUID = playerUUID;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public UUID getPlayerUUID() { return playerUUID; }

    /**
     * Returns an unmodifiable view of unlocked nodeIds for a tree. Never null.
     */
    public Set<String> getUnlockedNodes(String treeKey) {
        return Collections.unmodifiableSet(
                unlockedNodes.getOrDefault(treeKey.toLowerCase(), Collections.emptySet()));
    }

    public boolean isNodeUnlocked(String treeKey, String nodeId) {
        Set<String> nodes = unlockedNodes.get(treeKey.toLowerCase());
        return nodes != null && nodes.contains(nodeId);
    }

    /** Total unlocked nodes across all trees. */
    public int getTotalUnlocked() {
        return unlockedNodes.values().stream().mapToInt(Set::size).sum();
    }

    /** All tree keys that have at least one unlocked node. */
    public Set<String> getActiveTreeKeys() {
        return Collections.unmodifiableSet(unlockedNodes.keySet());
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** Mark a node unlocked. Called by SkillTreeManager and DB load path. */
    public void unlockNode(String treeKey, String nodeId) {
        unlockedNodes.computeIfAbsent(treeKey.toLowerCase(), k -> new HashSet<>()).add(nodeId);
    }

    /** Remove a single node from the unlocked set. Used on death / admin revoke. */
    public void lockNode(String treeKey, String nodeId) {
        Set<String> nodes = unlockedNodes.get(treeKey.toLowerCase());
        if (nodes != null) nodes.remove(nodeId);
    }

    /**
     * Remove ALL unlocked nodes for a tree.
     * Used when revoking the Avatar tree or admin-resetting an element.
     */
    public void resetTree(String treeKey) {
        unlockedNodes.remove(treeKey.toLowerCase());
    }
}