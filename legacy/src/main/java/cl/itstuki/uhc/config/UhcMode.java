package cl.itstuki.uhc.config;

import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XMaterial;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;

/** Modo de juego de la partida. */
public enum UhcMode {
    /** Equipos contra equipos; gana el último equipo con vida. */
    PVP(XMaterial.IRON_SWORD),
    /** Todos contra todos, sin equipos. */
    SOLITARIOS(XMaterial.WOODEN_SWORD),
    /** Gana quien mate a la dragona (un jugador o por equipos). */
    DRAGON(XMaterial.DRAGON_HEAD),
    /** Equipos de 3 con roles secretos: Troll, Rata y Normal. */
    TROLLS_RATAS(XMaterial.SPIDER_EYE),
    /** Conseguir una lista de objetos antes que los demás. */
    RESOURCE_RUSH(XMaterial.CHEST);

    private final XMaterial icon;

    UhcMode(XMaterial icon) {
        this.icon = icon;
    }

    public ItemStack icon() {
        return Compat.firstItem(icon, XMaterial.DRAGON_EGG);
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
