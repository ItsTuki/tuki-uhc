package cl.itstuki.uhc.compat;

import java.nio.charset.StandardCharsets;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Marca invisible para reconocer los ítems del plugin en cualquier versión (1.8 no tiene
 * PersistentDataContainer). Se guarda al final del nombre como códigos de color, que no se ven.
 */
public final class ItemTag {
    private static final String START = "§r§0§f§0";

    private ItemTag() {
    }

    public static String encode(String id) {
        StringBuilder out = new StringBuilder(START);
        for (byte b : id.getBytes(StandardCharsets.UTF_8)) {
            String hex = String.format("%02x", b);
            out.append('§').append(hex.charAt(0)).append('§').append(hex.charAt(1));
        }
        return out.append("§f§0§f").toString();
    }

    public static ItemStack tag(ItemStack stack, String id) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        String name = meta.hasDisplayName() ? meta.getDisplayName() : ChatColor.RESET + "";
        meta.setDisplayName(strip(name) + encode(id));
        stack.setItemMeta(meta);
        return stack;
    }

    /** Id guardado en el ítem, o null. */
    public static String read(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) {
            return null;
        }
        String name = stack.getItemMeta().getDisplayName();
        String start = START;
        int from = name.lastIndexOf(start);
        int to = name.lastIndexOf("§f§0§f");
        if (from < 0 || to <= from) {
            return null;
        }
        String codes = name.substring(from + start.length(), to).replace("§", "");
        if (codes.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = new byte[codes.length() / 2];
        try {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(codes.substring(i * 2, i * 2 + 2), 16);
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String strip(String name) {
        int at = name.lastIndexOf(START);
        return at >= 0 ? name.substring(0, at) : name;
    }
}
