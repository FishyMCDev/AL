package fishy.avatarlegacy.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Player;

import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Discovers ProjectKorra sub-elements and persists each player's rolled traits by parent element. */
public final class TraitManager {
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();
    private final AvatarLegacy plugin;
    private final Gson gson = new Gson();

    public TraitManager(AvatarLegacy plugin) { this.plugin = plugin; ensureTraitConfig(); }

    public void ensureTraitConfig() {
        boolean changed = false;
        for (Element.SubElement sub : Element.getAllSubElements()) {
            Element parent = sub.getParentElement();
            if (parent == null) continue;
            String path = "traits.chances." + parent.getName().toLowerCase() + "." + sub.getName();
            if (!plugin.getConfig().contains(path)) { plugin.getConfig().set(path, 0.0D); changed = true; }
        }
        if (changed) plugin.saveConfig();
    }

    public List<String> getTraits(java.util.UUID uuid, String element) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT traits_json FROM player_traits WHERE uuid = ? AND element = ?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, element.toLowerCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                List<String> traits = gson.fromJson(rs.getString(1), LIST_TYPE);
                return traits == null ? new ArrayList<>() : new ArrayList<>(traits);
            }
        } catch (Exception e) { plugin.getLogger().warning("Could not load traits: " + e.getMessage()); }
        return new ArrayList<>();
    }

    public boolean hasRolled(java.util.UUID uuid, String element) {
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "SELECT 1 FROM player_traits WHERE uuid = ? AND element = ?")) {
            ps.setString(1, uuid.toString()); ps.setString(2, element.toLowerCase()); return ps.executeQuery().next();
        } catch (Exception e) { return false; }
    }

    /** First choice rolls once. Switching elements only reapplies the already persisted result. */
    public List<String> applyTraits(Player player, String element, boolean reroll) {
        String normalized = element.toLowerCase();
        if ("chi".equals(normalized)) return List.of();
        List<String> traits = reroll || !hasRolled(player.getUniqueId(), normalized) ? roll(player, normalized) : getTraits(player.getUniqueId(), normalized);
        BendingPlayer bending = BendingPlayer.getBendingPlayer(player);
        if (bending != null) {
            // Remove only subelements owned by the active parent before adding the rolled set.
            bending.getSubElements().removeIf(sub -> sub.getParentElement() != null && sub.getParentElement().getName().equalsIgnoreCase(normalized));
            for (String name : traits) for (Element.SubElement sub : Element.getAllSubElements())
                if (sub.getName().equalsIgnoreCase(name)) bending.addSubElement(sub);
            bending.saveSubElements();
        }
        return traits;
    }

    private List<String> roll(Player player, String element) {
        List<String> traits = new ArrayList<>();
        for (Element.SubElement sub : Element.getAllSubElements()) {
            Element parent = sub.getParentElement();
            if (parent == null || !parent.getName().equalsIgnoreCase(element)) continue;
            double chance = plugin.getConfig().getDouble("traits.chances." + element + "." + sub.getName(), 0D);
            if (ThreadLocalRandom.current().nextDouble(100D) < Math.max(0D, Math.min(100D, chance))) traits.add(sub.getName());
        }
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                "INSERT INTO player_traits (uuid, element, traits_json) VALUES (?, ?, ?) ON CONFLICT(uuid, element) DO UPDATE SET traits_json = excluded.traits_json")) {
            ps.setString(1, player.getUniqueId().toString()); ps.setString(2, element); ps.setString(3, gson.toJson(traits)); ps.executeUpdate();
        } catch (Exception e) { plugin.getLogger().warning("Could not save traits: " + e.getMessage()); }
        return traits;
    }

    /**
     * Grants a specific subelement directly to the player (e.g. from consuming a
     * subelement-access-item), bypassing the normal random roll. Returns false if
     * the subelement name doesn't match any known ProjectKorra subelement, or the
     * player has no active parent element for it.
     */
    public boolean grantSubelementDirect(Player player, String subelementName) {
        BendingPlayer bending = BendingPlayer.getBendingPlayer(player);
        if (bending == null) return false;

        for (Element.SubElement sub : Element.getAllSubElements()) {
            if (!sub.getName().equalsIgnoreCase(subelementName)) continue;
            Element parent = sub.getParentElement();
            if (parent == null) return false;

            bending.addSubElement(sub);
            bending.saveSubElements();

            List<String> traits = getTraits(player.getUniqueId(), parent.getName().toLowerCase());
            if (!traits.contains(sub.getName())) {
                traits.add(sub.getName());
                try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(
                        "INSERT INTO player_traits (uuid, element, traits_json) VALUES (?, ?, ?) ON CONFLICT(uuid, element) DO UPDATE SET traits_json = excluded.traits_json")) {
                    ps.setString(1, player.getUniqueId().toString());
                    ps.setString(2, parent.getName().toLowerCase());
                    ps.setString(3, gson.toJson(traits));
                    ps.executeUpdate();
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not save granted trait: " + e.getMessage());
                }
            }
            return true;
        }
        return false;
    }
}