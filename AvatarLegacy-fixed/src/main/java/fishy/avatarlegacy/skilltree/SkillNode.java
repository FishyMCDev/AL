package fishy.avatarlegacy.skilltree;

import java.util.Collections;
import java.util.List;

/**
 * Represents a single ability node in the skill tree.
 *
 * ── Grid position ─────────────────────────────────────────────────────────────
 *   (posX, posY) map onto an infinite scrollable grid.
 *   The SkillTreeGUI translates them to inventory slots:
 *     screenX = posX - offsetX
 *     screenY = posY - offsetY
 *     slot    = screenY * 9 + screenX   (only drawn if 0 ≤ screenX ≤ 8 and 0 ≤ screenY ≤ 4)
 *
 * ── Color field ───────────────────────────────────────────────────────────────
 *   Controls which resource-pack icon is shown.
 *   Recognised values (case-insensitive): PURPLE (special/magic node).
 *   All other values fall through to the standard locked/unlockable/unlocked icons.
 *
 * ── is-default ────────────────────────────────────────────────────────────────
 *   Default nodes are always considered unlocked and are never removed on death.
 *
 * ── is-passive ────────────────────────────────────────────────────────────────
 *   Passive nodes are not rendered in the GUI and are not targetable by the
 *   death-removal logic.
 */
public class SkillNode {

    private String       nodeId;
    private String       abilityName;   // ProjectKorra ability name (for perm grant)
    private String       displayName;   // May contain legacy colour codes
    private List<String> lore;          // Description lines (may contain colour codes)
    private int          posX;
    private int          posY;
    private SkillRequirements requirements;
    private List<String> dependsOn;     // List of nodeIds that must be unlocked first
    private String       color;         // PURPLE | (anything else)
    private boolean      isDefault;
    private boolean      isPassive;
    /**
     * Name of the ProjectKorra sub-element this move belongs to (e.g. "Lightning",
     * "Bloodbending"), or null if the move is a plain element move with no
     * sub-element gate. Players without that sub-element (via trait roll,
     * granted trait, or the matching {@code avatarlegacy.subelement.<name>}
     * permission) cannot unlock nodes carrying this flag — see
     * {@link SkillTreeManager#canUnlock}.
     */
    private String       subelement;

    public SkillNode() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public String            getNodeId()        { return nodeId; }
    public String            getAbilityName()   { return abilityName; }
    public String            getDisplayName()   { return displayName; }
    public List<String>      getLore()          { return lore != null ? lore : Collections.emptyList(); }
    public int               getPosX()          { return posX; }
    public int               getPosY()          { return posY; }
    public SkillRequirements getRequirements()  { return requirements; }
    public List<String>      getDependsOn()     { return dependsOn != null ? dependsOn : Collections.emptyList(); }
    public String            getColor()         { return color; }
    public boolean           isDefault()        { return isDefault; }
    public boolean           isPassive()        { return isPassive; }
    public String            getSubelement()    { return subelement; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setNodeId(String v)                  { this.nodeId       = v; }
    public void setAbilityName(String v)             { this.abilityName  = v; }
    public void setDisplayName(String v)             { this.displayName  = v; }
    public void setLore(List<String> v)              { this.lore         = v; }
    public void setPosX(int v)                       { this.posX         = v; }
    public void setPosY(int v)                       { this.posY         = v; }
    public void setRequirements(SkillRequirements v) { this.requirements = v; }
    public void setDependsOn(List<String> v)         { this.dependsOn    = v; }
    public void setColor(String v)                   { this.color        = v; }
    public void setDefault(boolean v)                { this.isDefault    = v; }
    public void setPassive(boolean v)                { this.isPassive    = v; }
    public void setSubelement(String v)              { this.subelement   = v; }
}