package fishy.avatarlegacy.skilltree;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates all unlock requirements for a single SkillNode.
 *
 * ── Override vs base-value pattern ───────────────────────────────────────────
 *   Each numeric requirement has an "override" field (0 = not set) and an
 *   effective-value helper that falls back to the global base from
 *   skilltree-config.yml when the override is 0.
 *
 *   Example (config):
 *     leveling:
 *       base-xp:               100   ← used when xpOverride == 0
 *       base-playtime-minutes:  60   ← used when playtimeMinOverride == 0
 *       base-kills:              5   ← used when killsOverride == 0
 *
 *   The GUI and canUnlock() both call getEffective*() so they always agree.
 *
 * ── prerequisite-nodes ────────────────────────────────────────────────────────
 *   A second list of node IDs (in addition to SkillNode.dependsOn) that must
 *   be unlocked. Use dependsOn for structural tree edges (draws connector lines)
 *   and prerequisiteNodes for soft "hidden" dependencies that don't draw lines.
 */
public class SkillRequirements {

    private int          level;                // minimum player level
    private int          xpOverride;           // 0 = use base-xp from config
    private int          playtimeMinOverride;  // 0 = use base-playtime-minutes
    private int          killsOverride;        // 0 = use base-kills
    private List<String> prerequisiteNodes;    // extra node IDs (no line drawn)

    public SkillRequirements() {
        this.prerequisiteNodes = Collections.emptyList();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int          getLevel()              { return level; }
    public int          getXpOverride()         { return xpOverride; }
    public int          getPlaytimeMinOverride(){ return playtimeMinOverride; }
    public int          getKillsOverride()      { return killsOverride; }
    public List<String> getPrerequisiteNodes()  {
        return prerequisiteNodes != null ? prerequisiteNodes : Collections.emptyList();
    }

    // ── Effective value helpers ───────────────────────────────────────────────

    /**
     * Returns the XP threshold this node requires.
     * If xpOverride is 0, falls back to {@code baseXp} from config.
     * Returns 0 when neither is set (no XP requirement).
     */
    public int getEffectiveXp(int baseXp) {
        return xpOverride > 0 ? xpOverride : baseXp;
    }

    /**
     * Returns the playtime threshold (in minutes) this node requires.
     * Falls back to {@code basePtMin} when override is 0.
     */
    public int getEffectivePlaytimeMin(int basePtMin) {
        return playtimeMinOverride > 0 ? playtimeMinOverride : basePtMin;
    }

    /**
     * Returns the kill count threshold this node requires.
     * Falls back to {@code baseKills} when override is 0.
     */
    public int getEffectiveKills(int baseKills) {
        return killsOverride > 0 ? killsOverride : baseKills;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setLevel(int v)                      { this.level               = v; }
    public void setXpOverride(int v)                 { this.xpOverride          = v; }
    public void setPlaytimeMinOverride(int v)        { this.playtimeMinOverride  = v; }
    public void setKillsOverride(int v)              { this.killsOverride        = v; }
    public void setPrerequisiteNodes(List<String> v) { this.prerequisiteNodes    = v; }
}
