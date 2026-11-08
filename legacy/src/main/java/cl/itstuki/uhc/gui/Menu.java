package cl.itstuki.uhc.gui;

import java.util.HashMap;
import java.util.Map;
import cl.itstuki.uhc.compat.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Menú de inventario con acciones por casilla. Se redibuja con {@link #render()}. */
public abstract class Menu implements InventoryHolder {
    @FunctionalInterface
    public interface Action {
        void click(Player player, ClickType click);
    }

    private final Inventory inventory;
    private final Map<Integer, Action> actions = new HashMap<>();

    protected Menu(int rows, Component title) {
        this.inventory = Bukkit.createInventory(this, rows * 9, Text.fit(title, 32));
    }

    /** Dibuja todos los botones. */
    protected abstract void render();

    /** Clic en un ítem del inventario del jugador (abajo). Por defecto no hace nada. */
    public void clickBottom(Player player, ItemStack item, ClickType click) {
    }

    protected void set(int slot, ItemStack item, Action action) {
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    protected void clear() {
        inventory.clear();
        actions.clear();
    }

    public void open(Player player) {
        clear();
        render();
        player.openInventory(inventory);
    }

    public void refresh() {
        clear();
        render();
    }

    void handle(Player player, int slot, ClickType click) {
        Action action = actions.get(slot);
        if (action != null) {
            action.click(player, click);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
