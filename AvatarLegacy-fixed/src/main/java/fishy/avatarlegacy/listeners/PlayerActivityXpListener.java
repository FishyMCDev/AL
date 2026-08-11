package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerActivityXpListener implements Listener {
    private final AvatarLegacy plugin;

    public PlayerActivityXpListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    private boolean hasCharacter(Player player) {
        return player != null && plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()) != null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "interact", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "interact-entity", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!hasCharacter(player)) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "fish-catch", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!hasCharacter(player)) return;
        ItemStack crafted = event.getCurrentItem();
        int count = crafted != null ? Math.max(1, crafted.getAmount()) : 1;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "craft", count);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSmeltExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "smelt-extract", Math.max(1, event.getItemAmount()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(org.bukkit.event.enchantment.EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "enchant", Math.max(1, event.getEnchantsToAdd().size()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        if (!hasCharacter(player)) return;
        if (!(event.getEntity() instanceof Animals)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "breed", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "tame", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Player player = event.getPlayer();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "shear", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "bucket-fill", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "bucket-empty", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Player player = event.getPlayer();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "harvest", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSleep(PlayerBedEnterEvent event) {
        Player player = event.getPlayer();
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "sleep", 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!hasCharacter(player)) return;
        plugin.getStatsManager().awardConfiguredXp(player.getUniqueId(), "item-pickup", 1);
    }
}
