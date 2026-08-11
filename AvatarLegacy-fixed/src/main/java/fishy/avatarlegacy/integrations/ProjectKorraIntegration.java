package fishy.avatarlegacy.integrations;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectKorraIntegration {

    private final AvatarLegacy plugin;
    private final boolean enabled;


    private static final Map<String, String> ELEMENT_PREFIX_OVERRIDE = new HashMap<>();
    static {
        ELEMENT_PREFIX_OVERRIDE.put("fire",  "\u00a7c\u00a7lFire");
        ELEMENT_PREFIX_OVERRIDE.put("water", "\u00a7b\u00a7lWater");
        ELEMENT_PREFIX_OVERRIDE.put("earth", "\u00a7a\u00a7lEarth");
        ELEMENT_PREFIX_OVERRIDE.put("air",   "\u00a77\u00a7lAir");
        ELEMENT_PREFIX_OVERRIDE.put("chi",   "\u00a76\u00a7lChi");
        ELEMENT_PREFIX_OVERRIDE.put("avatar","\u00a7e\u00a7l\u2605 Avatar");
    }

    public ProjectKorraIntegration(AvatarLegacy plugin) {
        this.plugin = plugin;
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("ProjectKorra");
    }

    public boolean isEnabled()        { return enabled; }


    public String getElementPrefixOverride(String element) {
        if (element == null) return null;
        return ELEMENT_PREFIX_OVERRIDE.get(element.toLowerCase());
    }

    public String getPlayerElement(Player player) {
        if (!enabled || player == null) return null;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return null;
        if (bPlayer.hasElement(Element.FIRE))  return "fire";
        if (bPlayer.hasElement(Element.WATER)) return "water";
        if (bPlayer.hasElement(Element.EARTH)) return "earth";
        if (bPlayer.hasElement(Element.AIR))   return "air";
        if (bPlayer.hasElement(Element.CHI))   return "chi";
        return null;
    }

    public void setPlayerElement(Player player, String element) {
        if (!enabled || player == null) return;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        bPlayer.getElements().clear();
        switch (element.toLowerCase()) {
            case "fire"  -> bPlayer.addElement(Element.FIRE);
            case "water" -> bPlayer.addElement(Element.WATER);
            case "earth" -> bPlayer.addElement(Element.EARTH);
            case "air"   -> bPlayer.addElement(Element.AIR);
            case "chi"   -> bPlayer.addElement(Element.CHI);
        }
    }

    /**
     * True if the player currently holds the named ProjectKorra sub-element
     * (e.g. "Lightning", "Bloodbending", "Metalbending") on their live
     * BendingPlayer sub-element set. Used to gate skill-tree unlocks for
     * sub-element-only moves.
     */
    public boolean hasSubElement(Player player, String subelementName) {
        if (!enabled || player == null || subelementName == null) return false;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return false;
        for (Element.SubElement sub : bPlayer.getSubElements()) {
            if (sub.getName().equalsIgnoreCase(subelementName)) return true;
        }
        return false;
    }

    public void addAllElements(Player player) {
        if (!enabled || player == null) return;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        bPlayer.addElement(Element.FIRE);
        bPlayer.addElement(Element.WATER);
        bPlayer.addElement(Element.EARTH);
        bPlayer.addElement(Element.AIR);
    }

    public List<String> getSlottedAbilities(Player player) {
        List<String> abilities = new ArrayList<>();
        if (!enabled || player == null) return abilities;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return abilities;
        for (String ability : bPlayer.getAbilities().values()) {
            if (ability != null && !ability.isEmpty()) abilities.add(ability);
        }
        return abilities;
    }

    public void removeAbilityFromSlot(Player player, String moveName) {
        if (!enabled || player == null) return;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        Map<Integer, String> abilities = bPlayer.getAbilities();
        for (Map.Entry<Integer, String> entry : new HashMap<>(abilities).entrySet()) {
            if (moveName.equalsIgnoreCase(entry.getValue())) {
                abilities.remove(entry.getKey());
            }
        }
    }

    public void grantDefaultMoves(Player player, String element) {
        if (!enabled || player == null) return;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;

        List<String> defaultMoves = plugin.getConfig()
                .getStringList("protected-default-moves." + element.toLowerCase())
                .stream()
                .filter(move -> move != null && !move.isBlank())
                .collect(java.util.stream.Collectors.toList());

        if (defaultMoves.isEmpty()) {
            plugin.getLogger().warning("[AvatarLegacy] No default moves configured for element: "
                    + element + ". Add them under protected-default-moves."
                    + element + " in config.yml");
            return;
        }

        Map<Integer, String> slots = bPlayer.getAbilities();
        for (int i = 0; i < defaultMoves.size() && i < 9; i++) {
            String moveName = defaultMoves.get(i);
            slots.put(i + 1, moveName);
            plugin.getLuckPermsIntegration().grantPermission(
                    player.getUniqueId(), "bending.ability." + moveName.toLowerCase());
        }
    }

    public void unlearnAbility(Player player, String abilityName) {
        if (player == null) return;
        plugin.getLuckPermsIntegration().revokePermission(
                player.getUniqueId(), "bending.ability." + abilityName.toLowerCase());
    }

    public void relearnAbility(Player player, String abilityName) {
        if (player == null) return;
        plugin.getLuckPermsIntegration().grantPermission(
                player.getUniqueId(), "bending.ability." + abilityName.toLowerCase());
    }

    public void clearAllAbilities(java.util.UUID uuid) {
        if (!enabled) return;
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;
        BendingPlayer bPlayer = BendingPlayer.getBendingPlayer(player);
        if (bPlayer == null) return;
        bPlayer.getAbilities().clear();
        bPlayer.getElements().clear();
    }
}