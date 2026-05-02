package fishy.avatarlegacy.listeners;

import fishy.avatarlegacy.AvatarLegacy;
import fishy.avatarlegacy.items.ElementChangerItem;
import fishy.avatarlegacy.models.PlayerData;
import fishy.avatarlegacy.utils.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ElementChangerListener implements Listener {

    private final AvatarLegacy plugin;
    private static final String GUI_TITLE = "Choose Your New Element";

    private static final String[] ELEMENTS = {"fire", "water", "earth", "air", "chi"};
    private static final Material[] ELEMENT_MATERIALS = {
        Material.BLAZE_POWDER,
        Material.BLUE_ICE,
        Material.DIRT,
        Material.FEATHER,
        Material.BONE
    };
    private static final NamedTextColor[] ELEMENT_COLORS = {
        NamedTextColor.RED,
        NamedTextColor.AQUA,
        NamedTextColor.GREEN,
        NamedTextColor.WHITE,
        NamedTextColor.YELLOW
    };

    public ElementChangerListener(AvatarLegacy plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().name().contains("RIGHT")) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!ElementChangerItem.isElementChanger(plugin, item)) return;

        event.setCancelled(true);

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(MessageUtil.error("You need an active character to use this!"));
            return;
        }

        openElementGui(player);
    }

    private void openElementGui(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text(GUI_TITLE));

        for (int i = 0; i < ELEMENTS.length; i++) {
            String element = ELEMENTS[i];
            ItemStack icon = new ItemStack(ELEMENT_MATERIALS[i]);
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(element.substring(0, 1).toUpperCase() + element.substring(1),
                    ELEMENT_COLORS[i]).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text("Click to change your element to " + element.toUpperCase() + ".",
                        NamedTextColor.GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Component.text("This will permanently replace your current element.",
                        NamedTextColor.DARK_GRAY).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
            ));
            icon.setItemMeta(meta);
            inv.setItem(i, icon);
        }

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = ELEMENTS.length; i < 9; i++) inv.setItem(i, filler);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!title.equals(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        String chosenElement = null;
        for (int i = 0; i < ELEMENT_MATERIALS.length; i++) {
            if (clicked.getType() == ELEMENT_MATERIALS[i]) {
                chosenElement = ELEMENTS[i];
                break;
            }
        }
        if (chosenElement == null) return;

        player.closeInventory();

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(MessageUtil.error("No active character found."));
            return;
        }

        String oldElement = data.getElement();
        if (oldElement != null && oldElement.equalsIgnoreCase(chosenElement)) {
            player.sendMessage(MessageUtil.error("You are already a " + chosenElement.toUpperCase() + " bender!"));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!ElementChangerItem.isElementChanger(plugin, hand)) {
            player.sendMessage(MessageUtil.error("Element changer not found in hand."));
            return;
        }

        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        final String finalElement = chosenElement;
        plugin.getElementManager().changeElementViaChanger(player, finalElement);

        if (plugin.getConfig().getBoolean("element-changer.announce-change", true)) {
            String charName = plugin.getCharacterManager().getCachedCharacterName(player.getUniqueId());
            String displayName = (charName != null && !charName.isBlank()) ? charName : player.getName();
            Bukkit.broadcast(
                Component.text("\u2728 ", NamedTextColor.GOLD)
                    .append(Component.text(displayName, NamedTextColor.YELLOW))
                    .append(Component.text(" has changed their element to ", NamedTextColor.WHITE))
                    .append(Component.text(finalElement.toUpperCase(), ELEMENT_COLORS[getElementIndex(finalElement)]))
                    .append(Component.text("!", NamedTextColor.WHITE))
            );
        }
    }

    private int getElementIndex(String element) {
        for (int i = 0; i < ELEMENTS.length; i++) {
            if (ELEMENTS[i].equals(element)) return i;
        }
        return 0;
    }
}
