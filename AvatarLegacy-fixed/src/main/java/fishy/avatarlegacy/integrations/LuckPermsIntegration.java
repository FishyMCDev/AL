package fishy.avatarlegacy.integrations;

import fishy.avatarlegacy.AvatarLegacy;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LuckPermsIntegration {

    private final AvatarLegacy plugin;
    private LuckPerms luckPerms;

    public LuckPermsIntegration(AvatarLegacy plugin) {
        this.plugin = plugin;
        RegisteredServiceProvider<LuckPerms> provider =
                plugin.getServer().getServicesManager().getRegistration(LuckPerms.class);
        this.luckPerms = provider != null ? provider.getProvider() : null;
    }

    public boolean isEnabled() {
        return luckPerms != null;
    }

    public void grantPermission(UUID uuid, String permission) {
        if (!isEnabled()) return;
        User cached = luckPerms.getUserManager().getUser(uuid);
        if (cached != null) {
            cached.data().add(Node.builder(permission).build());
            luckPerms.getUserManager().saveUser(cached);
        } else {
            luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
                if (user != null) {
                    user.data().add(Node.builder(permission).build());
                    luckPerms.getUserManager().saveUser(user);
                }
            });
        }
    }

    public void revokePermission(UUID uuid, String permission) {
        if (!isEnabled()) return;
        User cached = luckPerms.getUserManager().getUser(uuid);
        if (cached != null) {
            cached.data().remove(Node.builder(permission).build());
            luckPerms.getUserManager().saveUser(cached);
        } else {
            luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
                if (user != null) {
                    user.data().remove(Node.builder(permission).build());
                    luckPerms.getUserManager().saveUser(user);
                }
            });
        }
    }

    
    
    

    
    public void addGroup(UUID uuid, String groupName) {
        if (!isEnabled()) return;
        User cached = luckPerms.getUserManager().getUser(uuid);
        if (cached != null) {
            cached.data().add(InheritanceNode.builder(groupName).build());
            luckPerms.getUserManager().saveUser(cached);
        } else {
            luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
                if (user != null) {
                    user.data().add(InheritanceNode.builder(groupName).build());
                    luckPerms.getUserManager().saveUser(user);
                }
            });
        }
    }

    
    public void removeGroup(UUID uuid, String groupName) {
        if (!isEnabled()) return;
        User cached = luckPerms.getUserManager().getUser(uuid);
        if (cached != null) {
            cached.data().remove(InheritanceNode.builder(groupName).build());
            luckPerms.getUserManager().saveUser(cached);
        } else {
            luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
                if (user != null) {
                    user.data().remove(InheritanceNode.builder(groupName).build());
                    luckPerms.getUserManager().saveUser(user);
                }
            });
        }
    }

    

    public void grantProtectedMovePermissions(Player player, String element) {
        if (player == null) return;
        grantProtectedMovePermissions(player, element, player.getUniqueId());
    }

    public void grantProtectedMovePermissions(Player player, String element, UUID uuid) {
        
        
        if (plugin.getProjectKorraIntegration().isScrollsEnabled()) return;
        List<String> moves = plugin.getConfig()
                .getStringList("protected-default-moves." + element.toLowerCase());
        for (String move : moves) {
            grantPermission(uuid, "bending.ability." + move.toLowerCase());
        }
    }

    public void revokeProtectedMovePermissions(Player player, String element) {
        if (player == null) return;
        
        if (plugin.getProjectKorraIntegration().isScrollsEnabled()) return;
        List<String> moves = plugin.getConfig()
                .getStringList("protected-default-moves." + element.toLowerCase());
        for (String move : moves) {
            revokePermission(player.getUniqueId(), "bending.ability." + move.toLowerCase());
        }
    }

    public void grantAvatarPermissions(UUID uuid) {
        grantPermission(uuid, "bending.command.add");
        grantPermission(uuid, "avatarlegacy.avatar");
        
        addGroup(uuid, "Avatar");
    }

    public void revokeAvatarPermissions(UUID uuid) {
        revokePermission(uuid, "bending.command.add");
        revokePermission(uuid, "avatarlegacy.avatar");
        
        removeGroup(uuid, "Avatar");
    }

    public void revokeAllPermissions(UUID uuid) {
        if (!isEnabled()) return;
        luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
            if (user == null) return;
            user.data().clear();
            luckPerms.getUserManager().saveUser(user);
        });
    }

    public void revokeCharacterPermissions(UUID uuid) {
        if (!isEnabled()) return;
        luckPerms.getUserManager().loadUser(uuid).thenAccept(user -> {
            if (user == null) return;

            java.util.List<net.luckperms.api.node.Node> toRemove = new java.util.ArrayList<>();
            for (net.luckperms.api.node.Node node : user.getNodes()) {
                String key = node.getKey();
                boolean shouldRemove =
                        key.startsWith("bending.ability.") ||
                        key.equals("bending.command.choose") ||
                        key.equals("bending.command.rechoose") ||
                        key.equals("bending.command.add") ||
                        key.equals("avatarlegacy.avatar");

                if (!shouldRemove && node instanceof InheritanceNode) {
                    String groupName = ((InheritanceNode) node).getGroupName();
                    if (groupName.equalsIgnoreCase("avatar")) {
                        shouldRemove = true;
                    }
                }

                if (shouldRemove) toRemove.add(node);
            }

            for (net.luckperms.api.node.Node node : toRemove) {
                user.data().remove(node);
            }

            luckPerms.getUserManager().saveUser(user);
        });
    }

    public LuckPerms getLuckPermsApi() {
        return luckPerms;
    }

    public List<String> getLearnedAbilities(UUID uuid) {
        List<String> abilities = new ArrayList<>();
        if (!isEnabled()) return abilities;
        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user == null) {
                user = luckPerms.getUserManager().loadUser(uuid).join();
            }
            if (user == null) return abilities;
            for (Node node : user.getNodes()) {
                if (node.getValue() && node.getKey().startsWith("bending.ability.")) {
                    String abilityName = node.getKey().substring("bending.ability.".length());
                    if (!abilityName.isEmpty()) {
                        String titled = Character.toUpperCase(abilityName.charAt(0))
                                + abilityName.substring(1);
                        abilities.add(titled);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LuckPerms] getLearnedAbilities failed: " + e.getMessage());
        }
        return abilities;
    }
}
