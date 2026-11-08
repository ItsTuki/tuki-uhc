package cl.itstuki.uhc.util;

import cl.itstuki.uhc.compat.Compat;
import cl.itstuki.uhc.compat.ItemTag;
import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** Constructor sencillo de ítems para menús y objetos especiales (textos ya en "§"). */
public final class Items {
    private final ItemStack stack;

    private Items(ItemStack stack) {
        this.stack = stack;
    }

    public static Items of(XMaterial material) {
        return new Items(Compat.item(material));
    }

    public static Items of(Material material) {
        return new Items(new ItemStack(material));
    }

    public static Items of(ItemStack stack) {
        return new Items(stack.clone());
    }

    private void edit(java.util.function.Consumer<ItemMeta> change) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            change.accept(meta);
            stack.setItemMeta(meta);
        }
    }

    public Items name(String name) {
        edit(meta -> meta.setDisplayName(name));
        return this;
    }

    public Items lore(List<String> lore) {
        edit(meta -> meta.setLore(lore));
        return this;
    }

    public Items amount(int amount) {
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public Items glow(boolean glow) {
        if (glow) {
            Enchantment enchant = XEnchantment.UNBREAKING.get();
            if (enchant != null) {
                edit(meta -> {
                    meta.addEnchant(enchant, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                });
            }
        }
        return this;
    }

    public Items hideAll() {
        edit(meta -> meta.addItemFlags(ItemFlag.values()));
        return this;
    }

    public Items tag(String id) {
        ItemTag.tag(stack, id);
        return this;
    }

    @SuppressWarnings("deprecation")
    public Items owner(String player) {
        if (player != null && stack.getItemMeta() instanceof SkullMeta) {
            edit(meta -> ((SkullMeta) meta).setOwner(player));
        }
        return this;
    }

    public ItemStack build() {
        return stack;
    }
}
