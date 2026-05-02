package fishy.avatarlegacy.listeners;

import com.projectkorra.projectkorra.event.AbilityDamageEntityEvent;
import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

public class EntityDamageListener implements Listener {

    private final AvatarLegacy plugin;

    
    private boolean cachedReincarnation = false;
    private long lastReincarnationCheck = 0L;
    private static final long REINCARNATION_CACHE_MS = 5000L;

    
    private static final int MIN_BENDING_STRENGTH = 1;

    public EntityDamageListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;

        event.setDamage(applyPhysicalMultipliers(damager.getUniqueId(), event.getDamage()));
    }

    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAbilityDamage(AbilityDamageEntityEvent event) {
        Player source = event.getSource();
        if (source == null) return;

        double newDamage = applyBendingMultipliers(source.getUniqueId(), event.getDamage());
        event.setDamage(newDamage);

        plugin.getStatsManager().addProjectKorraDamageDealt(source.getUniqueId(), newDamage);
    }

    private double applyPhysicalMultipliers(UUID uuid, double damage) {
        if (isReincarnationActiveCache()) {
            double debuffPercent = plugin.getConfig().getInt("avatar.global-debuff-during-reincarnation", 10) / 100.0;
            damage *= (1.0 - debuffPercent);
        }

        Integer nationId = plugin.getNationManager().getPlayerNation(uuid);
        if (nationId != null) {
            int upgradeLevel = plugin.getNationManager().getCitizenStrengthLevel(nationId);
            if (upgradeLevel > 0) {
                damage *= 1.0 + (upgradeLevel * 0.05);
            }
        }

        return damage;
    }

    private double applyBendingMultipliers(UUID uuid, double damage) {
        int strength = plugin.getStatsManager().getBendingStrength(uuid);
        
        strength = Math.max(MIN_BENDING_STRENGTH, strength);
        damage *= (strength / 100.0);

        if (isReincarnationActiveCache()) {
            double debuffPercent = plugin.getConfig().getInt("avatar.global-debuff-during-reincarnation", 10) / 100.0;
            damage *= (1.0 - debuffPercent);
        }

        Integer nationId = plugin.getNationManager().getPlayerNation(uuid);
        if (nationId != null) {
            int upgradeLevel = plugin.getNationManager().getCitizenStrengthLevel(nationId);
            if (upgradeLevel > 0) {
                damage *= 1.0 + (upgradeLevel * 0.05);
            }
        }

        return damage;
    }

    
    private synchronized boolean isReincarnationActiveCache() {
        long now = System.currentTimeMillis();
        if (now - lastReincarnationCheck > REINCARNATION_CACHE_MS) {
            cachedReincarnation = plugin.getAvatarManager().isReincarnationActive();
            lastReincarnationCheck = now;
        }
        return cachedReincarnation;
    }
}
