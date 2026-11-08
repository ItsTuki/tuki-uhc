package cl.itstuki.uhc.config;

import cl.itstuki.uhc.compat.Compat;
import com.cryptomorin.xseries.XMaterial;
import java.util.Locale;
import org.bukkit.inventory.ItemStack;

/**
 * Cómo se mide la partida.
 * EPISODES: partes con título, aviso de fin y descanso opcional; el borde se cierra en una parte.
 * TIMED: una duración total (ej. 2 horas) sin cortes; el borde se cierra de forma continua hasta el final.
 */
public enum MatchFormat {
    EPISODES(XMaterial.BOOKSHELF),
    TIMED(XMaterial.CLOCK);

    private final XMaterial icon;

    MatchFormat(XMaterial icon) {
        this.icon = icon;
    }

    public ItemStack icon() {
        return Compat.item(icon);
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
