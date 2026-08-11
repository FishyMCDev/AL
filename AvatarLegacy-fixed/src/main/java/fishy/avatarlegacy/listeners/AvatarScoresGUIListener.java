package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.guis.AvatarScoresGUI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class AvatarScoresGUIListener implements Listener {

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AvatarScoresGUI gui) {
            AvatarScoresGUI.onClose(gui);
        }
    }
}