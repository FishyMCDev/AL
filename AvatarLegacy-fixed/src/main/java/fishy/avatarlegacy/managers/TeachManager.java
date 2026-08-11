package fishy.avatarlegacy.managers;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles /teach: a nearby non-Avatar player teaches their bending element (including
 * Chi) to the current Avatar. Both players stand within range for a configured
 * duration while an elemental ring of particles surrounds them; moving too far apart
 * cancels the session.
 */
public class TeachManager {

    private final AvatarLegacy plugin;
    private final Map<UUID, BukkitTask> activeSessions = new ConcurrentHashMap<>();

    public TeachManager(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    public boolean isTeaching(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    public void startTeaching(Player teacher, Player avatar) {
        double closeDistance = plugin.getConfig().getDouble("teaching.close-distance-blocks", 5.0D);
        double breakDistance = plugin.getConfig().getDouble("teaching.break-distance-blocks", 90.0D);
        int durationSeconds  = plugin.getConfig().getInt("teaching.duration-seconds", 90);
        double minHours      = plugin.getConfig().getDouble("teaching.min-teacher-playtime-hours", 4.0D);

        if (isTeaching(teacher.getUniqueId()) || isTeaching(avatar.getUniqueId())) {
            teacher.sendMessage(MessageUtil.error("A teaching session is already in progress."));
            return;
        }

        if (!plugin.getAvatarManager().isAvatar(avatar.getUniqueId())) {
            teacher.sendMessage(MessageUtil.error(avatar.getName() + " is not the Avatar."));
            return;
        }

        if (teacher.getWorld() != avatar.getWorld()
                || teacher.getLocation().distance(avatar.getLocation()) > closeDistance) {
            teacher.sendMessage(MessageUtil.error(
                    "You need to be within " + (int) closeDistance + " blocks of the Avatar."));
            return;
        }

        PlayerData teacherData = plugin.getPlayerDataManager().getPlayerData(teacher.getUniqueId());
        PlayerData avatarData  = plugin.getPlayerDataManager().getPlayerData(avatar.getUniqueId());
        if (teacherData == null || avatarData == null) {
            teacher.sendMessage(MessageUtil.error("Player profiles are still loading. Try again shortly."));
            return;
        }

        String teacherElement = teacherData.getElement();
        if (teacherElement == null || teacherElement.isBlank()) {
            teacher.sendMessage(MessageUtil.error("You have no element to teach."));
            return;
        }

        if (teacherData.getPlaytimeSeconds() < (long) (minHours * 3600L)) {
            teacher.sendMessage(MessageUtil.error(
                    "You need at least " + minHours + " hours of playtime to teach."));
            return;
        }

        String avatarElement = avatarData.getElement();
        if (avatarElement != null && avatarElement.equalsIgnoreCase(teacherElement)) {
            teacher.sendMessage(MessageUtil.error("The Avatar has already learned " + teacherElement + "."));
            return;
        }

        teacher.sendMessage(MessageUtil.success("You begin teaching " + teacherElement.toUpperCase()
                + " to the Avatar. Stand close for " + durationSeconds + " seconds."));
        avatar.sendMessage(MessageUtil.success(teacher.getName() + " begins teaching you "
                + teacherElement.toUpperCase() + ". Stand close for " + durationSeconds + " seconds."));

        Particle.DustOptions dust = new Particle.DustOptions(elementColor(teacherElement), 1.2f);

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!teacher.isOnline() || !avatar.isOnline()
                        || teacher.getWorld() != avatar.getWorld()
                        || teacher.getLocation().distance(avatar.getLocation()) > breakDistance) {
                    teacher.sendMessage(MessageUtil.error("Teaching failed — you moved too far apart."));
                    if (avatar.isOnline()) avatar.sendMessage(MessageUtil.error("Teaching failed — you moved too far apart."));
                    activeSessions.remove(teacher.getUniqueId());
                    activeSessions.remove(avatar.getUniqueId());
                    cancel();
                    return;
                }

                ringParticles(teacher, dust);
                ringParticles(avatar, dust);

                elapsed++;
                if (elapsed >= durationSeconds) {
                    plugin.getElementManager().changeElementViaChanger(avatar, teacherElement);
                    teacher.sendMessage(MessageUtil.success("You have finished teaching " + teacherElement.toUpperCase() + "!"));
                    avatar.sendMessage(MessageUtil.success("You have learned " + teacherElement.toUpperCase() + "!"));
                    activeSessions.remove(teacher.getUniqueId());
                    activeSessions.remove(avatar.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        activeSessions.put(teacher.getUniqueId(), task);
        activeSessions.put(avatar.getUniqueId(), task);
    }

    private void ringParticles(Player center, Particle.DustOptions dust) {
        double radius = 1.2D;
        int points = 20;
        var loc = center.getLocation();
        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i) / points;
            double x = loc.getX() + radius * Math.cos(angle);
            double z = loc.getZ() + radius * Math.sin(angle);
            var particleLoc = loc.clone();
            particleLoc.setX(x);
            particleLoc.setZ(z);
            particleLoc.setY(loc.getY() + 0.1D);
            center.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, dust);
        }
    }

    private Color elementColor(String element) {
        return switch (element.toLowerCase()) {
            case "fire" -> Color.RED;
            case "water" -> Color.AQUA;
            case "earth" -> Color.fromRGB(34, 139, 34);
            case "air" -> Color.WHITE;
            case "chi" -> Color.YELLOW;
            default -> Color.PURPLE;
        };
    }
}