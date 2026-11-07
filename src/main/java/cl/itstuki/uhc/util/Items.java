package cl.itstuki.uhc.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

/** Constructor sencillo de ítems para menús y objetos especiales. */
public final class Items {
    private final ItemStack stack;

    private Items(Material material) {
        this.stack = ItemStack.of(material);
    }

    public static Items of(Material material) {
        return new Items(material);
    }

    public Items name(Component name) {
        stack.editMeta(meta -> meta.displayName(name.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE)));
        return this;
    }

    public Items lore(List<Component> lore) {
        stack.editMeta(meta -> meta.lore(lore.stream()
                .map(line -> line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                .toList()));
        return this;
    }

    public Items amount(int amount) {
        stack.setAmount(Math.max(1, Math.min(99, amount)));
        return this;
    }

    public Items glow(boolean glow) {
        stack.editMeta(meta -> meta.setEnchantmentGlintOverride(glow ? Boolean.TRUE : null));
        return this;
    }

    public Items hideAll() {
        stack.editMeta(meta -> meta.addItemFlags(ItemFlag.values()));
        return this;
    }

    public Items tag(NamespacedKey key, String value) {
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value));
        return this;
    }

    public Items owner(@Nullable OfflinePlayer player) {
        if (player != null) {
            stack.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
        }
        return this;
    }

    public ItemStack build() {
        return stack;
    }

    public static @Nullable String tag(@Nullable ItemStack stack, NamespacedKey key) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return stack.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
}
