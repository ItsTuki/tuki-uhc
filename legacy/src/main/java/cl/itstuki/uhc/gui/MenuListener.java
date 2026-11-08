package cl.itstuki.uhc.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu)) {
            return;
        }
        Menu menu = (Menu) event.getInventory().getHolder();
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        int raw = event.getRawSlot();
        if (raw < 0) {
            return;
        }
        if (raw < event.getView().getTopInventory().getSize()) {
            menu.handle(player, event.getSlot(), event.getClick());
        } else {
            menu.clickBottom(player, event.getCurrentItem(), event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
