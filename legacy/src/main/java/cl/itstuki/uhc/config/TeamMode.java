package cl.itstuki.uhc.config;

import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XMaterial;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;

/** Cómo se forman los equipos. */
public enum TeamMode {
    /** Sin equipos: cada jugador juega solo. */
    SOLO(XMaterial.PLAYER_HEAD),
    /** El organizador crea los equipos desde el menú o con /uhcteam. */
    MANUAL(XMaterial.CRAFTING_TABLE),
    /** Equipos al azar que se revelan en un episodio. */
    RANDOM(XMaterial.DISPENSER),
    /** "Amor a primera vista": los equipos se forman al juntarse durante la partida. */
    PROXIMITY(XMaterial.POPPY);

    private final XMaterial icon;

    TeamMode(XMaterial icon) {
        this.icon = icon;
    }

    public ItemStack icon() {
        return Compat.item(icon);
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
