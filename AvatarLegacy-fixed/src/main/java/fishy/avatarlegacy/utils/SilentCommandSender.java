package fishy.avatarlegacy.utils;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.permissions.*;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public class SilentCommandSender implements ConsoleCommandSender {

    private final Server server;

    public SilentCommandSender(Server server) {
        this.server = server;
    }

    @Override public void sendMessage(@NotNull String msg) {}
    @Override public void sendMessage(@NotNull String... msgs) {}
    @Override public void sendMessage(@Nullable UUID uuid, @NotNull String msg) {}
    @Override public void sendMessage(@Nullable UUID uuid, @NotNull String... msgs) {}
    @Override public void sendRawMessage(@NotNull String msg) {}
    @Override public void sendRawMessage(@Nullable UUID uuid, @NotNull String msg) {}
    @Override public @NotNull String getName() { return "SILENT"; }
    @Override public @NotNull Component name() { return Component.text("SILENT"); }
    @Override public @NotNull Server getServer() { return server; }
    @Override public boolean isOp() { return true; }
    @Override public void setOp(boolean b) {}

    @Override public boolean isPermissionSet(@NotNull String s) { return true; }
    @Override public boolean isPermissionSet(@NotNull Permission p) { return true; }
    @Override public boolean hasPermission(@NotNull String s) { return true; }
    @Override public boolean hasPermission(@NotNull Permission p) { return true; }
    @Override public @NotNull PermissionAttachment addAttachment(@NotNull Plugin p, @NotNull String name, boolean value) {
        throw new UnsupportedOperationException();
    }
    @Override public @NotNull PermissionAttachment addAttachment(@NotNull Plugin p) {
        throw new UnsupportedOperationException();
    }
    @Override public @Nullable PermissionAttachment addAttachment(@NotNull Plugin p, @NotNull String name, boolean value, int ticks) {
        return null;
    }
    @Override public @Nullable PermissionAttachment addAttachment(@NotNull Plugin p, int ticks) {
        return null;
    }
    @Override public void removeAttachment(@NotNull PermissionAttachment a) {}
    @Override public void recalculatePermissions() {}
    @Override public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() { return Set.of(); }

    @Override public boolean isConversing() { return false; }
    @Override public void acceptConversationInput(@NotNull String s) {}
    @Override public boolean beginConversation(org.bukkit.conversations.@NotNull Conversation c) { return false; }
    @Override public void abandonConversation(org.bukkit.conversations.@NotNull Conversation c) {}
    @Override public void abandonConversation(org.bukkit.conversations.@NotNull Conversation c,
                                               org.bukkit.conversations.@NotNull ConversationAbandonedEvent e) {}

    @Override public @NotNull org.bukkit.command.CommandSender.Spigot spigot() {
        return new org.bukkit.command.CommandSender.Spigot();
    }

    @Override public @NotNull Audience filterAudience(@NotNull java.util.function.Predicate<? super Audience> filter) {
        return Audience.empty();
    }
}
