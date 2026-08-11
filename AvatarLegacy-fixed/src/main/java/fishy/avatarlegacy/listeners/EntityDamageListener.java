package fishy.avatarlegacy.listeners;

import com.projectkorra.projectkorra.event.AbilityDamageEntityEvent;
import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

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
        Player damager = getDamagingPlayer(event.getDamager());
        if (damager == null) return;
        if (event.getEntity() instanceof Player victim && cancelGracePvP(event, damager, victim)) return;
        double finalDamage = applyPhysicalMultipliers(damager.getUniqueId(), event.getDamage());
        event.setDamage(finalDamage);
        plugin.getStatsManager().awardConfiguredXp(damager.getUniqueId(), "damage-dealt", (int) Math.ceil(finalDamage));
    }

    
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAbilityDamage(AbilityDamageEntityEvent event) {
        Player source = event.getSource();
        if (source == null) return;
        if (event.getEntity() instanceof Player victim && cancelGracePvP(event, source, victim)) return;

        double newDamage = applyBendingMultipliers(source.getUniqueId(), event.getDamage());
        ConfigurationSection abilityModifiers = plugin.getConfig().getConfigurationSection("ability-damage-modifiers");
        if (abilityModifiers != null && event.getAbility() != null) {
            String exactKey = "abilities." + event.getAbility().getName();
            String lowerKey = "abilities." + event.getAbility().getName().toLowerCase();
            double exactMul = abilityModifiers.getDouble(exactKey, 1.0);
            double lowerMul = abilityModifiers.getDouble(lowerKey, 1.0);
            newDamage *= exactMul * lowerMul;
        }
        event.setDamage(newDamage);

        plugin.getStatsManager().addProjectKorraDamageDealt(source.getUniqueId(), newDamage);
        plugin.getStatsManager().awardConfiguredXp(source.getUniqueId(), "ability-damage", (int) Math.ceil(newDamage));
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

    private Player getDamagingPlayer(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private boolean cancelGracePvP(org.bukkit.event.Cancellable event, Player attacker, Player victim) {
        if (attacker == null || victim == null || attacker.equals(victim)) return false;
        boolean attackerGrace = plugin.getNationManager().isPlayerNationInGracePeriod(attacker.getUniqueId());
        boolean victimGrace = plugin.getNationManager().isPlayerNationInGracePeriod(victim.getUniqueId());
        if (!attackerGrace && !victimGrace) return false;
        event.setCancelled(true);
        attacker.sendMessage("\u00a7cPvP is disabled while either nation is in its grace period.");
        return true;
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
